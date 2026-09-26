package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.CommandPlaceholders;
import com.jruk8.jmanhunt.command.ModifierTagScope;
import com.jruk8.jmanhunt.command.TagBackends;
import com.jruk8.jmanhunt.command.TagContext;
import com.jruk8.jmanhunt.command.TagExpressions;
import com.jruk8.jmanhunt.core.PlaceholderPass;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/** Executes game rules and modifiers at match state transitions. */
public final class GameStateCommandManager {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final ConfigService configService;
    private final MessageService messages;
    private final SoundService sounds;
    private final LobbyTeleporter lobbyTeleporter;
    private final GameManager game;
    /** One interval engine per live match, keyed by match id. */
    private final Map<Long, IntervalEngine> intervalEngines = new HashMap<>();

    private IntervalEngine engine(long matchId) {
        return intervalEngines.computeIfAbsent(matchId, ignored -> new IntervalEngine());
    }

    /** Names of all modifiers effectively enabled for one match's lobby. */
    private List<String> enabledModifiers(long matchId) {
        Integer lobby = lobbyOf(matchId);
        List<String> enabled = new ArrayList<>();
        for (String name : configService.modifierNames()) {
            if (plugin.overrides().modifierEnabled(lobby, name)) {
                enabled.add(name);
            }
        }
        return enabled;
    }

    /** Origin lobby of a match for override resolution, or null when gone. */
    private Integer lobbyOf(long matchId) {
        return game.lobbyOf(matchId);
    }

