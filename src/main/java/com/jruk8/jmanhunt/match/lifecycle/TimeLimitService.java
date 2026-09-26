package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.DurationFormat;
import com.jruk8.jmanhunt.player.Role;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.WinCondition;
import com.jruk8.jmanhunt.match.WinConditionEngine;

/**
 * Runs the time-limit win countdown: one per-second task per match that
 * announces each threshold once and finishes the match at zero.
 */
public final class TimeLimitService {
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

    public TimeLimitService(JManhuntPlugin plugin, WinConditionEngine winConditionEngine,
            MatchStore store, MatchMessaging messaging, MatchControl control) {
        this.plugin = plugin;
        this.winConditionEngine = winConditionEngine;
        this.store = store;
        this.messaging = messaging;
        this.control = control;
    }

    /**
     * Starts the survive-clock countdown for a match. The lowest enabled
     * time across the speedrunner, hunter, and cancel clocks wins; full
     * ties favor the speedrunners, and a console warning explains the pick
     * when several clocks are set. A single per-second task announces each
     * threshold once and ends (or cancels) the match at zero.
     */
    public void scheduleTimeLimit(GameInstance instance, long currentMatchId) {
        Integer lobby = instance.originLobbyId();
        Double runnerSecs = winConditionEngine.enabled(lobby, Role.SPEEDRUNNER, WinCondition.SURVIVE_TIME)
                ? winConditionEngine.time(lobby, Role.SPEEDRUNNER) : null;
        Double hunterSecs = winConditionEngine.enabled(lobby, Role.HUNTER, WinCondition.TIME_LIMIT)
                ? winConditionEngine.time(lobby, Role.HUNTER) : null;
        Double cancelSecs = winConditionEngine.cancelSurviveEnabled(lobby)
                ? winConditionEngine.cancelSurviveTime(lobby) : null;
        SurviveOutcome limit = resolveSurvive(runnerSecs, hunterSecs, cancelSecs);
        if (limit == null || limit.limitSecs() <= 0.0) {
            return;
        }
        if (clocksSet(runnerSecs, hunterSecs, cancelSecs) > 1) {
            plugin.logger().warning("Several survive clocks are set (speedrunners "
                    + describeClock(runnerSecs) + ", hunters " + describeClock(hunterSecs)
                    + ", cancel " + describeClock(cancelSecs) + "); the earliest expiry wins: "
                    + describeOutcome(limit) + ".");
        }
        double limitSecs = limit.limitSecs();
        long limitSecsWhole = Math.round(limitSecs);
        long limitMillis = Math.round(limitSecs * 1000.0);
        String winnerName = limit.winner() == null ? null : limit.winner().displayName() + "s";
        instance.setTimeLimitTask(Bukkit.getScheduler().runTaskTimer(plugin,
                () -> tickCountdown(instance, currentMatchId, limit, winnerName, limitSecsWhole, limitMillis),
                20L, 20L));
    }

    /** One countdown tick: announce due thresholds, or end/cancel at zero. */
    private void tickCountdown(GameInstance instance, long currentMatchId, SurviveOutcome limit,
            String winnerName, long limitSecsWhole, long limitMillis) {
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
                if (limit.cancel()) {
                    messaging.sendToInstance(instance, "game.cancel-in",
                            Map.of("time", DurationFormat.format(mark)));
                } else {
                    messaging.sendToInstance(instance, "game.time-left",
                            Map.of("winner", winnerName, "time", DurationFormat.format(mark)));
                }
            }
        } else if (limit.cancel()) {
            cancelTimeLimit(instance);
            control.cancel(instance);
        } else {
            cancelTimeLimit(instance);
            control.finish(instance, limit.winner());
        }
    }

    /**
     * Resolved survive clock: whether the match cancels on expiry, the
     * winning role (null when cancelling), and the limit in seconds.
     */
    public record SurviveOutcome(boolean cancel, Role winner, double limitSecs) {
    }

    /**
     * Picks the winning survive clock from the enabled times (null when
     * disabled). Lowest time wins; ties resolve to the speedrunners first,
     * then the cancel clock, then the hunters. Null when no clock runs.
     * Pure for tests.
     */
    public static SurviveOutcome resolveSurvive(Double runnerSecs, Double hunterSecs, Double cancelSecs) {
        double lowest = Double.MAX_VALUE;
        if (runnerSecs != null) {
            lowest = Math.min(lowest, runnerSecs);
        }
        if (hunterSecs != null) {
            lowest = Math.min(lowest, hunterSecs);
        }
        if (cancelSecs != null) {
            lowest = Math.min(lowest, cancelSecs);
        }
        if (lowest == Double.MAX_VALUE) {
            return null;
        }
        if (runnerSecs != null && runnerSecs == lowest) {
            return new SurviveOutcome(false, Role.SPEEDRUNNER, lowest);
        }
        if (cancelSecs != null && cancelSecs == lowest) {
            return new SurviveOutcome(true, null, lowest);
        }
        return new SurviveOutcome(false, Role.HUNTER, lowest);
    }

    private static int clocksSet(Double runnerSecs, Double hunterSecs, Double cancelSecs) {
        int set = 0;
        if (runnerSecs != null) {
            set++;
        }
        if (hunterSecs != null) {
            set++;
        }
        if (cancelSecs != null) {
            set++;
        }
        return set;
    }

    private static String describeClock(Double secs) {
        return secs == null ? "off" : secs + "s";
    }

    private static String describeOutcome(SurviveOutcome limit) {
        if (limit.cancel()) {
            return "cancel at " + limit.limitSecs() + "s";
        }
        return limit.winner().displayName() + "s at " + limit.limitSecs() + "s";
    }

    /** Stops a match's time-limit countdown, if any. */
    public void cancelTimeLimit(GameInstance instance) {
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
    public static List<Long> dueThresholds(long limitSecs, long remainingSecs, Set<Long> announced) {
        List<Long> due = new ArrayList<>();
        for (long mark : TIME_ANNOUNCE_SECONDS) {
            if (mark < limitSecs && mark >= remainingSecs && !announced.contains(mark)) {
                due.add(mark);
            }
        }
        return due;
    }

    /** Winner when both time limits run: earlier expiry wins, ties favor runners. Pure for tests. */
    public static Role timeLimitWinner(double runnerSecs, double hunterSecs) {
        return runnerSecs <= hunterSecs ? Role.SPEEDRUNNER : Role.HUNTER;
    }
}
