package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.DurationFormat;
import com.jruk8.jmanhunt.player.Role;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Runs the time-limit win countdown: one per-second task per match that
 * announces each threshold once and finishes the match at zero.
 */
final class TimeLimitService {
    /**
     * Countdown marks for a time-limit win, in whole seconds remaining:
     * 8h, 6h, 4h, 2h, 1h, 30m, 15m, 10m, 5m, 2m, 1m, 30s, 15s, 10s, 5-1s.
     */
    static final List<Long> TIME_ANNOUNCE_SECONDS = List.of(
            28_800L, 21_600L, 14_400L, 7_200L, 3_600L, 1_800L, 900L, 600L,
            300L, 120L, 60L, 30L, 15L, 10L, 5L, 4L, 3L, 2L, 1L);

    private final JManhuntPlugin plugin;
    private final WinConditionEngine winConditionEngine;
    private final MatchStore store;
    private final MatchMessaging messaging;
    private final MatchControl control;

    TimeLimitService(JManhuntPlugin plugin, WinConditionEngine winConditionEngine,
            MatchStore store, MatchMessaging messaging, MatchControl control) {
        this.plugin = plugin;
        this.winConditionEngine = winConditionEngine;
        this.store = store;
        this.messaging = messaging;
        this.control = control;
    }

    /**
     * Starts the time-limit win countdown for a match. With both sides'
     * limits set, the earlier expiry wins (ties favor the speedrunners)
     * and a console warning explains the pick. A single per-second task
     * announces each threshold once and ends the match at zero.
     */
    void scheduleTimeLimit(GameInstance instance, long currentMatchId) {
        boolean runnerClock = winConditionEngine.enabled(Role.SPEEDRUNNER, WinCondition.SURVIVE_TIME);
        boolean hunterClock = winConditionEngine.enabled(Role.HUNTER, WinCondition.TIME_LIMIT);
        if (!runnerClock && !hunterClock) {
            return;
        }
        Optional<TimeLimit> limit = resolveTimeLimit(runnerClock, hunterClock);
        if (limit.isEmpty()) {
            return;
        }
        Role winner = limit.get().winner();
        double limitSecs = limit.get().limitSecs();
        long limitSecsWhole = Math.round(limitSecs);
        long limitMillis = Math.round(limitSecs * 1000.0);
        String winnerName = winner.displayName() + "s";
        Role finalWinner = winner;
        instance.setTimeLimitTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (store.instance(currentMatchId).orElse(null) != instance
                    || !instance.active() || instance.ending()) {
                cancelTimeLimit(instance);
                return;
            }
            long elapsedMillis = System.currentTimeMillis() - instance.startedAtMillis();
            long remainingSecs = Math.max(0L, (limitMillis - elapsedMillis) / 1000L);
            if (remainingSecs > 0L) {
                for (long mark : dueThresholds(limitSecsWhole, remainingSecs, instance.timeAnnounced())) {
                    instance.timeAnnounced().add(mark);
                    messaging.sendToInstance(instance, "game.time-left",
                            Map.of("winner", winnerName, "time", DurationFormat.format(mark)));
                }
            } else {
                cancelTimeLimit(instance);
                control.finish(instance, finalWinner);
            }
        }, 20L, 20L));
    }

    /** Resolved time-limit clock: the winner on expiry and the limit in seconds. */
    private record TimeLimit(Role winner, double limitSecs) {
    }

    /** Resolves which clock wins, warning when both sides configure one. Empty when disabled. */
    private Optional<TimeLimit> resolveTimeLimit(boolean runnerClock, boolean hunterClock) {
        double runnerSecs = winConditionEngine.time(Role.SPEEDRUNNER);
        double hunterSecs = winConditionEngine.time(Role.HUNTER);
        double limitSecs;
        Role winner;
        if (runnerClock && hunterClock) {
            if (runnerSecs == hunterSecs) {
                plugin.logger().warning("Both time-limit win conditions are " + runnerSecs
                        + "s; favoring the speedrunners.");
            } else {
                plugin.logger().warning("Both time-limit win conditions are set (speedrunners "
                        + runnerSecs + "s, hunters " + hunterSecs + "s); the earlier expiry wins.");
            }
            winner = timeLimitWinner(runnerSecs, hunterSecs);
            limitSecs = Math.min(runnerSecs, hunterSecs);
        } else if (runnerClock) {
            winner = Role.SPEEDRUNNER;
            limitSecs = runnerSecs;
        } else {
            winner = Role.HUNTER;
            limitSecs = hunterSecs;
        }
        if (limitSecs <= 0.0) {
            return Optional.empty();
        }
        return Optional.of(new TimeLimit(winner, limitSecs));
    }

    /** Stops a match's time-limit countdown, if any. */
    void cancelTimeLimit(GameInstance instance) {
        if (instance.timeLimitTask() != null) {
            instance.timeLimitTask().cancel();
            instance.setTimeLimitTask(null);
        }
    }

    /**
     * Thresholds to announce now: marks strictly below the limit that the
     * remaining time has reached and that are still unannounced, highest
     * first. The limit mark itself is hit exactly on spawn and never
     * announces. Pure for tests.
     */
    static List<Long> dueThresholds(long limitSecs, long remainingSecs, Set<Long> announced) {
        List<Long> due = new ArrayList<>();
        for (long mark : TIME_ANNOUNCE_SECONDS) {
            if (mark < limitSecs && mark >= remainingSecs && !announced.contains(mark)) {
                due.add(mark);
            }
        }
        return due;
    }

    /** Winner when both time limits run: earlier expiry wins, ties favor runners. Pure for tests. */
    static Role timeLimitWinner(double runnerSecs, double hunterSecs) {
        return runnerSecs <= hunterSecs ? Role.SPEEDRUNNER : Role.HUNTER;
    }
}
