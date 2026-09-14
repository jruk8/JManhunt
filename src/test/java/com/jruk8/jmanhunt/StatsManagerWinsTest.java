package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsManagerWinsTest {

    private StatsManager manager() {
        return new StatsManager(null, null, null);
    }

    private UUID join(StatsManager manager, Role role) {
        UUID id = UUID.randomUUID();
        manager.getOrCreate(id).role = role;
        return id;
    }

    @Test
    void speedrunnerWinCreditsOnlySpeedrunners() {
        StatsManager manager = manager();
        UUID hunter = join(manager, Role.HUNTER);
        UUID first = join(manager, Role.SPEEDRUNNER);
        UUID second = join(manager, Role.SPEEDRUNNER);

        manager.completeMatch(Role.SPEEDRUNNER);

        StatsManager.CareerStats hunterStats = manager.career(hunter);
        assertEquals(0, hunterStats.wins);
        assertEquals(0, hunterStats.hunterWins);
        assertEquals(1, hunterStats.hunterSessions);
        assertEquals(1, hunterStats.sessions);

        for (UUID id : new UUID[]{first, second}) {
            StatsManager.CareerStats stats = manager.career(id);
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
        UUID hunter = join(manager, Role.HUNTER);
        UUID speedrunner = join(manager, Role.SPEEDRUNNER);

        manager.completeMatch(Role.HUNTER);

        StatsManager.CareerStats hunterStats = manager.career(hunter);
        assertEquals(1, hunterStats.wins);
        assertEquals(1, hunterStats.hunterWins);
        assertEquals(0, hunterStats.speedrunnerWins);

        StatsManager.CareerStats runnerStats = manager.career(speedrunner);
        assertEquals(0, runnerStats.wins);
        assertEquals(0, runnerStats.speedrunnerWins);
        assertEquals(1, runnerStats.speedrunnerSessions);
        assertEquals(1, runnerStats.sessions);
    }

    @Test
    void nonParticipantGetsNoWinOrSession() {
        StatsManager manager = manager();
        UUID spectator = join(manager, Role.NONE);

        manager.completeMatch(Role.HUNTER);

        assertTrue(manager.career(spectator).isEmpty());
    }

    @Test
    void winsAccumulateAcrossMatchesAndMatchRoleSum() {
        StatsManager manager = manager();
        UUID hunter = join(manager, Role.HUNTER);
        UUID speedrunner = join(manager, Role.SPEEDRUNNER);

        manager.completeMatch(Role.HUNTER);
        manager.completeMatch(Role.SPEEDRUNNER);

        StatsManager.CareerStats hunterStats = manager.career(hunter);
        assertEquals(1, hunterStats.wins);
        assertEquals(hunterStats.hunterWins + hunterStats.speedrunnerWins, hunterStats.wins);

        StatsManager.CareerStats runnerStats = manager.career(speedrunner);
        assertEquals(1, runnerStats.wins);
        assertEquals(runnerStats.hunterWins + runnerStats.speedrunnerWins, runnerStats.wins);
    }
}
