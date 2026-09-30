package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.match.lifecycle.TimeLimitService;
import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeLimitTest {

    @Test
    void winnerPrefersEarlierExpiry() {
        assertEquals(Role.SPEEDRUNNER, TimeLimitService.timeLimitWinner(50.0, 100.0));
        assertEquals(Role.HUNTER, TimeLimitService.timeLimitWinner(100.0, 50.0));
    }

    @Test
    void winnerTiesFavorSpeedrunners() {
        assertEquals(Role.SPEEDRUNNER, TimeLimitService.timeLimitWinner(100.0, 100.0));
    }

    @Test
    void limitMarkHitExactlyOnSpawnNeverAnnounces() {
        assertEquals(List.of(), TimeLimitService.dueThresholds(3_600L, 3_600L, Set.of()));
    }

    @Test
    void announcedMarksDoNotRepeat() {
        assertEquals(List.of(), TimeLimitService.dueThresholds(3_600L, 3_599L, Set.of(3_600L)));
    }

    @Test
    void nextMarkAnnouncesOnCrossing() {
        assertEquals(List.of(1_800L), TimeLimitService.dueThresholds(3_600L, 1_800L, Set.of(3_600L)));
    }

    @Test
    void marksAboveLimitNeverFire() {
        assertEquals(List.of(), TimeLimitService.dueThresholds(90L, 90L, Set.of()));
        assertEquals(List.of(60L), TimeLimitService.dueThresholds(90L, 60L, Set.of()));
    }

    @Test
    void finalSecondsCountDownOneByOne() {
        Set<Long> announced = new HashSet<>(Set.of(10L));
        assertEquals(List.of(5L), TimeLimitService.dueThresholds(10L, 5L, announced));
        announced.add(5L);
        assertEquals(List.of(4L), TimeLimitService.dueThresholds(10L, 4L, announced));
    }

    @Test
    void missedMarksCatchUpHighestFirst() {
        assertEquals(List.of(1_800L, 900L, 600L, 300L, 120L),
                TimeLimitService.dueThresholds(3_600L, 100L, Set.of()));
    }

    @Test
    void survivePicksLowestClock() {
        var outcome = TimeLimitService.resolveSurvive(3_600.0, 1_800.0, 28_800.0);
        assertEquals(Role.HUNTER, outcome.winner());
        assertEquals(1_800.0, outcome.limitSecs());
    }

    @Test
    void surviveCancelWinsWhenLowest() {
        var outcome = TimeLimitService.resolveSurvive(3_600.0, 1_800.0, 900.0);
        assertEquals(true, outcome.cancel());
        assertEquals(900.0, outcome.limitSecs());
    }

    @Test
    void surviveFullTieFavorsSpeedrunners() {
        var outcome = TimeLimitService.resolveSurvive(1_800.0, 1_800.0, 1_800.0);
        assertEquals(Role.SPEEDRUNNER, outcome.winner());
    }

    @Test
    void surviveRunnerCancelTieFavorsSpeedrunners() {
        var outcome = TimeLimitService.resolveSurvive(900.0, 1_800.0, 900.0);
        assertEquals(Role.SPEEDRUNNER, outcome.winner());
    }

    @Test
    void surviveHunterCancelTieCancels() {
        var outcome = TimeLimitService.resolveSurvive(1_800.0, 900.0, 900.0);
        assertEquals(true, outcome.cancel());
    }

    @Test
    void surviveSingleClockWins() {
        assertEquals(Role.HUNTER, TimeLimitService.resolveSurvive(null, 1_800.0, null).winner());
        assertEquals(true, TimeLimitService.resolveSurvive(null, null, 28_800.0).cancel());
    }

    @Test
    void surviveNoClockResolvesNull() {
        assertEquals(null, TimeLimitService.resolveSurvive(null, null, null));
    }
}
