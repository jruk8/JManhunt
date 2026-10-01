package com.jruk8.jmanhunt.stats;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Match-slice mob-kill and advancement counters behind the stat tags. */
class StatsManagerCountersTest {

    private StatsManager manager() {
        return new StatsManager(null, null, null, null, null);
    }

    @Test
    void mobKillsAndAdvancementsCountPerMatch() {
        StatsManager manager = manager();
        UUID id = UUID.randomUUID();

        manager.recordMobKill(7L, id, Role.HUNTER);
        manager.recordMobKill(7L, id, Role.HUNTER);
        manager.recordAdvancement(7L, id, Role.HUNTER);

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
        manager.recordMobKill(7L, id, Role.HUNTER);

        manager.clearMatch(7L);

        assertTrueMissing(manager, 7L, id);
    }

    @Test
    void spectatorsRecordNothing() {
        StatsManager manager = manager();
        UUID id = UUID.randomUUID();

        manager.recordMobKill(7L, id, Role.SPECTATOR);
        manager.recordAdvancement(7L, id, Role.NONE);

        assertTrueMissing(manager, 7L, id);
    }

    @Test
    void recordersStampFirstSeenRole() {
        StatsManager manager = manager();
        UUID id = UUID.randomUUID();

        manager.recordMobKill(7L, id, Role.HUNTER);

        assertEquals(Role.HUNTER, manager.matchStats(7L, id).orElseThrow().role);
    }

    private static void assertTrueMissing(StatsManager manager, long matchId, UUID id) {
        assertTrue(manager.matchStats(matchId, id).isEmpty());
    }
}
