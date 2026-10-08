package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.CommandPlaceholders;
import com.jruk8.jmanhunt.command.CommandSyntax;
import com.jruk8.jmanhunt.command.EngineEscapes;
import com.jruk8.jmanhunt.command.ModifierTagScope;
import com.jruk8.jmanhunt.command.QuietConsoleDispatch;
import com.jruk8.jmanhunt.command.TagBackends;
import com.jruk8.jmanhunt.command.TagControlFlow;
import com.jruk8.jmanhunt.command.SelectorExpansion;
import com.jruk8.jmanhunt.command.SharedRandomRolls;
import com.jruk8.jmanhunt.command.TagContext;
import com.jruk8.jmanhunt.core.PlaceholderPass;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.MiscConfig;
import com.jruk8.jmanhunt.lobby.config.PlayersSettingsFacade;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.core.JManhuntPlaceholders;
import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerResetService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Executes game rules and modifiers at match state transitions. */
public final class GameStateCommandManager implements ModifierToggleService.Commands {
    /** States, modifier reads, interop, and player settings. */
    public record CommandReads(PlayerStateStore playerStates, ConfigService configService,
            MiscConfig.Interop interop, PlayersSettingsFacade players) {
    }

    /** Engine states, fakes, logger, overrides, placeholders, and scheduler. */
    public record CommandEdge(EngineStateRepository engineStates, FakeSpectatorService fakes,
            JManhuntLogger log, OverrideService overrides, JManhuntPlaceholders placeholders,
            TaskScheduler tasks) {
    }

    private final CommandReads reads;
    private final CommandEdge edge;
    private final MessageService messages;
    private final SoundService sounds;
    private final GameManager game;
    private final IntervalDispatcher intervals;
    private final ModifierToggleService toggles;
    private final PlayerWipeService wipes;
    private final MatchDefaultsService defaults;
    private final ModifierTagSinks sinks;

    public GameStateCommandManager(CommandReads reads, CommandEdge edge,
            MessageService messages, SoundService sounds, GameManager game) {
        this.reads = reads;
        this.edge = edge;
        this.messages = messages;
        this.sounds = sounds;
        this.game = game;
        this.intervals = new IntervalDispatcher(
                new IntervalDispatcher.IntervalReads(reads.configService(), edge.overrides()),
                new IntervalDispatcher.IntervalRuntime(game, reads.playerStates(), edge.fakes()),
                new IntervalDispatcher.IntervalEdge(edge.log(), edge.tasks()),
                this::dispatchModifier);
        this.toggles = new ModifierToggleService(
                new ModifierToggleService.ToggleReads(edge.overrides(), edge.log(),
                        reads.configService()),
                game, intervals, this);
        this.wipes = new PlayerWipeService(edge.engineStates(), edge.log(),
                new PlayerResetService(edge.overrides()));
        this.defaults = new MatchDefaultsService(edge.overrides(), edge.log(),
                wipes, new MatchDefaultsService.DefaultsStates(reads.playerStates(),
                        edge.fakes()));
        this.sinks = new ModifierTagSinks(edge.log(),
                new ModifierTagSinks.SinkBus(messages, messages.modifiers(), sounds),
                game, reads.playerStates(), reads.interop());
    }

    public void runStart(long matchId, List<Player> participants, List<Player> lobbySpectators, int lobbyId) {
        intervals.cancelPendingDelayed(matchId);
        defaults.runDefault("start", participants, lobbySpectators, lobbyId, false);
        game.instance(matchId).ifPresent(instance -> {
            for (Player player : participants) {
                instance.markStartFired(player.getUniqueId());
            }
        });
        runModifierStarts(matchId);
    }

