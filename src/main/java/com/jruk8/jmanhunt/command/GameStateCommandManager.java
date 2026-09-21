package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.core.JManhuntPlugin;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/** Executes built-in and configured actions at match state transitions. */
public final class GameStateCommandManager {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final ConfigService configService;
    private final LobbyTeleporter lobbyTeleporter;
    private final GameManager game;
    /** One interval engine per live match, keyed by match id. */
    private final Map<Long, IntervalEngine> intervalEngines = new HashMap<>();

    /** Per-match interval engine state; each match runs its own chains. */
    private static final class IntervalEngine {
        final List<BukkitTask> tasks = new ArrayList<>();
        final List<BukkitTask> delayed = new ArrayList<>();
        /** Bumped every time interval chains are (re)started so stale firings stop. */
        long generation;
        /** Modifiers with per-executor timing: name to player ids owning a chain. */
        final Map<String, Set<UUID>> executors = new HashMap<>();
        /** Modifiers with per-executor timing that currently own a console chain. */
        final Set<String> consoleChained = new HashSet<>();
    }

    private IntervalEngine engine(long matchId) {
        return intervalEngines.computeIfAbsent(matchId, ignored -> new IntervalEngine());
    }

    public GameStateCommandManager(JManhuntPlugin plugin, PlayerStateStore playerStates,
                                   ConfigService configService, LobbyTeleporter lobbyTeleporter,
                                   GameManager game) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.configService = configService;
        this.lobbyTeleporter = lobbyTeleporter;
        this.game = game;
    }

    public void runStart(long matchId, List<Player> participants, List<Player> lobbySpectators, int lobbyId) {
        cancelPendingDelayed(matchId);
        runDefault("start", participants, lobbySpectators, lobbyId, false);
        runConfigured("start", participants, matchId);
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
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            if (!runsOnContains(name, "ON_START")) continue;
            if (!afterPrestart(name)) continue;
            runModifierCommands(name, matchId);
        }
    }

    /** True when the modifier defers its ON_START sequence past the pre-start hit. */
    private boolean afterPrestart(String name) {
        return runsAfterPrestart(plugin.getConfig()
                .getString("custom-modifiers." + name + ".on-start.pre-start-order", "BEFORE"));
    }

    public void runEnd(long matchId, List<Player> participants, List<Player> lobbySpectators, int lobbyId,
                       boolean lastMatch) {
        cancelPendingDelayed(matchId);
        runConfigured("end", participants, matchId);
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

    public void runConsoleCleanup() {
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            runCommands("custom-modifiers." + name + ".commands.console-cleanup", null);
        }
    }

    public void runPlayerCleanup(List<Player> participants) {
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            for (Player player : participants) {
                runCommands("custom-modifiers." + name + ".commands.player-cleanup", player);
            }
        }
    }

    /** Per-activation versus per-executor random behavior. */
    enum TriggerScope {
        PER_INVOKE, PER_EXECUTOR
    }

    /** Command selection mode for {@code commands.execution.selection}. */
    enum Selection {
        IN_ORDER, PICK_RANDOM
    }

    /**
     * Starts interval-based custom modifiers. Should be called when the game
     * begins (via {@link GameManager#beginGame()}). Modifiers whose
     * {@code runs-on} list contains INTERVAL are scheduled on a repeating task.
     * Supports decimal intervals: 0-0.05 seconds executes every tick, rounds to
     * the nearest tick. When {@code interval-settings.deviation} is above zero
     * the delay is re-rolled every firing within {@code interval ± deviation};
     * {@code PER_EXECUTOR} deviation fans out to one chain per player plus one
     * console chain instead of a single shared chain.
     */
    public void startIntervalModifiers(long matchId) {
        cancelIntervalModifiers(matchId);
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            if (!runsOnContains(name, "INTERVAL")) continue;
            String base = "custom-modifiers." + name + ".interval-settings.";
            double intervalSeconds = plugin.getConfig().getDouble(base + "interval", 60.0);
            if (intervalSeconds < 0) continue;
            double deviation = clampDeviation(plugin.getConfig().getDouble(base + "deviation", 0.0), intervalSeconds);
            TriggerScope scope = parseScope(plugin.getConfig().getString(base + "behavior", "PER_INVOKE"));
            if (deviation > 0.0 && scope == TriggerScope.PER_EXECUTOR) {
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
        double intervalSeconds = plugin.getConfig()
                .getDouble("custom-modifiers." + name + ".interval-settings.interval", 60.0);
        if (intervalSeconds < 0) return;
        double deviation = clampDeviation(plugin.getConfig()
                .getDouble("custom-modifiers." + name + ".interval-settings.deviation", 0.0), intervalSeconds);
        if (deviation <= 0.0) {
            long intervalTicks = secondsToTicks(intervalSeconds);
            AtomicReference<BukkitTask> ref = new AtomicReference<>();
            ref.set(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (generation != engine.generation || !configService.modifierEnabled(name)) {
                    BukkitTask task = ref.get();
                    if (task != null) task.cancel();
                    engine.tasks.remove(ref.get());
                    return;
                }
                runModifierCommands(name, matchId);
            }, intervalTicks, intervalTicks));
            engine.tasks.add(ref.get());
            return;
        }
        long delayTicks = jitteredIntervalTicks(intervalSeconds, deviation,
                ThreadLocalRandom.current().nextDouble());
        AtomicReference<BukkitTask> ref = new AtomicReference<>();
        ref.set(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            engine.tasks.remove(ref.get());
            if (generation != engine.generation || !configService.modifierEnabled(name)) return;
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
                    || !configService.modifierEnabled(name)) {
                engine.consoleChained.remove(name);
                return;
            }
            runModifierWithDelay(name, () -> dispatchModifier(name, List.of()), matchId);
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
            if (generation != engine.generation || !configService.modifierEnabled(name)) {
                chainedPlayers(matchId, name).remove(playerId);
                return;
            }
            Player target = Bukkit.getPlayer(playerId);
            boolean present = target != null && playerStates.role(target).isParticipant()
                    && game.isActiveInInstance(matchId, playerId);
            if (present) {
                runModifierWithDelay(name, () -> dispatchModifier(name, List.of(target)), matchId);
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
        double intervalSeconds = plugin.getConfig()
                .getDouble("custom-modifiers." + name + ".interval-settings.interval", 60.0);
        if (intervalSeconds < 0) return -1;
        double deviation = clampDeviation(plugin.getConfig()
                .getDouble("custom-modifiers." + name + ".interval-settings.deviation", 0.0), intervalSeconds);
        return jitteredIntervalTicks(intervalSeconds, deviation, ThreadLocalRandom.current().nextDouble());
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
        if (player == null) return;
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            if (!runsOnContains(name, event)) continue;
            runModifierWithDelay(name, () -> dispatchModifier(name, List.of(player)), matchId);
        }
    }

    /**
     * Runs a modifier's trigger dispatch after its configured {@code delay}
     * in ticks. Positions and roles resolve when delayed commands fire, not
     * when they trigger. Cleanup commands never go through here.
     */
    private void runModifierWithDelay(String name, Runnable dispatch, long matchId) {
        long delay = Math.max(0L, plugin.getConfig().getLong("custom-modifiers." + name + ".delay", 0L));
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
                if (!isStaleDispatch(generation, engine.generation, intervalEngines.get(matchId) == engine)) {
                    dispatch.run();
                }
            } finally {
                engine.delayed.remove(ref.get());
            }
        }, delay));
        engine.delayed.add(ref.get());
    }

    /**
     * True when a delayed dispatch must not fire: its engine restarted
     * (new generation) or was torn down. Pure for tests.
     */
    static boolean isStaleDispatch(long capturedGeneration, long currentGeneration, boolean engineAlive) {
        return capturedGeneration != currentGeneration || !engineAlive;
    }

    /**
     * Canonicalizes a {@code runs-on} trigger name. Currently this only trims
     * surrounding whitespace; unknown keys (including the removed legacy
     * {@code ON_FIRST_ENTER_NETHER} / {@code ON_FIRST_ENTER_END} aliases)
     * pass through unchanged and therefore never match an event.
     */
    static String normalizeTrigger(String trigger) {
        if (trigger == null) {
            return null;
        }
        return trigger.trim();
    }

    private boolean runsOnContains(String name, String event) {
        List<String> runsOn = plugin.getConfig().getStringList("custom-modifiers." + name + ".runs-on");
        String canonical = normalizeTrigger(event);
        // When runs-on is omitted, default to ON_START.
        if (runsOn.isEmpty()) {
            return "ON_START".equalsIgnoreCase(canonical);
        }
        return runsOn.stream().map(GameStateCommandManager::normalizeTrigger).anyMatch(canonical::equalsIgnoreCase);
    }

    private void runModifierCommands(String name, long matchId) {
        runModifierWithDelay(name, () -> dispatchModifier(name, game.onlineParticipants(matchId)), matchId);
    }

    /**
     * Dispatches one modifier activation to its executors. {@code targets} are
     * the players this activation covers: every participant for start/interval
     * triggers, or just the involved player for event triggers. Console always
     * counts as its own executor. Cleanup commands never pass through here.
     */
    private void dispatchModifier(String name, List<Player> targets) {
        double chance = clampChance(plugin.getConfig()
                .getDouble("custom-modifiers." + name + ".success-chance.chance", 1.0));
        TriggerScope chanceScope = parseScope(plugin.getConfig()
                .getString("custom-modifiers." + name + ".success-chance.behavior", "PER_INVOKE"));
        TriggerScope pickScope = parseScope(plugin.getConfig().getString(
                "custom-modifiers." + name + ".commands.execution.pick-random.behavior", "PER_INVOKE"));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean sharedPicks = pickScope == TriggerScope.PER_INVOKE;
        Map<String, List<String>> shared = new HashMap<>();
        if (sharedPicks) {
            for (String list : List.of("console", "player", "hunter", "speedrunner")) {
                shared.put(list, resolveCommandList(name, list));
            }
        }
        if (chanceScope == TriggerScope.PER_EXECUTOR) {
            if (rollChance(chance, random.nextDouble())) {
                runCommandList(sharedPicks ? shared.get("console") : resolveCommandList(name, "console"), null);
            }
            for (Player target : targets) {
                if (!rollChance(chance, random.nextDouble())) continue;
                runExecutorPlayerLists(name, target, shared, sharedPicks);
            }
            return;
        }
        if (!rollChance(chance, random.nextDouble())) return;
        runCommandList(shared.get("console"), null);
        for (Player target : targets) {
            runExecutorPlayerLists(name, target, shared, true);
        }
    }

    private void runExecutorPlayerLists(String name, Player target, Map<String, List<String>> shared,
                                        boolean useShared) {
        runCommandList(useShared ? shared.get("player") : resolveCommandList(name, "player"), target);
        String roleCommands = playerStates.role(target) == Role.HUNTER ? "hunter" : "speedrunner";
        runCommandList(useShared ? shared.get(roleCommands) : resolveCommandList(name, roleCommands), target);
    }

    /**
     * Resolves one command list under the modifier's {@code commands.execution}
     * settings. {@code IN_ORDER} (the default) returns every line; {@code
     * PICK_RANDOM} returns {@code pick-random.count} randomly drawn lines.
     */
    private List<String> resolveCommandList(String name, String listKey) {
        List<String> commands = plugin.getConfig()
                .getStringList("custom-modifiers." + name + ".commands." + listKey);
        String execBase = "custom-modifiers." + name + ".commands.execution.";
        if (parseSelection(plugin.getConfig().getString(execBase + "selection", "IN_ORDER"))
                != Selection.PICK_RANDOM) {
            return commands;
        }
        int count = Math.max(1, plugin.getConfig().getInt(execBase + "pick-random.count", 1));
        return pickCommands(commands, count, ThreadLocalRandom.current());
    }

    /** Parses a {@code PER_INVOKE} / {@code PER_EXECUTOR} behavior key. */
    static TriggerScope parseScope(String raw) {
        if (raw != null && raw.trim().equalsIgnoreCase("PER_EXECUTOR")) return TriggerScope.PER_EXECUTOR;
        return TriggerScope.PER_INVOKE;
    }

    /** Parses a {@code commands.execution.selection} key. */
    static Selection parseSelection(String raw) {
        if (raw != null && raw.trim().equalsIgnoreCase("PICK_RANDOM")) return Selection.PICK_RANDOM;
        return Selection.IN_ORDER;
    }

    /**
     * Parses an {@code on-start.pre-start-order} key. Only AFTER defers
     * the ON_START sequence past the pre-start hit; anything else runs at
     * match start.
     */
    static boolean runsAfterPrestart(String raw) {
        return raw != null && raw.trim().equalsIgnoreCase("AFTER");
    }

    /**
     * Clamps a {@code success-chance.chance} value to the 0.0-1.0 decimal
     * fraction range. Unset or unreadable values fall back to 1.0 upstream.
     */
    static double clampChance(double chance) {
        if (Double.isNaN(chance)) return 1.0;
        return Math.min(1.0, Math.max(0.0, chance));
    }

    /** Rolls a clamped chance against a {@code [0.0, 1.0)} draw. */
    static boolean rollChance(double clampedChance, double roll) {
        return roll < clampedChance;
    }

    /** Clamps {@code interval-settings.deviation} to {@code [0, interval]}. */
    static double clampDeviation(double deviation, double intervalSeconds) {
        if (Double.isNaN(deviation) || deviation <= 0.0) return 0.0;
        if (Double.isNaN(intervalSeconds) || intervalSeconds <= 0.0) return 0.0;
        return Math.min(deviation, intervalSeconds);
    }

    /**
     * Converts seconds to ticks, rounding to the nearest tick. Anything at or
     * below zero ticks becomes a single tick so scheduling never stalls.
     */
    static long secondsToTicks(double seconds) {
        return Math.max(1L, (long) Math.round(seconds * 20.0));
    }

    /**
     * Rolls the next interval delay within {@code [interval - deviation,
     * interval + deviation]} seconds for a {@code [0.0, 1.0)} draw.
     */
    static long jitteredIntervalTicks(double intervalSeconds, double deviationSeconds, double roll) {
        double deviation = clampDeviation(deviationSeconds, intervalSeconds);
        if (deviation <= 0.0) return secondsToTicks(intervalSeconds);
        return secondsToTicks(intervalSeconds - deviation + roll * deviation * 2.0);
    }

    /** Draws up to {@code count} distinct lines from a command list. */
    static List<String> pickCommands(List<String> commands, int count, Random random) {
        if (commands == null || commands.isEmpty() || count <= 0) return List.of();
        List<String> pool = new ArrayList<>(commands);
        Collections.shuffle(pool, random);
        return List.copyOf(pool.subList(0, Math.min(count, pool.size())));
    }

    private void runDefault(String phase, List<Player> participants, List<Player> lobbySpectators, int lobbyId,
                            boolean lastMatch) {
        if (!plugin.getConfig().getBoolean("gamestate-commands.default-commands.enabled", true)) return;
        String path = "gamestate-commands.default-commands.";
        if (plugin.getConfig().getBoolean(path + "reset-players-stats", false)) {
            participants.forEach(this::resetPlayer);
        }
        if (plugin.getConfig().getBoolean(path + "auto-set-gamemode", false)) {
            // AFK players are skipped above and always left alone; NONEs follow
            // the toggle, keeping their gamemode like AFK when it is off.
            boolean setNoneSpectator = plugin.getConfig().getBoolean("settings.roles.turn-nones-spectator.enabled", false);
            List<Player> nonePlayers = new ArrayList<>();
            for (Player player : participants) {
                player.setGameMode(GameMode.SURVIVAL);
            }
            for (Player player : lobbySpectators) {
                if (playerStates.role(player) == Role.AFK) continue; // AFK players are left alone
                if (phase.equals("start")) {
                    if (setNoneSpectator) player.setGameMode(GameMode.SPECTATOR);
                    nonePlayers.add(player);
                } else {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
            // When the world engine is enabled, NONE spectators travel to the
            // match cell with the players instead of waiting in the lobby.
            boolean engineMovesSpectators = phase.equals("start")
                    && setNoneSpectator
                    && plugin.getConfig().getBoolean("world-engine.enabled", false);
            if (!nonePlayers.isEmpty() && !engineMovesSpectators) {
                lobbyTeleporter.teleportToLobby(nonePlayers, lobbyId);
                lobbyTeleporter.setSpawnToLobby(nonePlayers, lobbyId);
            }
        }
        var worlds = Bukkit.getWorlds();
        boolean disableLocatorBar = plugin.getConfig().getBoolean(path + "disable-locator-bar", false);
        worlds.forEach(world -> world.setGameRule(GameRules.LOCATOR_BAR, !disableLocatorBar));
        // Disable phantom spawning while a match runs and restore it when the
        // match ends. The gamerule is re-enabled on the end phase.
        boolean disablePhantoms = plugin.getConfig().getBoolean(path + "disable-phantoms", false);
        GameRule doInsomnia = Registry.GAME_RULE.get(NamespacedKey.minecraft("do_insomnia"));
        if (doInsomnia != null) {
            boolean phantomsEnabled = (phase.equals("end") && lastMatch) || !disablePhantoms;
            worlds.forEach(world -> world.setGameRule(doInsomnia, phantomsEnabled));
        }
        worlds.forEach(world -> world.setGameRule(GameRules.IMMEDIATE_RESPAWN,
                plugin.getConfig().getBoolean(path + "set-respawn-immediate", false)));
        // Prevent spectators from generating chunks while the match is active.
        // This is the native gamerule equivalent of the old spectator chunk
        // generation toggle and avoids lag from spectators exploring.
        worlds.forEach(world -> world.setGameRule(GameRules.SPECTATORS_GENERATE_CHUNKS, false));
        // Pillager patrols never spawn while a match runs; restored when the
        // last match ends. Unconditional like the spectator chunk rule above.
        GameRule spawnPatrols = Registry.GAME_RULE.get(NamespacedKey.minecraft("spawn_patrols"));
        if (spawnPatrols != null) {
            boolean patrolsEnabled = phase.equals("end") && lastMatch;
            worlds.forEach(world -> world.setGameRule(spawnPatrols, patrolsEnabled));
        }
        if (plugin.getConfig().getBoolean(path + "set-daytime", false)) {
            Bukkit.getWorlds().forEach(this::setDaytime);
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

    private void runConfigured(String phase, List<Player> participants, long matchId) {
        String base = "gamestate-commands.";
        if (plugin.getConfig().getBoolean(base + "console-commands.enabled", false)) {
            runCommands(base + "console-commands." + phase, null);
        }
        if (plugin.getConfig().getBoolean(base + "player-commands.enabled", false)) {
            for (Player player : participants) {
                runCommands(base + "player-commands." + phase, player);
            }
        }
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            if (phase.equals("start") && runsOnContains(name, "ON_START") && !afterPrestart(name)) {
                runModifierCommands(name, matchId);
            }
        }
    }

    private void runCommands(String path, Player player) {
        runCommandList(plugin.getConfig().getStringList(path), player);
    }

    private void runCommandList(List<String> commands, Player player) {
        for (String command : commands) {
            if (command.isBlank()) continue;
            try {
                String playerName = player != null ? player.getName() : null;
                double x = player != null ? player.getLocation().getX() : 0.0;
                double y = player != null ? player.getLocation().getY() : 0.0;
                double z = player != null ? player.getLocation().getZ() : 0.0;
                String parsed = CommandPlaceholders.replace(command, playerName, x, y, z);
                if (parsed.startsWith("/")) parsed = parsed.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
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