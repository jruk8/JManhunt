package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.CommandPlaceholders;
import com.jruk8.jmanhunt.command.ModifierTagScope;
import com.jruk8.jmanhunt.config.ConfigService;
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
    private final LobbyTeleporter lobbyTeleporter;
    private final GameManager game;
    /** One interval engine per live match, keyed by match id. */
    private final Map<Long, IntervalEngine> intervalEngines = new HashMap<>();

    private IntervalEngine engine(long matchId) {
        return intervalEngines.computeIfAbsent(matchId, ignored -> new IntervalEngine());
    }

    /** Names of all enabled modifiers. */
    private List<String> enabledModifiers() {
        List<String> enabled = new ArrayList<>();
        for (String name : configService.modifierNames()) {
            if (configService.modifierEnabled(name)) {
                enabled.add(name);
            }
        }
        return enabled;
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
        for (String name : enabledModifiers()) {
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

    public void runConsoleCleanup() {
        ModifierTagScope scope = ModifierTagScope.executor(null, plugin.logger()::warning);
        for (String name : enabledModifiers()) {
            runCommandList(configService.commandList(name, "console-cleanup"), null, scope);
        }
    }

    public void runPlayerCleanup(List<Player> participants) {
        for (String name : enabledModifiers()) {
            for (Player player : participants) {
                runCommandList(configService.commandList(name, "player-cleanup"), player,
                        matchScope(player, participants));
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
     * the nearest tick. When {@code interval-settings.deviation} is above zero
     * the delay is re-rolled every firing within {@code interval ± deviation};
     * {@code PER_EXECUTOR} deviation fans out to one chain per player plus one
     * console chain instead of a single shared chain.
     */
    public void startIntervalModifiers(long matchId) {
        cancelIntervalModifiers(matchId);
        for (String name : enabledModifiers()) {
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
                if (generation != engine.generation || !configService.modifierEnabled(name)) {
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
            if (generation != engine.generation || !configService.modifierEnabled(name)) {
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
                    || !configService.modifierEnabled(name)) {
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
            if (generation != engine.generation || !configService.modifierEnabled(name)) {
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
        for (String name : enabledModifiers()) {
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
        Map<String, List<String>> shared = new HashMap<>();
        if (sharedPicks) {
            for (String list : List.of("console", "player", "hunter", "speedrunner")) {
                shared.put(list, resolveCommandList(name, list));
            }
        }
        ModifierTagScope consoleScope = matchScope(null, match);
        if (chanceScope == ModifierTriggers.TriggerScope.PER_EXECUTOR) {
            if (ModifierTriggers.rollChance(chance, random.nextDouble())) {
                runCommandList(sharedPicks ? shared.get("console") : resolveCommandList(name, "console"), null,
                        consoleScope);
            }
            for (Player target : targets) {
                if (!ModifierTriggers.rollChance(chance, random.nextDouble())) {
                    continue;
                }
                runExecutorPlayerLists(name, target, shared, sharedPicks, matchScope(target, match));
            }
            return;
        }
        if (!ModifierTriggers.rollChance(chance, random.nextDouble())) {
            return;
        }
        runCommandList(shared.get("console"), null, consoleScope);
        for (Player target : targets) {
            runExecutorPlayerLists(name, target, shared, true, matchScope(target, match));
        }
    }

    private void runExecutorPlayerLists(String name, Player target, Map<String, List<String>> shared,
                                        boolean useShared, ModifierTagScope scope) {
        runCommandList(useShared ? shared.get("player") : resolveCommandList(name, "player"), target, scope);
        String roleCommands = playerStates.role(target) == Role.HUNTER ? "hunter" : "speedrunner";
        runCommandList(useShared ? shared.get(roleCommands) : resolveCommandList(name, roleCommands), target,
                scope);
    }

    /**
     * Resolves one command list under the modifier's {@code commands.execution}
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
        if (!configService.getBoolean("match.game-rules.enabled", true)) {
            return;
        }
        String path = "match.game-rules.rules.";
        if (configService.getBoolean(path + "reset-players-stats", false)) {
            participants.forEach(this::resetPlayer);
        }
        if (configService.getBoolean(path + "auto-set-gamemode", false)) {
            applyDefaultGamemodes(phase, participants, lobbySpectators, lobbyId);
        }
        var worlds = Bukkit.getWorlds();
        boolean disableLocatorBar = configService.getBoolean(path + "disable-locator-bar", false);
        worlds.forEach(world -> world.setGameRule(GameRules.LOCATOR_BAR, !disableLocatorBar));
        // Quiet command feedback while a match runs and restore it when
        // the last match ends. Unlike its siblings this toggle defaults
        // to off.
        boolean disableFeedback = configService.getBoolean(path + "disable-command-feedback", false);
        worlds.forEach(world -> world.setGameRule(GameRules.SEND_COMMAND_FEEDBACK,
                gameruleRestored(phase, lastMatch, disableFeedback)));
        // Disable phantom spawning while a match runs and restore it when the
        // match ends. The gamerule is re-enabled on the end phase.
        boolean disablePhantoms = configService.getBoolean(path + "disable-phantoms", false);
        worlds.forEach(world -> world.setGameRule(GameRules.SPAWN_PHANTOMS,
                gameruleRestored(phase, lastMatch, disablePhantoms)));
        worlds.forEach(world -> world.setGameRule(GameRules.IMMEDIATE_RESPAWN,
                configService.getBoolean(path + "set-respawn-immediate", false)));
        // Prevent spectators from generating chunks while the match is active.
        // This is the native gamerule equivalent of the old spectator chunk
        // generation toggle and avoids lag from spectators exploring.
        worlds.forEach(world -> world.setGameRule(GameRules.SPECTATORS_GENERATE_CHUNKS, false));
        // Pillager patrols never spawn while a match runs; restored when the
        // last match ends.
        boolean disablePatrols = configService.getBoolean(path + "disable-pillager-patrols", false);
        worlds.forEach(world -> world.setGameRule(GameRules.SPAWN_PATROLS,
                gameruleRestored(phase, lastMatch, disablePatrols)));
        if (configService.getBoolean(path + "set-daytime", false)) {
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
        boolean setNoneSpectator = configService.getBoolean(
                "settings.roles.turn-nones-spectator.enabled", false);
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
            lobbyTeleporter.setSpawnToLobby(nonePlayers, lobbyId);
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
        for (String name : enabledModifiers()) {
            if (runsOnContains(name, "ON_START") && !afterPrestart(name)) {
                runModifierCommands(name, matchId);
            }
        }
    }

    private void runCommandList(List<String> commands, Player player, ModifierTagScope scope) {
        for (String command : commands) {
            if (command.isBlank()) {
                continue;
            }
            try {
                String playerName = player != null ? player.getName() : null;
                double x = player != null ? player.getLocation().getX() : 0.0;
                double y = player != null ? player.getLocation().getY() : 0.0;
                double z = player != null ? player.getLocation().getZ() : 0.0;
                for (String expanded : CommandPlaceholders.expandAllPlayers(command, scope)) {
                    String parsed = CommandPlaceholders.replace(expanded, playerName, x, y, z, scope);
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