    /**
     * Runs ON_START modifiers deferred past the pre-start sequence via
     * {@code on-start.pre-start-order: AFTER}. Called from
     * {@link GameManager#beginGame()} once the speedrunner first hits a
     * hunter (or the match force-starts). With
     * start-on-speedrunner-damage disabled, begin runs inside start, so
     * deferred modifiers still fire immediately.
     */
    public void runPostStartModifiers(long matchId) {
        game.instance(matchId).ifPresent(instance -> {
            for (Player player : game.onlineParticipants(matchId)) {
                instance.markStartFired(player.getUniqueId());
            }
        });
        for (String name : intervals.enabledModifiers(matchId)) {
            for (int index : reads.configService().behaviorIndexes(name)) {
                if (!ModifierTriggers.runsOn(reads.configService().runsOn(name, index), "ON_START")) {
                    continue;
                }
                if (!afterPrestart(name, index)) {
                    continue;
                }
                runModifierCommands(name, index, matchId, List.of());
            }
        }
    }

    /**
     * Runs ON_START behaviors for one switched player: BEFORE lists
     * always, AFTER lists only once begun (pre-begin AFTER lists still
     * arrive through the begin sequence). Single-target catch-up behind
     * role switches; callers skip non-participant targets.
     */
    public void runStartForPlayer(long matchId, Player player) {
        boolean begun = game.instance(matchId).map(GameInstance::begun).orElse(false);
        for (String name : intervals.enabledModifiers(matchId)) {
            for (int index : reads.configService().behaviorIndexes(name)) {
                if (!ModifierTriggers.runsOn(reads.configService().runsOn(name, index),
                        "ON_START")) {
                    continue;
                }
                if (afterPrestart(name, index) && !begun) {
                    continue;
                }
                dispatchModifier(name, index, List.of(player), matchId, List.of());
            }
        }
    }

    /**
     * Runs ON_RESPAWN plus the live-role split for one switched
     * player. Single-target catch-up behind role switches; callers
     * skip non-participant targets.
     */
    public void runRespawnForPlayer(long matchId, Player player) {
        Role role = reads.playerStates().role(player);
        String split = role == Role.HUNTER ? "ON_HUNTER_RESPAWN" : "ON_SPEEDRUNNER_RESPAWN";
        for (String name : intervals.enabledModifiers(matchId)) {
            for (int index : reads.configService().behaviorIndexes(name)) {
                List<String> runsOn = reads.configService().runsOn(name, index);
                if (ModifierTriggers.runsOn(runsOn, "ON_RESPAWN")
                        || ModifierTriggers.runsOn(runsOn, split)) {
                    dispatchModifier(name, index, List.of(player), matchId, List.of());
                }
            }
        }
    }

    /** True when the modifier defers its ON_START sequence past the pre-start hit. */
    public void runEnd(long matchId, List<Player> participants, List<Player> lobbySpectators, int lobbyId,
                       boolean lastMatch) {
        intervals.cancelPendingDelayed(matchId);
        defaults.runDefault("end", participants, lobbySpectators, lobbyId, lastMatch);
    }

    public void runConsoleCleanup(long matchId) {
        for (String name : intervals.enabledModifiers(matchId)) {
            runConsoleCleanup(name, matchId);
        }
    }

    /** Console cleanup for one modifier; reused by toggle sync. */
    void runConsoleCleanup(String name, long matchId) {
        ModifierTagScope scope = ModifierTagScope.executor(null,
                EngineEscapes.restoring(edge.log()::warning));
        for (int index : reads.configService().behaviorIndexes(name)) {
            runCommandList(reads.configService().commandList(name, index, "console-cleanup"), null,
                    tagContext(name, null, scope, matchId, List.of()),
                    TagContext.Provenance.of(name, index, "console-cleanup"));
        }
    }

    public void runPlayerCleanup(long matchId, List<Player> participants) {
        for (String name : intervals.enabledModifiers(matchId)) {
            runPlayerCleanup(name, matchId, participants);
        }
    }

    /** Player cleanup for one modifier; reused by toggle sync. */
    void runPlayerCleanup(String name, long matchId, List<Player> participants) {
        for (int index : reads.configService().behaviorIndexes(name)) {
            for (Player player : participants) {
                runCommandList(reads.configService().commandList(name, index, "player-cleanup"), player,
                        tagContext(name, player, matchScope(player, participants), matchId, List.of()),
                        TagContext.Provenance.of(name, index, "player-cleanup"));
            }
        }
    }

