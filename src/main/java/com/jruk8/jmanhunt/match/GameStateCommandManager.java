package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.CommandPlaceholders;
import com.jruk8.jmanhunt.command.CommandSyntax;
import com.jruk8.jmanhunt.command.ModifierTagScope;
import com.jruk8.jmanhunt.command.TagBackends;
import com.jruk8.jmanhunt.command.TagContext;
import com.jruk8.jmanhunt.command.TagExpressions;
import com.jruk8.jmanhunt.core.PlaceholderPass;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.MatchConfig;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.player.PlayerResetService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Executes game rules and modifiers at match state transitions. */
public final class GameStateCommandManager implements ModifierToggleService.Commands {
    /** Config path of the modifier command blacklist. */
    private static final String BLACKLISTED_COMMANDS_PATH =
            "advanced.misc.interop.blacklisted-modifier-commands";

    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final ConfigService configService;
    private final MessageService messages;
    private final SoundService sounds;
    private final GameManager game;
    private final IntervalDispatcher intervals;
    private final ModifierToggleService toggles;
    private final PlayerResetService resets;
    private final Set<UUID> pendingEndWipes = new HashSet<>();

    public GameStateCommandManager(JManhuntPlugin plugin, PlayerStateStore playerStates,
                                   ConfigService configService, MessageService messages,
                                   SoundService sounds, GameManager game) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.configService = configService;
        this.messages = messages;
        this.sounds = sounds;
        this.game = game;
        this.intervals = new IntervalDispatcher(plugin, configService, game, playerStates,
                this::dispatchModifier);
        this.toggles = new ModifierToggleService(plugin, configService, game, intervals, this);
        this.resets = new PlayerResetService(plugin.overrides());
    }

    public void runStart(long matchId, List<Player> participants, List<Player> lobbySpectators, int lobbyId) {
        intervals.cancelPendingDelayed(matchId);
        runDefault("start", participants, lobbySpectators, lobbyId, false);
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
        for (String name : intervals.enabledModifiers(matchId)) {
            for (int index : configService.behaviorIndexes(name)) {
                if (!ModifierTriggers.runsOn(configService.runsOn(name, index), "ON_START")) {
                    continue;
                }
                if (!afterPrestart(name, index)) {
                    continue;
                }
                runModifierCommands(name, index, matchId, List.of());
            }
        }
    }

    /** True when the modifier defers its ON_START sequence past the pre-start hit. */
    public void runEnd(long matchId, List<Player> participants, List<Player> lobbySpectators, int lobbyId,
                       boolean lastMatch) {
        intervals.cancelPendingDelayed(matchId);
        runDefault("end", participants, lobbySpectators, lobbyId, lastMatch);
    }

    public void runConsoleCleanup(long matchId) {
        for (String name : intervals.enabledModifiers(matchId)) {
            runConsoleCleanup(name, matchId);
        }
    }

    /** Console cleanup for one modifier; reused by toggle sync. */
    void runConsoleCleanup(String name, long matchId) {
        ModifierTagScope scope = ModifierTagScope.executor(null, plugin.logger()::warning);
        for (int index : configService.behaviorIndexes(name)) {
            runCommandList(configService.commandList(name, index, "console-cleanup"), null,
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
        for (int index : configService.behaviorIndexes(name)) {
            for (Player player : participants) {
                runCommandList(configService.commandList(name, index, "player-cleanup"), player,
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
        return ModifierTriggers.runsAfterPrestart(configService.preStartOrder(name, index));
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
                    playerStates.role(participant).name()));
        }
        return ModifierTagScope.match(executor == null ? null : executor.getName(),
                scope, ThreadLocalRandom.current(), plugin.logger()::warning);
    }

    /** Starts interval modifiers; see {@link IntervalDispatcher}. */
    public void startIntervalModifiers(long matchId) {
        intervals.startIntervalModifiers(matchId);
    }

    /** Cancels one match's interval tasks; see {@link IntervalDispatcher}. */
    public void cancelIntervalModifiers(long matchId) {
        intervals.cancelIntervalModifiers(matchId);
    }

    /** Cancels every match's interval tasks; see {@link IntervalDispatcher}. */
    public void cancelAllIntervalModifiers() {
        intervals.cancelAllIntervalModifiers();
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
     * kill victims, portal worlds, advancement keys, or the death
     * location list, depending on the trigger.
     */
    public void runEventModifiers(String event, Player player, long matchId, List<String> eventArgs) {
        if (player == null) {
            return;
        }
        for (String name : intervals.enabledModifiers(matchId)) {
            for (int index : configService.behaviorIndexes(name)) {
                if (!ModifierTriggers.runsOn(configService.runsOn(name, index), event)) {
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
        double chance = ModifierTriggers.clampChance(configService.chance(name, index));
        ModifierTriggers.TriggerScope chanceScope =
                ModifierTriggers.parseScope(configService.chanceBehavior(name, index));
        ModifierTriggers.TriggerScope pickScope =
                ModifierTriggers.parseScope(configService.pickBehavior(name, index));
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
                        CommandPlaceholders::rollSharedRandom));
            }
        }
        return shared;
    }

    private void runExecutorPlayerLists(String name, int index, Player target,
                                        Map<String, List<String>> shared,
                                        boolean useShared, TagContext context) {
        runCommandList(useShared ? shared.get("player") : resolveCommandList(name, index, "player"), target,
                context, TagContext.Provenance.of(name, index, "player"));
        String roleCommands = playerStates.role(target) == Role.HUNTER ? "hunter" : "speedrunner";
        runCommandList(useShared ? shared.get(roleCommands) : resolveCommandList(name, index, roleCommands),
                target, context, TagContext.Provenance.of(name, index, roleCommands));
    }

    /** Tag context for one modifier dispatch run: id, sinks, stats, flags, event args. */
    private TagContext tagContext(String name, Player executor, ModifierTagScope scope, long matchId,
            List<String> eventArgs) {
        return TagContext.run(scope, name,
                text -> messages.broadcastText(formatEngineMessage(text)),
                text -> {
                    if (executor != null) {
                        messages.sendText(executor, formatEngineMessage(text));
                    } else {
                        scope.warn("Tag <pmessage> needs an executor player: skipped in '" + name + "'.");
                    }
                },
                (soundId, pitch, volume) -> playEngineSound(name, null, soundId, pitch, volume),
                (soundId, pitch, volume) -> {
                    if (executor != null) {
                        playEngineSound(name, executor, soundId, pitch, volume);
                    } else {
                        scope.warn("Tag <psound> needs an executor player: skipped in '" + name + "'.");
                    }
                },
                (target, reason) -> losePlayerByName(name, target, reason, scope, matchId),
                (role, reason) -> winForRole(name, role, reason, scope, matchId),
                matchId, new TagBackends(game.matchStatValues(matchId), game.flagStore(),
                        new PlaceholderPass(plugin.placeholderValues()),
                        new MatchRosterValues(game, playerStates, plugin.fakeSpectators(), matchId)),
                eventArgs, detail -> loopLimitExceeded(detail, matchId),
                (role, text) -> sendRoleMessage(name, matchId, scope, role, text),
                (role, soundId, pitch, volume) -> playRoleSound(name, matchId, scope, role,
                        soundId, pitch, volume),
                (line, provenance) -> runTagCommand(line, provenance));
    }

    /**
     * {@code <run>} sink: dispatches one evaluated line from the
     * console with the blacklist enforced, like a command list
     * entry. A hit only skips that line, never the outer list.
     */
    private void runTagCommand(String line, String provenance) {
        Collection<String> blocked = configService.getStringList(BLACKLISTED_COMMANDS_PATH);
        if (CommandSyntax.isBlockedCommand(line, blocked)) {
            plugin.logger().severe("Blocked blacklisted modifier command '"
                    + line + "' at " + provenance + ".");
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
    }

    /**
     * {@code <rmessage>} sink: tells every online assigned player of
     * the named role, formatted like {@code <pmessage>}.
     */
    private void sendRoleMessage(String name, long matchId, ModifierTagScope scope,
            String role, String text) {
        roleMembers("rmessage", name, matchId, scope, role).ifPresent(members -> {
            String formatted = formatEngineMessage(text);
            for (Player member : members) {
                messages.sendText(member, formatted);
            }
        });
    }

    /**
     * {@code <rsound>} sink: plays for every online assigned player
     * of the named role. Unknown ids skip like engine sounds.
     */
    private void playRoleSound(String name, long matchId, ModifierTagScope scope,
            String role, String soundId, float pitch, float volume) {
        Optional<List<Player>> members = roleMembers("rsound", name, matchId, scope, role);
        if (members.isEmpty()) {
            return;
        }
        if (!sounds.isValidSound(soundId)) {
            plugin.logger().warning("modifier \"" + name
                    + "\" tried playing invalid sound \"" + soundId + "\"");
            return;
        }
        for (Player member : members.get()) {
            sounds.playCustomSound(member, soundId, pitch, volume);
        }
    }

    /**
     * Online assigned players of the named role behind role tags,
     * else empty with the reason warned. The tag layer validates the
     * role, like {@code <win>}.
     */
    private Optional<List<Player>> roleMembers(String tag, String name, long matchId,
            ModifierTagScope scope, String role) {
        Role target = Role.valueOf(role);
        Optional<GameInstance> instance = game.instance(matchId);
        if (instance.isEmpty()) {
            scope.warn("Tag <" + tag + "> needs a live match: skipped in '" + name + "'.");
            return Optional.empty();
        }
        List<Player> members = new ArrayList<>();
        for (Player member : game.onlineAssignedPlayers(instance.get())) {
            if (playerStates.role(member) == target) {
                members.add(member);
            }
        }
        return Optional.of(members);
    }

    /**
     * Loop-limit sink: a {@code <while>} or {@code <for>} passed 1000
     * steps. Logs the source line, tells the match to contact an
     * administrator, and cancels the match.
     */
    private void loopLimitExceeded(String detail, long matchId) {
        plugin.logger().severe("JMHScript loop exceeded 1000 steps at " + detail);
        Optional<GameInstance> instance = game.instance(matchId);
        if (instance.isEmpty()) {
            return;
        }
        String text = messages.string("modifiers.loop-limit",
                "{prefix}<red>A modifier loop exceeded its step limit and the match was cancelled. "
                        + "Please tell an administrator.");
        for (Player player : game.onlineParticipants(matchId)) {
            messages.sendText(player, text);
        }
        game.cancel(instance.get());
    }

    /**
     * {@code <loseplayer>} sink: eliminates one player by name with
     * the tag reason; failures skip with a warning naming the tag.
     */
    private void losePlayerByName(String name, String target, String reason,
            ModifierTagScope scope, long matchId) {
        if (!game.losePlayer(matchId, target, reason)) {
            scope.warn("Tag <loseplayer:" + target + "> skipped: '" + target
                    + "' is not an active runner or hunter in '" + name + "'.");
        }
    }

    /**
     * {@code <win:ROLE>} sink: ends the match for one role next
     * tick, with the tag reason on the win screen.
     */
    private void winForRole(String name, String role, String reason,
            ModifierTagScope scope, long matchId) {
        Optional<GameInstance> instance = game.instance(matchId);
        if (instance.isEmpty() || !instance.get().begun() || instance.get().ending()) {
            scope.warn("Tag <win> needs a live match: skipped in '" + name + "'.");
            return;
        }
        game.finishLater(instance.get(), Role.valueOf(role), reason);
    }

    private String formatEngineMessage(String text) {
        String format = messages.string("modifiers.message-format", "{prefix}{message}");
        return format.replace("{prefix}", messages.string("prefix", ""))
                .replace("{message}", text);
    }

    /**
     * Plays one engine sound for every online player (global) or one
     * executor. Unknown ids skip with the modifier named in the log.
     */
    private void playEngineSound(String containerId, Player target, String soundId,
            float pitch, float volume) {
        if (!sounds.isValidSound(soundId)) {
            plugin.logger().warning("modifier \"" + containerId
                    + "\" tried playing invalid sound \"" + soundId + "\"");
            return;
        }
        if (target != null) {
            sounds.playCustomSound(target, soundId, pitch, volume);
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            sounds.playCustomSound(online, soundId, pitch, volume);
        }
    }

    /**
     * Resolves one command list under the modifier's {@code options.execution}
     * settings. {@code IN_ORDER} (the default) returns every line; {@code
     * PICK_RANDOM} returns {@code pick-random.count} randomly drawn lines.
     */
    private List<String> resolveCommandList(String name, int index, String listKey) {
        List<String> commands = configService.commandList(name, index, listKey);
        if (ModifierTriggers.parseSelection(configService.selection(name, index))
                != ModifierTriggers.Selection.PICK_RANDOM) {
            return commands;
        }
        int count = Math.max(1, configService.pickCount(name, index));
        return ModifierTriggers.pickCommands(commands, count, ThreadLocalRandom.current());
    }

    /**
     * True when a match-paused gamerule is back on: the end phase of the
     * last match, or any phase when its toggle is off. Pure for tests.
     */
    static boolean gameruleRestored(String phase, boolean lastMatch, boolean toggleEnabled) {
        return (phase.equals("end") && lastMatch) || !toggleEnabled;
    }

    /** Pauses a spawn gamerule while a match runs, restoring it after. */
    private void applySpawnGamerule(List<World> worlds, String phase,
            boolean lastMatch, boolean disabled, GameRule<Boolean> rule) {
        worlds.forEach(world -> world.setGameRule(rule,
                gameruleRestored(phase, lastMatch, disabled)));
    }

    /** True when the end phase wipes participant inventories for the lobby. */
    public boolean endWipeEnabled(int lobbyId) {
        return resets.endWipeEnabled(lobbyId);
    }

    private void runDefault(String phase, List<Player> participants, List<Player> lobbySpectators, int lobbyId,
                            boolean lastMatch) {
        if (!plugin.overrides().getBoolean(lobbyId, "advanced.advanced-match-controls.game-rules.enabled", true)) {
            return;
        }
        List<String> rules = plugin.overrides().getStringList(lobbyId,
                MatchConfig.GameRules.RULES_PATH);
        if (endWipeEnabled(lobbyId)) {
            participants.forEach(this::resetPlayer);
        }
        if (MatchConfig.GameRules.isRuleEnabled(rules, "AUTO_SET_GAMEMODE")) {
            applyDefaultGamemodes(phase, participants, lobbySpectators, lobbyId);
        }
        applyWorldRules(Bukkit.getWorlds(), phase, lastMatch, rules);
        if (MatchConfig.GameRules.isRuleEnabled(rules, "SET_DAYTIME")) {
            Bukkit.getWorlds().forEach(this::setDaytime);
        }
    }

    /** Vanilla gamerule applications for one phase. */
    private void applyWorldRules(List<World> worlds, String phase,
            boolean lastMatch, List<String> rules) {
        boolean disableLocatorBar =
                MatchConfig.GameRules.isRuleEnabled(rules, "DISABLE_LOCATOR_BAR");
        worlds.forEach(world -> world.setGameRule(GameRules.LOCATOR_BAR, !disableLocatorBar));
        // Quiet command feedback while a match runs and restore it when
        // the last match ends. Unlike its siblings this rule defaults
        // to off.
        boolean disableFeedback =
                MatchConfig.GameRules.isRuleEnabled(rules, "DISABLE_COMMAND_FEEDBACK");
        worlds.forEach(world -> world.setGameRule(GameRules.SEND_COMMAND_FEEDBACK,
                gameruleRestored(phase, lastMatch, disableFeedback)));
        // Disable phantom spawning while a match runs and restore it when the
        // match ends. The gamerule is re-enabled on the end phase.
        boolean disablePhantoms =
                MatchConfig.GameRules.isRuleEnabled(rules, "DISABLE_PHANTOMS");
        worlds.forEach(world -> world.setGameRule(GameRules.SPAWN_PHANTOMS,
                gameruleRestored(phase, lastMatch, disablePhantoms)));
        worlds.forEach(world -> world.setGameRule(GameRules.IMMEDIATE_RESPAWN,
                MatchConfig.GameRules.isRuleEnabled(rules, "SET_RESPAWN_IMMEDIATE")));
        // Prevent spectators from generating chunks while the match is active.
        // This is the native gamerule equivalent of the old spectator chunk
        // generation toggle and avoids lag from spectators exploring.
        worlds.forEach(world -> world.setGameRule(GameRules.SPECTATORS_GENERATE_CHUNKS, false));
        // Pillager patrols never spawn while a match runs; restored when the last match ends.
        applySpawnGamerule(worlds, phase, lastMatch,
                MatchConfig.GameRules.isRuleEnabled(rules, "DISABLE_PILLAGER_PATROLS"),
                GameRules.SPAWN_PATROLS);
        // Wandering traders never spawn while a match runs; restored when the last match ends.
        applySpawnGamerule(worlds, phase, lastMatch,
                MatchConfig.GameRules.isRuleEnabled(rules, "DISABLE_WANDERING_TRADER"),
                GameRules.SPAWN_WANDERING_TRADERS);
    }

    /**
     * Default modes for a phase: participants to survival, NONEs to fake
     * spectator on start (or back to survival on end) unless AFK. Nobody
     * is teleported here: match travel belongs to the cell teleports.
     */
    private void applyDefaultGamemodes(String phase, List<Player> participants,
            List<Player> lobbySpectators, int lobbyId) {
        // AFK players are skipped above and always left alone; NONEs follow
        // the toggle, keeping their mode like AFK when it is off.
        boolean setNoneSpectator = plugin.overrides().getBoolean(lobbyId,
                "settings.players.roles.turn-nones-spectator.enabled", false);
        for (Player player : participants) {
            plugin.fakeSpectators().disable(player);
        }
        for (Player player : lobbySpectators) {
            if (playerStates.role(player) == Role.AFK) {
                continue; // AFK players are left alone
            }
            if (phase.equals("start")) {
                if (setNoneSpectator) {
                    plugin.fakeSpectators().enable(player);
                }
            } else {
                plugin.fakeSpectators().disable(player);
            }
        }
    }

    private void setDaytime(World world) {
        try {
            world.setTime(0L);
            // Clear any ongoing rain/storm so a fresh match starts with clear
            // skies, matching the behaviour of a newly generated world.
            world.setStorm(false);
            world.setWeatherDuration(0);
        } catch (IllegalArgumentException exception) {
            plugin.logger().fine("Skipping daytime reset in world without a world clock: " + world.getName());
        }
    }

    /** Runs ON_START modifiers that do not wait out the pre-start window. */
    private void runModifierStarts(long matchId) {
        for (String name : intervals.enabledModifiers(matchId)) {
            for (int index : configService.behaviorIndexes(name)) {
                if (ModifierTriggers.runsOn(configService.runsOn(name, index), "ON_START")
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
        Collection<String> blocked = configService.getStringList(BLACKLISTED_COMMANDS_PATH);
        for (int lineIndex = 0; lineIndex < commands.size(); lineIndex++) {
            String command = commands.get(lineIndex);
            context.setProvenance(base.withLine(lineIndex));
            if (command.isBlank()) {
                continue;
            }
            if (TagExpressions.isExitMisuse(command)) {
                context.scope().warn("'exit' must stand alone on its line, skipping: " + command);
                continue;
            }
            try {
                String playerName = player != null ? player.getName() : null;
                double x = player != null ? player.getLocation().getX() : 0.0;
                double y = player != null ? player.getLocation().getY() : 0.0;
                double z = player != null ? player.getLocation().getZ() : 0.0;
                for (String expanded : CommandPlaceholders.expandAllPlayers(command, context.scope())) {
                    String parsed = CommandPlaceholders.replace(expanded, playerName, x, y, z, context);
                    if (playerName != null) {
                        parsed = context.placeholders().resolve(parsed, playerName);
                    }
                    if (TagExpressions.isExit(parsed)) {
                        return;
                    }
                    if (TagExpressions.isExitMisuse(parsed)) {
                        context.scope().warn("'exit' must stand alone on its line, skipping: " + command);
                        continue;
                    }
                    if (!dispatchModifierLine(parsed, context, blocked)) {
                        return;
                    }
                }
            } catch (Exception e) {
                plugin.logger().severe("Failed to run command '%s'. Skipping..".formatted(command));
                e.printStackTrace();
            }
        }
    }

    /**
     * Dispatches one parsed modifier line as console, false when the
     * line names a blacklisted command: the hit logs severe and the
     * caller aborts the rest of the list. A line that resolved to
     * pure {@code "null"} warns with the source line and never
     * dispatches, like blank lines.
     */
    private boolean dispatchModifierLine(String parsed, TagContext context,
            Collection<String> blocked) {
        Optional<String> dispatchable = TagExpressions.dispatchableLine(parsed);
        if (dispatchable.isPresent() && TagExpressions.isPureNull(dispatchable.get())) {
            plugin.logger().warning("Skipping command that resolved to pure \"null\" at "
                    + context.provenance().describe() + ".");
            return true;
        }
        if (dispatchable.isPresent()
                && CommandSyntax.isBlockedCommand(dispatchable.get(), blocked)) {
            plugin.logger().severe("Blocked blacklisted modifier command '"
                    + dispatchable.get() + "' at " + context.provenance().describe()
                    + "; aborting the command list.");
            return false;
        }
        dispatchable.ifPresent(line ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line));
        return true;
    }

    /** Full match-end style wipe for one player. */
    public void resetPlayer(Player player) {
        resets.resetPlayer(player);
    }

    /**
     * Defers the match-end wipe for players who are offline at
     * teardown. Memory only: matches never survive a restart anyway.
     */
    public void markPendingEndWipe(Collection<UUID> playerIds) {
        pendingEndWipes.addAll(playerIds);
    }

    /**
     * Runs a deferred match-end wipe for a rejoiner. Returns true
     * when a wipe was pending and ran.
     */
    public boolean applyPendingEndWipe(Player player) {
        if (!pendingEndWipes.remove(player.getUniqueId())) {
            return false;
        }
        resetPlayer(player);
        return true;
    }

    /** Vitals-only reset for one player. */
    public void resetVitals(Player player) {
        resets.resetVitals(player);
    }
}