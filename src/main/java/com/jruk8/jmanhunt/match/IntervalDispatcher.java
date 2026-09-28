package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.core.DebugLevel;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.command.TagMath;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * INTERVAL scheduling plus delayed dispatch for modifiers. Owns one
 * interval engine per live match; firing runs through the owning
 * manager's dispatch hook with the INTERVAL execution roster
 * (watchers, the dead, and fake spectators skipped).
 */
public final class IntervalDispatcher {
    private final JManhuntPlugin plugin;
    private final ConfigService configService;
    private final GameManager game;
    private final PlayerStateStore playerStates;
    private final DispatchHook dispatch;
    /** One interval engine per live match, keyed by match id. */
    private final Map<Long, IntervalEngine> intervalEngines = new HashMap<>();

    /** Fires one modifier activation; implemented by the owning manager. */
    public interface DispatchHook {
        void dispatch(String name, int index, List<Player> targets, long matchId,
                List<String> eventArgs);
    }

    public IntervalDispatcher(JManhuntPlugin plugin, ConfigService configService,
            GameManager game, PlayerStateStore playerStates, DispatchHook dispatch) {
        this.plugin = plugin;
        this.configService = configService;
        this.game = game;
        this.playerStates = playerStates;
        this.dispatch = dispatch;
    }

    private IntervalEngine engine(long matchId) {
        return intervalEngines.computeIfAbsent(matchId, ignored -> new IntervalEngine());
    }

    /** Names of all modifiers effectively enabled for one match's lobby. */
    List<String> enabledModifiers(long matchId) {
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
        scheduleIntervalModifiers(matchId);
    }

    /**
     * Schedules interval chains without cancelling first. Used with
     * {@link #cancelIntervalChains(long)} when pending delayed
     * dispatches must survive the restart.
     */
    void scheduleIntervalModifiers(long matchId) {
        for (String name : enabledModifiers(matchId)) {
            for (int index : configService.behaviorIndexes(name)) {
                if (!ModifierTriggers.runsOn(configService.runsOn(name, index), "INTERVAL")) {
                    continue;
                }
                double intervalSeconds = configService.intervalSeconds(name, index);
                if (intervalSeconds < 0) {
                    continue;
                }
                double deviation = ModifierTriggers.clampDeviation(
                        configService.intervalDeviation(name, index), intervalSeconds);
                ModifierTriggers.TriggerScope scope = ModifierTriggers.parseScope(
                        configService.intervalBehavior(name, index));
                if (deviation > 0.0 && scope == ModifierTriggers.TriggerScope.PER_EXECUTOR) {
                    startPerExecutorInterval(name, index, matchId);
                } else {
                    scheduleSharedFiring(name, index, matchId);
                }
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
     * Cancels one match's interval chains but keeps pending delayed
     * dispatches and the generation, so in-flight trigger output
     * survives a mid-match restart. Same-thread calls are atomic
     * with task execution, so explicit cancels leave no orphans.
     */
    void cancelIntervalChains(long matchId) {
        IntervalEngine engine = engine(matchId);
        for (BukkitTask task : engine.tasks) {
            task.cancel();
        }
        engine.tasks.clear();
        engine.executors.clear();
        engine.consoleChained.clear();
    }

    /** Drops delayed modifier commands that never fired, e.g. at match end. */
    public void cancelPendingDelayed(long matchId) {
        IntervalEngine engine = engine(matchId);
        for (BukkitTask task : engine.delayed) {
            task.cancel();
        }
        engine.delayed.clear();
    }

    /**
     * Schedules the next firing of a shared-timing interval modifier. Fixed
     * cadences stay on a plain repeating task; deviated ones re-roll the delay
     * every firing. Interval values are re-read each cycle so reloads apply
     * without a match restart.
     */
    private void scheduleSharedFiring(String name, int index, long matchId) {
        IntervalEngine engine = engine(matchId);
        long generation = engine.generation;
        double intervalSeconds = configService.intervalSeconds(name, index);
        if (intervalSeconds < 0) {
            return;
        }
        double deviation = ModifierTriggers.clampDeviation(
                configService.intervalDeviation(name, index), intervalSeconds);
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
                runIntervalCommands(name, index, matchId, intervalArg(intervalTicks));
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
            runIntervalCommands(name, index, matchId, intervalArg(delayTicks));
            scheduleSharedFiring(name, index, matchId);
        }, delayTicks));
        engine.tasks.add(ref.get());
    }