    /** Reconciles live matches with toggled modifiers; see ModifierToggleService. */
    public void syncModifierToggles(Collection<String> names) {
        toggles.syncModifierToggles(names);
    }

    @Override
    public void fireBehavior(String name, int index, long matchId) {
        runModifierCommands(name, index, matchId, List.of());
    }

    @Override
    public void cleanModifier(String name, long matchId, List<Player> roster) {
        runConsoleCleanup(name, matchId);
        runPlayerCleanup(name, matchId, roster);
    }

    @Override
    public boolean afterPrestart(String name, int index) {
        return ModifierTriggers.runsAfterPrestart(reads.configService().preStartOrder(name, index));
    }

    /**
     * Match scope covering the given participants for tag evaluation:
     * {@code <all-players>} fans out to them and {@code <random-player>}
     * draws from them, so neither can leak into another match.
     */
    private ModifierTagScope matchScope(Player executor, List<Player> participants) {
        List<ModifierTagScope.Participant> scope = new ArrayList<>(participants.size());
        for (Player participant : participants) {
            scope.add(new ModifierTagScope.Participant(participant.getName(),
                    reads.playerStates().role(participant).name()));
        }
        return ModifierTagScope.match(executor == null ? null : executor.getName(),
                scope, ThreadLocalRandom.current(),
                EngineEscapes.restoring(edge.log()::warning));
    }

    /** Starts interval modifiers; see {@link IntervalDispatcher}. */
    public void startIntervalModifiers(long matchId) {
        intervals.startIntervalModifiers(matchId);
    }

    /** Cancels one match's interval tasks; see {@link IntervalDispatcher}. */
    public void cancelIntervalModifiers(long matchId) {
        intervals.cancelIntervalModifiers(matchId);
    }

    /**
     * Re-registers interval chains for changed modifiers;
     * see {@link IntervalDispatcher}.
     */
    public void reregisterIntervalModifiers(long matchId, Set<String> names) {
        intervals.reregisterIntervalModifiers(matchId, names);
    }

    /**
     * Runs modifiers whose {@code runs-on} list contains the given event for
     * the specific player involved in the event. The player's own
     * {@code player}/{@code hunter}/{@code speedrunner} commands run, plus the
     * modifier's console commands.
     *
     * @param event  the event name (e.g. ON_MOB_KILLED)
     * @param player the player involved in the event
     * @param matchId the match the event belongs to
     */
    public void runEventModifiers(String event, Player player, long matchId) {
        runEventModifiers(event, player, matchId, List.of());
    }

    /**
     * Event dispatch with trigger args behind {@code <args:index>}:
     * kill victims, portal worlds, advancement keys, the death
     * location list, or the dead player plus killer names,
     * depending on the trigger.
     */
    public void runEventModifiers(String event, Player player, long matchId, List<String> eventArgs) {
        if (player == null) {
            return;
        }
        for (String name : intervals.enabledModifiers(matchId)) {
            for (int index : reads.configService().behaviorIndexes(name)) {
                if (!ModifierTriggers.runsOn(reads.configService().runsOn(name, index), event)) {
                    continue;
                }
                intervals.runDelayed(name, index,
                        () -> dispatchModifier(name, index, List.of(player), matchId, eventArgs), matchId);
            }
        }
    }

    private void runModifierCommands(String name, int index, long matchId, List<String> eventArgs) {
        intervals.runDelayed(name, index,
                () -> dispatchModifier(name, index, game.onlineParticipants(matchId), matchId, eventArgs),
                matchId);
    }

