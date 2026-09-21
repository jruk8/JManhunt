package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeLimitTest {

    @Test
    void winnerPrefersEarlierExpiry() {
        assertEquals(Role.SPEEDRUNNER, GameManager.timeLimitWinner(50.0, 100.0));
        assertEquals(Role.HUNTER, GameManager.timeLimitWinner(100.0, 50.0));
    }

    @Test
    void winnerTiesFavorSpeedrunners() {
        assertEquals(Role.SPEEDRUNNER, GameManager.timeLimitWinner(100.0, 100.0));
    }

    @Test
    void limitMarkHitExactlyOnSpawnNeverAnnounces() {
        assertEquals(List.of(), GameManager.dueThresholds(3_600L, 3_600L, Set.of()));
    }

    @Test
    void announcedMarksDoNotRepeat() {
        assertEquals(List.of(), GameManager.dueThresholds(3_600L, 3_599L, Set.of(3_600L)));
    }

    @Test
    void nextMarkAnnouncesOnCrossing() {
        assertEquals(List.of(1_800L), GameManager.dueThresholds(3_600L, 1_800L, Set.of(3_600L)));
    }

    @Test
    void marksAboveLimitNeverFire() {
        assertEquals(List.of(), GameManager.dueThresholds(90L, 90L, Set.of()));
        assertEquals(List.of(60L), GameManager.dueThresholds(90L, 60L, Set.of()));
    }

    @Test
    void finalSecondsCountDownOneByOne() {
        Set<Long> announced = new HashSet<>(Set.of(10L));
        assertEquals(List.of(5L), GameManager.dueThresholds(10L, 5L, announced));
        announced.add(5L);
        assertEquals(List.of(4L), GameManager.dueThresholds(10L, 4L, announced));
    }

    @Test
    void missedMarksCatchUpHighestFirst() {
        assertEquals(List.of(1_800L, 900L, 600L, 300L, 120L),
                GameManager.dueThresholds(3_600L, 100L, Set.of()));
    }
}