    /** Starts one interval chain per participating player plus a console chain. */
    private void startPerExecutorInterval(String name, int index, long matchId) {
        engine(matchId).consoleChained.add(new IntervalEngine.BehaviorChain(name, index));
        scheduleConsoleFiring(name, index, matchId);
        for (Player player : game.onlineParticipants(matchId)) {
            if (chainedPlayers(matchId, name, index).add(player.getUniqueId())) {
                schedulePlayerFiring(name, index, player.getUniqueId(), matchId);
            }
        }
    }

    /** Schedules the next firing of a per-executor console chain. */
    private void scheduleConsoleFiring(String name, int index, long matchId) {
        IntervalEngine engine = engine(matchId);
        long generation = engine.generation;
        IntervalEngine.BehaviorChain chain = new IntervalEngine.BehaviorChain(name, index);
        long delayTicks = currentJitteredDelay(name, index);
        if (delayTicks < 0) {
            engine.consoleChained.remove(chain);
            return;
        }
        AtomicReference<BukkitTask> ref = new AtomicReference<>();
        ref.set(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            engine.tasks.remove(ref.get());
            if (generation != engine.generation || !engine.consoleChained.contains(chain)
                    || !plugin.overrides().modifierEnabled(lobbyOf(matchId), name)) {
                engine.consoleChained.remove(chain);
                return;
            }
            runDelayed(name, index,
                    () -> dispatch.dispatch(name, index, List.of(), matchId, intervalArg(delayTicks)),
                    matchId);
            reconcilePlayerChains(name, index, matchId);
            scheduleConsoleFiring(name, index, matchId);
        }, delayTicks));
        engine.tasks.add(ref.get());
    }

    /** Schedules the next firing of one player's interval chain. */
    private void schedulePlayerFiring(String name, int index, UUID playerId, long matchId) {
        IntervalEngine engine = engine(matchId);
        long generation = engine.generation;
        long delayTicks = currentJitteredDelay(name, index);
        if (delayTicks < 0) {
            chainedPlayers(matchId, name, index).remove(playerId);
            return;
        }
        AtomicReference<BukkitTask> ref = new AtomicReference<>();
        ref.set(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            engine.tasks.remove(ref.get());
            if (generation != engine.generation
                    || !plugin.overrides().modifierEnabled(lobbyOf(matchId), name)) {
                chainedPlayers(matchId, name, index).remove(playerId);
                return;
            }
            Player target = Bukkit.getPlayer(playerId);
            Optional<String> skip = target == null
                    ? Optional.of("offline") : intervalSkip(target, matchId);
            if (skip.isEmpty()) {
                runDelayed(name, index,
                        () -> dispatch.dispatch(name, index, List.of(target), matchId,
                                intervalArg(delayTicks)),
                        matchId);
            } else {
                if (target != null) {
                    plugin.logger().debug(DebugLevel.INFO, "debug.interval-skip", Map.of("modifier", name,
                            "player", target.getName(), "why", skip.get()));
                }
                chainedPlayers(matchId, name, index).remove(playerId);
            }
            reconcilePlayerChains(name, index, matchId);
            if (skip.isEmpty() && chainedPlayers(matchId, name, index).contains(playerId)) {
                schedulePlayerFiring(name, index, playerId, matchId);
            }
        }, delayTicks));
        engine.tasks.add(ref.get());
    }

    /**
     * INTERVAL event args for one firing: the actual seconds waited
     * (base interval plus that execution's rolled deviation),
     * derived from the scheduled tick delay.
     */
    private static List<String> intervalArg(long delayTicks) {
        return List.of(TagMath.formatNumber(delayTicks / 20.0));
    }

    /** Reads the live interval config and rolls the next delay, or -1 when disabled. */
    private long currentJitteredDelay(String name, int index) {
        double intervalSeconds = configService.intervalSeconds(name, index);
        if (intervalSeconds < 0) {
            return -1;
        }
        double deviation = ModifierTriggers.clampDeviation(
                configService.intervalDeviation(name, index), intervalSeconds);
        return ModifierTriggers.jitteredIntervalTicks(intervalSeconds, deviation,
                ThreadLocalRandom.current().nextDouble());
    }

    private Set<UUID> chainedPlayers(long matchId, String name, int index) {
        return engine(matchId).executors.computeIfAbsent(
                new IntervalEngine.BehaviorChain(name, index), key -> new HashSet<>());
    }

    /** Starts chains for participants that joined after the modifier began. */
    private void reconcilePlayerChains(String name, int index, long matchId) {
        Set<UUID> active = chainedPlayers(matchId, name, index);
        for (Player player : game.onlineParticipants(matchId)) {
            if (active.add(player.getUniqueId())) {
                schedulePlayerFiring(name, index, player.getUniqueId(), matchId);
            }
        }
    }

    /**
     * Runs a modifier's trigger dispatch after its configured {@code delay}
     * in ticks. Positions and roles resolve when delayed commands fire, not
     * when they trigger. Cleanup commands never go through here.
     */
    public void runDelayed(String name, int index, Runnable dispatch, long matchId) {
        long delay = Math.max(0L, configService.delayTicks(name, index));
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

    /**
     * INTERVAL-only dispatch: the execution roster skips watchers,
     * the dead, and fake spectators (respawn waits and headstart
     * holds), with a debug line per skip. ON_START keeps the full
     * roster and never passes here.
     */
    private void runIntervalCommands(String name, int index, long matchId, List<String> eventArgs) {
        runDelayed(name, index,
                () -> dispatch.dispatch(name, index, intervalTargets(name, matchId), matchId,
                        eventArgs),
                matchId);
    }

    /** INTERVAL execution roster: eligible players only, skips logged. */
    private List<Player> intervalTargets(String name, long matchId) {
        List<Player> targets = new ArrayList<>();
        for (Player player : game.onlineParticipants(matchId)) {
            Optional<String> skip = intervalSkip(player, matchId);
            if (skip.isEmpty()) {
                targets.add(player);
            } else {
                plugin.logger().debug(DebugLevel.INFO, "debug.interval-skip", Map.of("modifier", name,
                        "player", player.getName(), "why", skip.get()));
            }
        }
        return targets;
    }

    /** Live-state INTERVAL gate for one player; empty when eligible. */
    private Optional<String> intervalSkip(Player player, long matchId) {
        return intervalSkipWhy(playerStates.role(player),
                game.isActiveInInstance(matchId, player.getUniqueId()),
                player.isDead(), plugin.fakeSpectators().isFakeSpectator(player));
    }

    /**
     * INTERVAL eligibility from plain states: a participant role,
     * still active, alive, and not fake-spectating. Returns the skip
     * reason, or empty when the player may execute. Pure for tests.
     */
    static Optional<String> intervalSkipWhy(Role role, boolean active, boolean dead,
            boolean fakeSpectator) {
        if (!role.isParticipant()) {
            return Optional.of("not a participant");
        }
        if (!active) {
            return Optional.of("not active");
        }
        if (dead) {
            return Optional.of("dead");
        }
        if (fakeSpectator) {
            return Optional.of("spectating");
        }
        return Optional.empty();
    }
}