    private void dispatchModifier(String name, int index, List<Player> targets, long matchId,
            List<String> eventArgs) {
        List<Player> match = game.onlineParticipants(matchId);
        double chance = ModifierTriggers.clampChance(reads.configService().chance(name, index));
        ModifierTriggers.TriggerScope chanceScope =
                ModifierTriggers.parseScope(reads.configService().chanceBehavior(name, index));
        ModifierTriggers.TriggerScope pickScope =
                ModifierTriggers.parseScope(reads.configService().pickBehavior(name, index));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean sharedPicks = pickScope == ModifierTriggers.TriggerScope.PER_INVOKE;
        Map<String, List<String>> shared = sharedLists(name, index, sharedPicks);
        TagContext consoleContext = tagContext(name, null, matchScope(null, match), matchId, eventArgs);
        if (chanceScope == ModifierTriggers.TriggerScope.PER_EXECUTOR) {
            if (ModifierTriggers.rollChance(chance, random.nextDouble())) {
                runCommandList(sharedPicks ? shared.get("console") : resolveCommandList(name, index, "console"),
                        null, consoleContext, TagContext.Provenance.of(name, index, "console"));
            }
            for (Player target : targets) {
                if (!ModifierTriggers.rollChance(chance, random.nextDouble())) {
                    continue;
                }
                runExecutorPlayerLists(name, index, target, shared, sharedPicks,
                        tagContext(name, target, matchScope(target, match), matchId, eventArgs));
            }
            return;
        }
        if (!ModifierTriggers.rollChance(chance, random.nextDouble())) {
            return;
        }
        // The shared map only exists for PER_INVOKE picks; per-executor
        // picks resolve their lists here instead of reading nulls.
        runCommandList(sharedPicks ? shared.get("console") : resolveCommandList(name, index, "console"),
                null, consoleContext, TagContext.Provenance.of(name, index, "console"));
        for (Player target : targets) {
            runExecutorPlayerLists(name, index, target, shared, sharedPicks,
                    tagContext(name, target, matchScope(target, match), matchId, eventArgs));
        }
    }

    private Map<String, List<String>> sharedLists(String name, int index, boolean sharedPicks) {
        Map<String, List<String>> shared = new HashMap<>();
        if (sharedPicks) {
            // One mob and one item roll for the whole activation: the shared
            // lists carry concrete values, so per-executor tag evaluation
            // downstream finds nothing left to re-roll.
            Map<String, String> sharedDraws = new HashMap<>();
            for (String list : List.of("console", "player", "hunter", "speedrunner")) {
                shared.put(list, CommandPlaceholders.preresolveSharedRandoms(
                        resolveCommandList(name, index, list), sharedDraws,
                        SharedRandomRolls::rollSharedRandom));
            }
        }
        return shared;
    }

    private void runExecutorPlayerLists(String name, int index, Player target,
                                        Map<String, List<String>> shared,
                                        boolean useShared, TagContext context) {
        runCommandList(useShared ? shared.get("player") : resolveCommandList(name, index, "player"), target,
                context, TagContext.Provenance.of(name, index, "player"));
        String roleCommands = reads.playerStates().role(target) == Role.HUNTER ? "hunter" : "speedrunner";
        runCommandList(useShared ? shared.get(roleCommands) : resolveCommandList(name, index, roleCommands),
                target, context, TagContext.Provenance.of(name, index, roleCommands));
    }

