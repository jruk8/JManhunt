package com.jruk8.jmanhunt.world.end;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndCellManagerTest {

    @Test
    void poolNameAppendsNumberWithHardcodedUnderscore() {
        assertEquals("jmh_end_1", EndCellManager.poolName("jmh_end", 1L));
        assertEquals("custom_12", EndCellManager.poolName("custom", 12L));
    }

    @Test
    void nextNStartsAtOneWhenPoolIsEmpty() {
        assertEquals(1L, EndCellManager.nextN(Set.of()));
    }

    @Test
    void nextNIsHighestPlusOne() {
        assertEquals(5L, EndCellManager.nextN(Set.of(1L, 2L, 4L)));
    }

    @Test
    void lowestFreeSkipsAssigned() {
        assertEquals(OptionalLong.of(2L),
                EndCellManager.lowestFree(Set.of(1L, 2L, 4L), Set.of(1L)));
    }

    @Test
    void lowestFreeEmptyWhenAllAssigned() {
        assertEquals(OptionalLong.empty(),
                EndCellManager.lowestFree(Set.of(1L, 2L), Set.of(1L, 2L)));
    }

    @Test
    void needsTopUpBelowBufferOnly() {
        assertTrue(EndCellManager.needsTopUp(2, 3));
        assertFalse(EndCellManager.needsTopUp(3, 3));
        assertFalse(EndCellManager.needsTopUp(4, 3));
    }

    @Test
    void overflowCeilingIsFiveTimesBuffer() {
        assertEquals(5, EndCellManager.OVERFLOW_MULTIPLIER);
        assertFalse(EndCellManager.exceedsOverflow(15, 3));
        assertTrue(EndCellManager.exceedsOverflow(16, 3));
    }

    @Test
    void overflowVictimIsHighestFree() {
        assertEquals(OptionalLong.of(9L), EndCellManager.overflowVictim(Set.of(2L, 9L, 4L)));
        assertEquals(OptionalLong.empty(), EndCellManager.overflowVictim(Set.of()));
    }

    @Test
    void parsePoolNumberAcceptsPositiveIntegersOnly() {
        assertEquals(OptionalLong.of(3L), EndCellManager.parsePoolNumber("jmh_end_3", "jmh_end"));
        assertEquals(OptionalLong.empty(), EndCellManager.parsePoolNumber("jmh_end_old", "jmh_end"));
        assertEquals(OptionalLong.empty(), EndCellManager.parsePoolNumber("jmh_end_0", "jmh_end"));
        assertEquals(OptionalLong.empty(), EndCellManager.parsePoolNumber("jmh_end", "jmh_end"));
        assertEquals(OptionalLong.empty(), EndCellManager.parsePoolNumber("other_end_1", "jmh_end"));
    }

    @Test
    void strayEndWorldsMatchLegacyAndNonNumericPoolFolders() {
        List<String> dirs = List.of(
                "world_the_end_9", "other", "world_the_end_old", "world",
                "jmh_end_old", "jmh_end_2", "jmh_end", "world_the_end");

        assertEquals(
                List.of("jmh_end_old", "world_the_end_9", "world_the_end_old"),
                EndCellManager.strayEndWorlds(dirs, "world_the_end_", "jmh_end"));
    }
}