    public GameStateCommandManager(JManhuntPlugin plugin, PlayerStateStore playerStates,
                                   ConfigService configService, MessageService messages,
                                   SoundService sounds, LobbyTeleporter lobbyTeleporter,
                                   GameManager game) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.configService = configService;
        this.messages = messages;
        this.sounds = sounds;
        this.lobbyTeleporter = lobbyTeleporter;
        this.game = game;
    }

    public void runStart(long matchId, List<Player> participants, List<Player> lobbySpectators, int lobbyId) {
        cancelPendingDelayed(matchId);
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
        for (String name : enabledModifiers(matchId)) {
            if (!runsOnContains(name, "ON_START")) {
                continue;
            }
            if (!afterPrestart(name)) {
                continue;
            }
            runModifierCommands(name, matchId);
        }
    }

    /** True when the modifier defers its ON_START sequence past the pre-start hit. */
    private boolean afterPrestart(String name) {
        return ModifierTriggers.runsAfterPrestart(configService.preStartOrder(name));
    }

    public void runEnd(long matchId, List<Player> participants, List<Player> lobbySpectators, int lobbyId,
                       boolean lastMatch) {
        cancelPendingDelayed(matchId);
        runDefault("end", participants, lobbySpectators, lobbyId, lastMatch);
    }

    /** Drops delayed modifier commands that never fired, e.g. at match end. */
    private void cancelPendingDelayed(long matchId) {
        IntervalEngine engine = engine(matchId);
        for (BukkitTask task : engine.delayed) {
            task.cancel();
        }
        engine.delayed.clear();
    }

    public void runConsoleCleanup(long matchId) {
        for (String name : enabledModifiers(matchId)) {
            ModifierTagScope scope = ModifierTagScope.executor(null, plugin.logger()::warning);
            runCommandList(configService.commandList(name, "console-cleanup"), null,
                    tagContext(name, null, scope, matchId));
        }
    }

    public void runPlayerCleanup(long matchId, List<Player> participants) {
        for (String name : enabledModifiers(matchId)) {
            for (Player player : participants) {
                runCommandList(configService.commandList(name, "player-cleanup"), player,
                        tagContext(name, player, matchScope(player, participants), matchId));
            }
        }
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

    /**
     * Starts interval-based modifiers. Should be called when the game
     * begins (via {@link GameManager#beginGame()}). Modifiers whose
     * {@code runs-on} list contains INTERVAL are scheduled on a repeating task.
     * Supports decimal intervals: 0-0.05 seconds executes every tick, rounds to
     * the nearest tick. When {@code options.interval-settings.deviation} is above zero
     * the delay is re-rolled every firing within {@code interval ± deviation};
     * {@code PER_EXECUTOR} deviation fans out to one chain per player plus one
     * console chain instead of a single shared chain.
     */
    public void startIntervalModifiers(long matchId) {
        cancelIntervalModifiers(matchId);
        for (String name : enabledModifiers(matchId)) {
            if (!runsOnContains(name, "INTERVAL")) {
                continue;
            }
            double intervalSeconds = configService.intervalSeconds(name);
            if (intervalSeconds < 0) {
                continue;
            }
            double deviation = ModifierTriggers.clampDeviation(
                    configService.intervalDeviation(name), intervalSeconds);
            ModifierTriggers.TriggerScope scope = ModifierTriggers.parseScope(
                    configService.intervalBehavior(name));
            if (deviation > 0.0 && scope == ModifierTriggers.TriggerScope.PER_EXECUTOR) {
                startPerExecutorInterval(name, matchId);
            } else {
                scheduleSharedFiring(name, matchId);
            }
        }
    }

    /** Cancels one match's running interval modifier tasks, including delayed firings. */
    public void cancelIntervalModifiers(long matchId) {
        IntervalEngine engine = engine(matchId);
        engine.generation++;
        for (BukkitTask task : engine.tasks) {
            task.cancel();
        }
        engine.tasks.clear();
        for (BukkitTask task : engine.delayed) {
            task.cancel();
        }
        engine.delayed.clear();
        engine.executors.clear();
        engine.consoleChained.clear();
        intervalEngines.remove(matchId);
    }

    /** Cancels every match's interval tasks, e.g. on reload. */
    public void cancelAllIntervalModifiers() {
        for (long matchId : List.copyOf(intervalEngines.keySet())) {
            cancelIntervalModifiers(matchId);
        }
    }

    /**
     * Schedules the next firing of a shared-timing interval modifier. Fixed
     * cadences stay on a plain repeating task; deviated ones re-roll the delay
     * every firing. Interval values are re-read each cycle so reloads apply
     * without a match restart.
     */
    private void scheduleSharedFiring(String name, long matchId) {
        IntervalEngine engine = engine(matchId);
        long generation = engine.generation;
        double intervalSeconds = configService.intervalSeconds(name);
        if (intervalSeconds < 0) {
            return;
        }
        double deviation = ModifierTriggers.clampDeviation(
                configService.intervalDeviation(name), intervalSeconds);
        if (deviation <= 0.0) {
            long intervalTicks = ModifierTriggers.secondsToTicks(intervalSeconds);
            AtomicReference<BukkitTask> ref = new AtomicReference<>();
            ref.set(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (generation != engine.generation
                        || !plugin.overrides().modifierEnabled(lobbyOf(matchId), name)) {
                    BukkitTask task = ref.get();
                    if (task != null) {
                        task.cancel();
                    }
                    engine.tasks.remove(ref.get());
                    return;
                }
                runModifierCommands(name, matchId);
            }, intervalTicks, intervalTicks));
            engine.tasks.add(ref.get());
            return;
        }
        long delayTicks = ModifierTriggers.jitteredIntervalTicks(intervalSeconds, deviation,
                ThreadLocalRandom.current().nextDouble());
        AtomicReference<BukkitTask> ref = new AtomicReference<>();
        ref.set(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            engine.tasks.remove(ref.get());
            if (generation != engine.generation
                    || !plugin.overrides().modifierEnabled(lobbyOf(matchId), name)) {
                return;
            }
            runModifierCommands(name, matchId);
            scheduleSharedFiring(name, matchId);
        }, delayTicks));
        engine.tasks.add(ref.get());
    }

    /** Starts one interval chain per participating player plus a console chain. */
    private void startPerExecutorInterval(String name, long matchId) {
        engine(matchId).consoleChained.add(name);
        scheduleConsoleFiring(name, matchId);
        for (Player player : game.onlineParticipants(matchId)) {
            if (chainedPlayers(matchId, name).add(player.getUniqueId())) {
                schedulePlayerFiring(name, player.getUniqueId(), matchId);
            }
        }
    }

    /** Schedules the next firing of a per-executor console chain. */
    private void scheduleConsoleFiring(String name, long matchId) {
        IntervalEngine engine = engine(matchId);
        long generation = engine.generation;
        long delayTicks = currentJitteredDelay(name);
        if (delayTicks < 0) {
            engine.consoleChained.remove(name);
            return;
        }
        AtomicReference<BukkitTask> ref = new AtomicReference<>();
        ref.set(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            engine.tasks.remove(ref.get());
            if (generation != engine.generation || !engine.consoleChained.contains(name)
                    || !plugin.overrides().modifierEnabled(lobbyOf(matchId), name)) {
                engine.consoleChained.remove(name);
                return;
            }
            runModifierWithDelay(name, () -> dispatchModifier(name, List.of(), matchId), matchId);
            reconcilePlayerChains(name, matchId);
            scheduleConsoleFiring(name, matchId);
        }, delayTicks));
        engine.tasks.add(ref.get());
    }

    /** Schedules the next firing of one player's interval chain. */
    private void schedulePlayerFiring(String name, UUID playerId, long matchId) {
        IntervalEngine engine = engine(matchId);
        long generation = engine.generation;
        long delayTicks = currentJitteredDelay(name);
        if (delayTicks < 0) {
            chainedPlayers(matchId, name).remove(playerId);
            return;
        }
        AtomicReference<BukkitTask> ref = new AtomicReference<>();
        ref.set(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            engine.tasks.remove(ref.get());
            if (generation != engine.generation
                    || !plugin.overrides().modifierEnabled(lobbyOf(matchId), name)) {
                chainedPlayers(matchId, name).remove(playerId);
                return;
            }
            Player target = Bukkit.getPlayer(playerId);
            boolean present = target != null && playerStates.role(target).isParticipant()
                    && game.isActiveInInstance(matchId, playerId);
            if (present) {
                runModifierWithDelay(name, () -> dispatchModifier(name, List.of(target), matchId), matchId);
            } else {
                chainedPlayers(matchId, name).remove(playerId);
            }
            reconcilePlayerChains(name, matchId);
            if (present && chainedPlayers(matchId, name).contains(playerId)) {
                schedulePlayerFiring(name, playerId, matchId);
            }
        }, delayTicks));
        engine.tasks.add(ref.get());
    }

    /** Reads the live interval config and rolls the next delay, or -1 when disabled. */
    private long currentJitteredDelay(String name) {
        double intervalSeconds = configService.intervalSeconds(name);
        if (intervalSeconds < 0) {
            return -1;
        }
        double deviation = ModifierTriggers.clampDeviation(
                configService.intervalDeviation(name), intervalSeconds);
        return ModifierTriggers.jitteredIntervalTicks(intervalSeconds, deviation,
                ThreadLocalRandom.current().nextDouble());
    }

    private Set<UUID> chainedPlayers(long matchId, String name) {
        return engine(matchId).executors.computeIfAbsent(name, key -> new HashSet<>());
    }

    /** Starts chains for participants that joined after the modifier began. */
    private void reconcilePlayerChains(String name, long matchId) {
        Set<UUID> active = chainedPlayers(matchId, name);
        for (Player player : game.onlineParticipants(matchId)) {
            if (active.add(player.getUniqueId())) {
                schedulePlayerFiring(name, player.getUniqueId(), matchId);
            }
        }
    }

    /**
     * Runs modifiers whose {@code runs-on} list contains the given event for
     * the specific player involved in the event. The player's own
     * {@code player}/{@code hunter}/{@code speedrunner} commands run, plus the
     * modifier's console commands.
     *
     * @param event  the event name (e.g. ON_EVERY_KILL)
     * @param player the player involved in the event
     * @param matchId the match the event belongs to
     */
    public void runEventModifiers(String event, Player player, long matchId) {
        if (player == null) {
            return;
        }
        for (String name : enabledModifiers(matchId)) {
            if (!runsOnContains(name, event)) {
                continue;
            }
            runModifierWithDelay(name, () -> dispatchModifier(name, List.of(player), matchId), matchId);
        }
    }

    /**
     * Runs a modifier's trigger dispatch after its configured {@code delay}
     * in ticks. Positions and roles resolve when delayed commands fire, not
     * when they trigger. Cleanup commands never go through here.
     */
    private void runModifierWithDelay(String name, Runnable dispatch, long matchId) {
        long delay = Math.max(0L, configService.delayTicks(name));
        if (delay <= 0L) {
            dispatch.run();
            return;
        }
        IntervalEngine engine = engine(matchId);
        long generation = engine.generation;
        AtomicReference<BukkitTask> ref = new AtomicReference<>();
        ref.set(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                // A restart or teardown between scheduling and firing voids
                // the dispatch: stale interval output never leaks into a
                // new match.
                if (!ModifierTriggers.isStaleDispatch(generation, engine.generation,
                        intervalEngines.get(matchId) == engine)) {
                    dispatch.run();
                }
            } finally {
                engine.delayed.remove(ref.get());
            }
        }, delay));
        engine.delayed.add(ref.get());
    }

    private boolean runsOnContains(String name, String event) {
        List<String> runsOn = configService.runsOn(name);
        String canonical = ModifierTriggers.normalizeTrigger(event);
        // When runs-on is omitted, default to ON_START.
        if (runsOn.isEmpty()) {
            return "ON_START".equalsIgnoreCase(canonical);
        }
        return runsOn.stream().map(ModifierTriggers::normalizeTrigger).anyMatch(canonical::equalsIgnoreCase);
    }

    private void runModifierCommands(String name, long matchId) {
        runModifierWithDelay(name, () -> dispatchModifier(name, game.onlineParticipants(matchId), matchId), matchId);
    }

    /**
     * Dispatches one modifier activation to its executors. {@code targets} are
     * the players this activation covers: every participant for start/interval
     * triggers, or just the involved player for event triggers. Console always
     * counts as its own executor. Match tags ({@code <all-players>},
     * {@code <random-player>}) always resolve against the full match roster so
     * event triggers stay match-scoped too. Cleanup commands never pass here.
     */
    private void dispatchModifier(String name, List<Player> targets, long matchId) {
        List<Player> match = game.onlineParticipants(matchId);
        double chance = ModifierTriggers.clampChance(configService.chance(name));
        ModifierTriggers.TriggerScope chanceScope = ModifierTriggers.parseScope(configService.chanceBehavior(name));
        ModifierTriggers.TriggerScope pickScope = ModifierTriggers.parseScope(configService.pickBehavior(name));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean sharedPicks = pickScope == ModifierTriggers.TriggerScope.PER_INVOKE;
        Map<String, List<String>> shared = sharedLists(name, sharedPicks);
        TagContext consoleContext = tagContext(name, null, matchScope(null, match), matchId);
        if (chanceScope == ModifierTriggers.TriggerScope.PER_EXECUTOR) {
            if (ModifierTriggers.rollChance(chance, random.nextDouble())) {
                runCommandList(sharedPicks ? shared.get("console") : resolveCommandList(name, "console"), null,
                        consoleContext);
            }
            for (Player target : targets) {
                if (!ModifierTriggers.rollChance(chance, random.nextDouble())) {
                    continue;
                }
                runExecutorPlayerLists(name, target, shared, sharedPicks,
                        tagContext(name, target, matchScope(target, match), matchId));
            }
            return;
        }
        if (!ModifierTriggers.rollChance(chance, random.nextDouble())) {
            return;
        }
        // The shared map only exists for PER_INVOKE picks; per-executor
        // picks resolve their lists here instead of reading nulls.
        runCommandList(sharedPicks ? shared.get("console") : resolveCommandList(name, "console"), null,
                consoleContext);
        for (Player target : targets) {
            runExecutorPlayerLists(name, target, shared, sharedPicks,
                    tagContext(name, target, matchScope(target, match), matchId));
        }
    }

    private Map<String, List<String>> sharedLists(String name, boolean sharedPicks) {
        Map<String, List<String>> shared = new HashMap<>();
        if (sharedPicks) {
            // One mob and one item roll for the whole activation: the shared
            // lists carry concrete values, so per-executor tag evaluation
            // downstream finds nothing left to re-roll.
            Map<String, String> sharedDraws = new HashMap<>();
            for (String list : List.of("console", "player", "hunter", "speedrunner")) {
                shared.put(list, CommandPlaceholders.preresolveSharedRandoms(
                        resolveCommandList(name, list), sharedDraws,
                        CommandPlaceholders::rollSharedRandom));
            }
        }
        return shared;
    }

    private void runExecutorPlayerLists(String name, Player target, Map<String, List<String>> shared,
                                        boolean useShared, TagContext context) {
        runCommandList(useShared ? shared.get("player") : resolveCommandList(name, "player"), target, context);
        String roleCommands = playerStates.role(target) == Role.HUNTER ? "hunter" : "speedrunner";
        runCommandList(useShared ? shared.get(roleCommands) : resolveCommandList(name, roleCommands), target,
                context);
    }

    /** Tag context for one modifier dispatch run: id, sinks, stats, flags. */
    private TagContext tagContext(String name, Player executor, ModifierTagScope scope, long matchId) {
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
                matchId, new TagBackends(game.matchStatValues(matchId), game.flagStore(),
                        new PlaceholderPass(plugin.placeholderValues())));
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
    private List<String> resolveCommandList(String name, String listKey) {
        List<String> commands = configService.commandList(name, listKey);
        if (ModifierTriggers.parseSelection(configService.selection(name))
                != ModifierTriggers.Selection.PICK_RANDOM) {
            return commands;
        }
        int count = Math.max(1, configService.pickCount(name));
        return ModifierTriggers.pickCommands(commands, count, ThreadLocalRandom.current());
    }

    /**
     * True when a match-paused gamerule is back on: the end phase of the
     * last match, or any phase when its toggle is off. Pure for tests.
     */
    static boolean gameruleRestored(String phase, boolean lastMatch, boolean toggleEnabled) {
        return (phase.equals("end") && lastMatch) || !toggleEnabled;
    }

    private void runDefault(String phase, List<Player> participants, List<Player> lobbySpectators, int lobbyId,
                            boolean lastMatch) {
        if (!plugin.overrides().getBoolean(lobbyId, "match.game-rules.enabled", true)) {
            return;
        }
        String path = "match.game-rules.rules.";
        if (plugin.overrides().getBoolean(lobbyId, path + "reset-players-stats", false)) {
            participants.forEach(this::resetPlayer);
        }
        if (plugin.overrides().getBoolean(lobbyId, path + "auto-set-gamemode", false)) {
            applyDefaultGamemodes(phase, participants, lobbySpectators, lobbyId);
        }
        var worlds = Bukkit.getWorlds();
        boolean disableLocatorBar =
                plugin.overrides().getBoolean(lobbyId, path + "disable-locator-bar", false);
        worlds.forEach(world -> world.setGameRule(GameRules.LOCATOR_BAR, !disableLocatorBar));
        // Quiet command feedback while a match runs and restore it when
        // the last match ends. Unlike its siblings this toggle defaults
        // to off.
        boolean disableFeedback =
                plugin.overrides().getBoolean(lobbyId, path + "disable-command-feedback", false);
        worlds.forEach(world -> world.setGameRule(GameRules.SEND_COMMAND_FEEDBACK,
                gameruleRestored(phase, lastMatch, disableFeedback)));
        // Disable phantom spawning while a match runs and restore it when the
        // match ends. The gamerule is re-enabled on the end phase.
        boolean disablePhantoms =
                plugin.overrides().getBoolean(lobbyId, path + "disable-phantoms", false);
        worlds.forEach(world -> world.setGameRule(GameRules.SPAWN_PHANTOMS,
                gameruleRestored(phase, lastMatch, disablePhantoms)));
        worlds.forEach(world -> world.setGameRule(GameRules.IMMEDIATE_RESPAWN,
                plugin.overrides().getBoolean(lobbyId, path + "set-respawn-immediate", false)));
        // Prevent spectators from generating chunks while the match is active.
        // This is the native gamerule equivalent of the old spectator chunk
        // generation toggle and avoids lag from spectators exploring.
        worlds.forEach(world -> world.setGameRule(GameRules.SPECTATORS_GENERATE_CHUNKS, false));
        // Pillager patrols never spawn while a match runs; restored when the
        // last match ends.
        boolean disablePatrols =
                plugin.overrides().getBoolean(lobbyId, path + "disable-pillager-patrols", false);
        worlds.forEach(world -> world.setGameRule(GameRules.SPAWN_PATROLS,
                gameruleRestored(phase, lastMatch, disablePatrols)));
        if (plugin.overrides().getBoolean(lobbyId, path + "set-daytime", false)) {
            Bukkit.getWorlds().forEach(this::setDaytime);
        }
    }

    /**
     * Default gamemodes for a phase: participants to survival, NONEs to
     * spectator on start (or back to survival on end) unless AFK, with
     * lobby travel for NONEs when the engine does not move them.
     */
    private void applyDefaultGamemodes(String phase, List<Player> participants,
            List<Player> lobbySpectators, int lobbyId) {
        // AFK players are skipped above and always left alone; NONEs follow
        // the toggle, keeping their gamemode like AFK when it is off.
        boolean setNoneSpectator = plugin.overrides().getBoolean(lobbyId,
                "settings.players.roles.turn-nones-spectator.enabled", false);
        List<Player> nonePlayers = new ArrayList<>();
        for (Player player : participants) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        for (Player player : lobbySpectators) {
            if (playerStates.role(player) == Role.AFK) {
                continue; // AFK players are left alone
            }
            if (phase.equals("start")) {
                if (setNoneSpectator) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
                nonePlayers.add(player);
            } else {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
        // When the world engine is enabled, NONE spectators travel to the
        // match cell with the players instead of waiting in the lobby.
        boolean engineMovesSpectators = phase.equals("start")
                && setNoneSpectator
                && configService.getBoolean("world-engine.enabled", false);
        if (!nonePlayers.isEmpty() && !engineMovesSpectators) {
            lobbyTeleporter.teleportToLobby(nonePlayers, lobbyId);
            lobbyTeleporter.setSpawnToLobbyQuiet(nonePlayers, lobbyId);
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
        for (String name : enabledModifiers(matchId)) {
            if (runsOnContains(name, "ON_START") && !afterPrestart(name)) {
                runModifierCommands(name, matchId);
            }
        }
    }

    private void runCommandList(List<String> commands, Player player, TagContext context) {
        for (String command : commands) {
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
                    if (parsed.startsWith("/")) {
                        parsed = parsed.substring(1);
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
                }
            } catch (Exception e) {
                plugin.logger().severe("Failed to run command '%s'. Skipping..".formatted(command));
                e.printStackTrace();
            }
        }
    }

    /**
     * Full match-end style wipe for one player: inventory, vitals, and
     * advancements. Used by auto-leave so a removed player restarts clean.
     */
    public void resetPlayer(Player player) {
        resetPlayerStats(player, true, true);
    }

    /**
     * Vitals-only reset for one player: no inventory clear, no advancement
     * wipe. Used by voluntary leave after the leaver's gear has dropped.
     */
    public void resetVitals(Player player) {
        resetPlayerStats(player, false, false);
    }

    private void resetPlayerStats(Player player, boolean clearInventory, boolean wipeAdvancements) {
        if (clearInventory) {
            player.getInventory().clear();
        }
        player.setLevel(0);
        player.setExp(0.0f);
        player.clearActivePotionEffects();
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        if (wipeAdvancements) {
            clearAdvancements(player);
        }
    }

    private void clearAdvancements(Player player) {
        Bukkit.advancementIterator().forEachRemaining(advancement ->
                player.getAdvancementProgress(advancement).getAwardedCriteria().forEach(criteria ->
                        player.getAdvancementProgress(advancement).revokeCriteria(criteria)));
    }
}