    /** Tag context for one modifier dispatch run: id, sinks, stats, flags, event args. */
    private TagContext tagContext(String name, Player executor, ModifierTagScope scope, long matchId,
            List<String> eventArgs) {
        TagContext context = TagContext.run(new TagContext.TagIdentity(scope, name),
                new TagContext.TagSinks(
                        text -> messages.broadcastText(sinks.formatEngineMessage(text)),
                        text -> {
                            if (executor != null) {
                                messages.sendText(executor, sinks.formatEngineMessage(text));
                            } else {
                                scope.warn("Tag <pmessage> needs an executor player: skipped in '"
                                        + name + "'.");
                            }
                        },
                        (soundId, pitch, volume) -> sinks.playEngineSound(name, null, soundId,
                                pitch, volume),
                        (soundId, pitch, volume) -> {
                            if (executor != null) {
                                sinks.playEngineSound(name, executor, soundId, pitch, volume);
                            } else {
                                scope.warn("Tag <psound> needs an executor player: skipped in '"
                                        + name + "'.");
                            }
                        },
                        (line, provenance) -> sinks.runTagCommand(line, provenance)),
                new TagContext.TagRole(
                        (role, text) -> sinks.sendRoleMessage(name, matchId, scope, role,
                                text),
                        (role, soundId, pitch, volume) -> sinks.playRoleSound(name, matchId,
                                scope, role, soundId, pitch, volume)),
                new TagContext.TagMatch(matchId,
                        new TagBackends(game.matchStatValues(matchId), game.flagStore(),
                                new PlaceholderPass(edge.placeholders()),
                                new MatchRosterValues(game, reads.playerStates(), edge.fakes(),
                                        matchId),
                                NamedPlayerSinks.of(messages, messages.modifiers(), sounds,
                                        edge.log()::warning, name)),
                        eventArgs, detail -> sinks.loopLimitExceeded(detail, matchId),
                        (target, reason) -> sinks.losePlayerByName(name, target, reason, scope,
                                matchId),
                        (role, reason) -> sinks.winForRole(name, role, reason, scope,
                                matchId),
                        (target, role) -> sinks.switchPlayerRoleByName(name, target, role,
                                scope, matchId)));
        context.setCooldowns(game.cooldownStore());
        return context;
    }

    /**
     * Resolves one command list under the modifier's {@code options.execution}
     * settings. {@code IN_ORDER} (the default) returns every line; {@code
     * PICK_RANDOM} returns {@code pick-random.count} randomly drawn lines.
     */
    private List<String> resolveCommandList(String name, int index, String listKey) {
        List<String> commands = reads.configService().commandList(name, index, listKey);
        if (ModifierTriggers.parseSelection(reads.configService().selection(name, index))
                != ModifierTriggers.Selection.PICK_RANDOM) {
            return commands;
        }
        int count = Math.max(1, reads.configService().pickCount(name, index));
        return ModifierTriggers.pickCommands(commands, count, ThreadLocalRandom.current());
    }

    /** True when the end phase wipes participant inventories for the lobby. */
    public boolean endWipeEnabled(int lobbyId) {
        return wipes.endWipeEnabled(lobbyId);
    }

    /** Runs ON_START modifiers that do not wait out the pre-start window. */
    private void runModifierStarts(long matchId) {
        for (String name : intervals.enabledModifiers(matchId)) {
            for (int index : reads.configService().behaviorIndexes(name)) {
                if (ModifierTriggers.runsOn(reads.configService().runsOn(name, index), "ON_START")
                        && !afterPrestart(name, index)) {
                    runModifierCommands(name, index, matchId, List.of());
                }
            }
        }
    }

    /** Runs one command list; package-visible so tests can pin dispatch routing. */
    /**
     * Runs one command list, stamping the 0-based line provenance
     * before each line for loop-limit and null-line diagnostics.
     */
    void runCommandList(List<String> commands, Player player, TagContext context,
            TagContext.Provenance base) {
        Collection<String> blocked = reads.interop().getBlacklistedModifierCommands();
        for (int lineIndex = 0; lineIndex < commands.size(); lineIndex++) {
            String command = commands.get(lineIndex);
            context.setProvenance(base.withLine(lineIndex));
            if (command.isBlank()) {
                continue;
            }
            if (TagControlFlow.isExitMisuse(command)) {
                context.scope().warn("'exit' must stand alone on its line, skipping: " + command);
                continue;
            }
            try {
                String playerName = player != null ? player.getName() : null;
                double x = player != null ? player.getLocation().getX() : 0.0;
                double y = player != null ? player.getLocation().getY() : 0.0;
                double z = player != null ? player.getLocation().getZ() : 0.0;
                for (String expanded : SelectorExpansion.expandAllPlayers(command, context.scope())) {
                    String parsed = CommandPlaceholders.replace(expanded, playerName, x, y, z, context);
                    if (playerName != null) {
                        parsed = context.placeholders().resolve(parsed, playerName);
                    }
                    if (TagControlFlow.isExit(parsed)) {
                        return;
                    }
                    if (TagControlFlow.isExitMisuse(parsed)) {
                        context.scope().warn("'exit' must stand alone on its line, skipping: " + command);
                        continue;
                    }
                    if (!dispatchModifierLine(parsed, context, blocked, player)) {
                        return;
                    }
                }
            } catch (Exception e) {
                edge.log().severe("Failed to run command '%s'. Skipping..".formatted(command), e);
            }
        }
    }

