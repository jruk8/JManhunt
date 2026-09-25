package com.jruk8.jmanhunt.stats;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Match-slice mob-kill and advancement counters behind the stat tags. */
class StatsManagerCountersTest {

    private StatsManager manager() {
        return new StatsManager(null, null, null);
    }

    @Test
    void mobKillsAndAdvancementsCountPerMatch() {
        StatsManager manager = manager();
        UUID id = UUID.randomUUID();

        manager.recordMobKill(7L, id);
        manager.recordMobKill(7L, id);
        manager.recordAdvancement(7L, id);

        Stats slice = manager.matchStats(7L, id).orElseThrow();
        assertEquals(2, slice.mobsKilled);
        assertEquals(1, slice.achievementsGained);
        assertEquals(0, slice.kills);
        assertTrueMissing(manager, 8L, id);
    }

    @Test
    void clearMatchDropsCounters() {
        StatsManager manager = manager();
        UUID id = UUID.randomUUID();
        manager.recordMobKill(7L, id);

        manager.clearMatch(7L);

        assertTrueMissing(manager, 7L, id);
    }

    private static void assertTrueMissing(StatsManager manager, long matchId, UUID id) {
        assertTrue(manager.matchStats(matchId, id).isEmpty());
    }
}
