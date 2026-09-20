package com.jruk8.jmanhunt.stats;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsManagerWinsTest {

    private StatsManager manager() {
        return new StatsManager(null, null, null);
    }

    private UUID join(StatsManager manager, long matchId, Role role) {
        UUID id = UUID.randomUUID();
        manager.getOrCreate(matchId, id).role = role;
        return id;
    }

    @Test
    void speedrunnerWinCreditsOnlySpeedrunners() {
        StatsManager manager = manager();
        UUID hunter = join(manager, 1L, Role.HUNTER);
        UUID first = join(manager, 1L, Role.SPEEDRUNNER);
        UUID second = join(manager, 1L, Role.SPEEDRUNNER);

        manager.completeMatch(1L, Role.SPEEDRUNNER);

        CareerStats hunterStats = manager.career(hunter);
        assertEquals(0, hunterStats.wins);
        assertEquals(0, hunterStats.hunterWins);
        assertEquals(1, hunterStats.hunterSessions);
        assertEquals(1, hunterStats.sessions);

        for (UUID id : new UUID[]{first, second}) {
            CareerStats stats = manager.career(id);
            assertEquals(1, stats.wins);
            assertEquals(1, stats.speedrunnerWins);
            assertEquals(0, stats.hunterWins);
            assertEquals(1, stats.speedrunnerSessions);
            assertEquals(1, stats.sessions);
        }
    }

    @Test
    void hunterWinCreditsOnlyHunters() {
        StatsManager manager = manager();
        UUID hunter = join(manager, 1L, Role.HUNTER);
        UUID speedrunner = join(manager, 1L, Role.SPEEDRUNNER);

        manager.completeMatch(1L, Role.HUNTER);

        CareerStats hunterStats = manager.career(hunter);
        assertEquals(1, hunterStats.wins);
        assertEquals(1, hunterStats.hunterWins);
        assertEquals(0, hunterStats.speedrunnerWins);

        CareerStats runnerStats = manager.career(speedrunner);
        assertEquals(0, runnerStats.wins);
        assertEquals(0, runnerStats.speedrunnerWins);
        assertEquals(1, runnerStats.speedrunnerSessions);
        assertEquals(1, runnerStats.sessions);
    }

    @Test
    void nonParticipantGetsNoWinOrSession() {
        StatsManager manager = manager();
        UUID spectator = join(manager, 1L, Role.NONE);

        manager.completeMatch(1L, Role.HUNTER);

        assertTrue(manager.career(spectator).isEmpty());
    }

    @Test
    void winsAccumulateAcrossMatchesAndMatchRoleSum() {
        StatsManager manager = manager();
        UUID hunter = UUID.randomUUID();
        UUID speedrunner = UUID.randomUUID();
        manager.getOrCreate(1L, hunter).role = Role.HUNTER;
        manager.getOrCreate(1L, speedrunner).role = Role.SPEEDRUNNER;
        manager.getOrCreate(2L, hunter).role = Role.HUNTER;
        manager.getOrCreate(2L, speedrunner).role = Role.SPEEDRUNNER;

        manager.completeMatch(1L, Role.HUNTER);
        manager.completeMatch(2L, Role.SPEEDRUNNER);

        CareerStats hunterStats = manager.career(hunter);
        assertEquals(1, hunterStats.wins);
        assertEquals(2, hunterStats.sessions);
        assertEquals(hunterStats.hunterWins + hunterStats.speedrunnerWins, hunterStats.wins);

        CareerStats runnerStats = manager.career(speedrunner);
        assertEquals(1, runnerStats.wins);
        assertEquals(2, runnerStats.sessions);
        assertEquals(runnerStats.hunterWins + runnerStats.speedrunnerWins, runnerStats.wins);
    }

    @Test
    void concurrentMatchesStayIsolated() {
        StatsManager manager = manager();
        UUID hunter = join(manager, 1L, Role.HUNTER);
        UUID speedrunner = join(manager, 2L, Role.SPEEDRUNNER);

        manager.completeMatch(1L, Role.HUNTER);

        assertEquals(1, manager.career(hunter).wins);
        assertTrue(manager.career(speedrunner).isEmpty());

        manager.completeMatch(2L, Role.SPEEDRUNNER);

        assertEquals(1, manager.career(speedrunner).wins);
        assertEquals(1, manager.career(speedrunner).sessions);
        assertEquals(1, manager.career(hunter).sessions);
    }
}