    /**
     * Dispatches one parsed modifier line as console, false when the
     * line names a blacklisted command: the hit logs severe and the
     * caller aborts the rest of the list. A line that resolved to
     * pure {@code "null"} warns with the source line and never
     * dispatches, like blank lines. A non-null executor pins the
     * dispatch to their world and facing; a null one keeps the plain
     * console context.
     */
    private boolean dispatchModifierLine(String parsed, TagContext context,
            Collection<String> blocked, Player player) {
        Optional<String> dispatchable = TagControlFlow.dispatchableLine(parsed);
        if (dispatchable.isPresent() && TagControlFlow.isPureNull(dispatchable.get())) {
            edge.log().warning("Skipping command that resolved to pure \"null\" at "
                    + context.provenance().describe() + ".");
            return true;
        }
        if (dispatchable.isPresent()
                && CommandSyntax.isBlockedCommand(dispatchable.get(), blocked)) {
            edge.log().severe("Blocked blacklisted modifier command '"
                    + dispatchable.get() + "' at " + context.provenance().describe()
                    + "; aborting the command list.");
            return false;
        }
        if (dispatchable.isPresent()) {
            if (player == null) {
                QuietConsoleDispatch.dispatch(dispatchable.get());
            } else {
                QuietConsoleDispatch.dispatchAt(player, dispatchable.get());
            }
        }
        return true;
    }

    /** Full match-end style wipe for one player. See {@link PlayerWipeService}. */
    public void resetPlayer(Player player) {
        wipes.resetPlayer(player);
    }

    /**
     * Defers the match-end wipe for players who are offline at
     * teardown. Memory only: matches never survive a restart anyway.
     */
    public void markPendingEndWipe(Collection<UUID> playerIds) {
        wipes.markPendingEndWipe(playerIds);
    }

    /**
     * Runs a deferred match-end wipe for a rejoiner. Returns true
     * when a wipe was pending and ran.
     */
    public boolean applyPendingEndWipe(Player player) {
        return wipes.applyPendingEndWipe(player, this::resetPlayer);
    }

    /**
     * Tracks match-state entry for post-crash cleanup: every
     * speedrunner, hunter, and spectator activation.
     * See {@link PlayerWipeService}.
     */
    public void trackMatchEntry(Collection<UUID> playerIds) {
        wipes.trackMatchEntry(playerIds);
    }

    /**
     * Drops clean match-state exits from post-crash cleanup.
     * See {@link PlayerWipeService}.
     */
    public void untrackMatchExit(Collection<UUID> playerIds) {
        wipes.untrackMatchExit(playerIds);
    }

    /**
     * Loads surviving crash_cleanup rows after enable when the previous
     * run crashed; drops stale rows after a clean shutdown.
     * See {@link PlayerWipeService}.
     */
    public void loadCrashCleanup() {
        wipes.loadCrashCleanup();
    }

    /**
     * Runs the post-crash wipe for a rejoiner. Returns true when a wipe
     * was pending and ran. See {@link PlayerWipeService}.
     */
    public boolean applyPendingCrashWipe(Player player) {
        return wipes.applyPendingCrashWipe(player, this::resetPlayer);
    }

    /** Vitals-only reset for one player. */
    public void resetVitals(Player player) {
        wipes.resetVitals(player);
    }

    /** Engine message format; package-visible for the Test-a-Command runner. */
    String formatEngineMessage(String text) {
        return sinks.formatEngineMessage(text);
    }

    /** Runs one {@code <run>} line; package-visible for the Test-a-Command runner. */
    void runTagCommand(String line, String provenance) {
        sinks.runTagCommand(line, provenance);
    }
}