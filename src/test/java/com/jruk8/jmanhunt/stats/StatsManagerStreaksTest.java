package com.jruk8.jmanhunt.stats;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsManagerStreaksTest {

    private UUID join(StatsManager manager, long matchId, Role role) {
        UUID id = UUID.randomUUID();
        manager.getOrCreate(matchId, id).role = role;
        return id;
    }

    @Test
    void winIncrementsStreakAndBest() {
        StatsManager manager = new StatsManager(null, null, null);
        UUID winner = join(manager, 1L, Role.HUNTER);

        manager.completeMatch(1L, Role.HUNTER);

        assertEquals(1, manager.career(winner).currentWinStreak);
        assertEquals(1, manager.career(winner).bestWinStreak);
    }

    @Test
    void lossResetsCurrentButKeepsBest() {
        StatsManager manager = new StatsManager(null, null, null);
        UUID player = join(manager, 1L, Role.HUNTER);
        manager.completeMatch(1L, Role.HUNTER);
        join(manager, 2L, Role.HUNTER);
        manager.getOrCreate(2L, player).role = Role.HUNTER;

        manager.completeMatch(2L, Role.SPEEDRUNNER);

        assertEquals(0, manager.career(player).currentWinStreak);
        assertEquals(1, manager.career(player).bestWinStreak);
    }

    @Test
    void streaksSpanMatches() {
        StatsManager manager = new StatsManager(null, null, null);
        UUID player = join(manager, 1L, Role.SPEEDRUNNER);
        manager.completeMatch(1L, Role.SPEEDRUNNER);
        manager.getOrCreate(2L, player).role = Role.SPEEDRUNNER;

        manager.completeMatch(2L, Role.SPEEDRUNNER);

        assertEquals(2, manager.career(player).currentWinStreak);
        assertEquals(2, manager.career(player).bestWinStreak);
    }

    @Test
    void nonParticipantsKeepStreaks() {
        StatsManager manager = new StatsManager(null, null, null);
        UUID watcher = join(manager, 1L, Role.NONE);

        manager.completeMatch(1L, Role.HUNTER);

        assertEquals(0, manager.career(watcher).currentWinStreak);
        assertEquals(0, manager.career(watcher).bestWinStreak);
    }

    @Test
    void lobbySessionsRecordAndCount() {
        StatsManager manager = new StatsManager(null, null, null);

        assertEquals(0, manager.lifetimeSessions(0));
        manager.recordLobbySession(0);
        manager.recordLobbySession(0);
        manager.recordLobbySession(1);

        assertEquals(2, manager.lifetimeSessions(0));
        assertEquals(1, manager.lifetimeSessions(1));
    }
}
