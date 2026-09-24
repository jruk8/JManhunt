package com.jruk8.jmanhunt.stats;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opposite-role kill attribution for match slices and career folds. */
class StatsManagerKillsTest {

    private StatsManager manager() {
        return new StatsManager(null, null, null);
    }

    private UUID join(StatsManager manager, long matchId, Role role) {
        UUID id = UUID.randomUUID();
        manager.getOrCreate(matchId, id).role = role;
        return id;
    }

    @Test
    void hunterKillingSpeedrunnerCountsSplitAndTotal() {
        StatsManager manager = manager();
        UUID hunter = join(manager, 1L, Role.HUNTER);

        manager.recordPlayerKill(1L, hunter, Role.HUNTER, Role.SPEEDRUNNER);

        Stats slice = manager.matchStats(1L, hunter).orElseThrow();
        assertEquals(1, slice.kills);
        assertEquals(1, slice.hunterKills);
        assertEquals(0, slice.speedrunnerKills);

        manager.completeMatch(1L, Role.HUNTER);

        CareerStats career = manager.career(hunter);
        assertEquals(1, career.kills);
        assertEquals(1, career.hunterKills);
        assertEquals(0, career.speedrunnerKills);
    }

    @Test
    void speedrunnerKillingHunterCountsSpeedrunnerSplit() {
        StatsManager manager = manager();
        UUID runner = join(manager, 1L, Role.SPEEDRUNNER);

        manager.recordPlayerKill(1L, runner, Role.SPEEDRUNNER, Role.HUNTER);

        manager.completeMatch(1L, Role.SPEEDRUNNER);

        CareerStats career = manager.career(runner);
        assertEquals(1, career.kills);
        assertEquals(1, career.speedrunnerKills);
        assertEquals(0, career.hunterKills);
    }

    @Test
    void sameRoleKillCountsTotalOnly() {
        StatsManager manager = manager();
        UUID hunter = join(manager, 1L, Role.HUNTER);

        manager.recordPlayerKill(1L, hunter, Role.HUNTER, Role.HUNTER);

        manager.completeMatch(1L, Role.SPEEDRUNNER);

        CareerStats career = manager.career(hunter);
        assertEquals(1, career.kills);
        assertEquals(0, career.hunterKills);
        assertEquals(0, career.speedrunnerKills);
    }

    @Test
    void nonParticipantKillerRecordsNothing() {
        StatsManager manager = manager();
        UUID spectator = UUID.randomUUID();

        manager.recordPlayerKill(1L, spectator, Role.SPECTATOR, Role.HUNTER);

        assertTrue(manager.matchStats(1L, spectator).isEmpty());
    }
}
