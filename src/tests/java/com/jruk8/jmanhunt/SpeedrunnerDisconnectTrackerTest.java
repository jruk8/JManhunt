package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedrunnerDisconnectTrackerTest {

    @Test
    void firstDisconnectReturnsOneStrikeAndNoForfeit() {
        SpeedrunnerDisconnectTracker tracker = new SpeedrunnerDisconnectTracker();
        UUID playerId = UUID.randomUUID();

        SpeedrunnerDisconnectTracker.Decision decision = tracker.registerDisconnect(playerId, 1L, 3);

        assertEquals(1, decision.strikes());
        assertFalse(decision.forfeit());
    }

    @Test
    void strikesAccumulateAcrossMultipleDisconnectsInSameMatch() {
        SpeedrunnerDisconnectTracker tracker = new SpeedrunnerDisconnectTracker();
        UUID playerId = UUID.randomUUID();

        tracker.registerDisconnect(playerId, 1L, 3);
        // Rejoin does NOT clear strikes, so the second disconnect should be strike 2
        SpeedrunnerDisconnectTracker.Decision second = tracker.registerDisconnect(playerId, 1L, 3);

        assertEquals(2, second.strikes());
        assertFalse(second.forfeit());
    }

    @Test
    void forfeitsWhenStrikesReachMaxLimit() {
        SpeedrunnerDisconnectTracker tracker = new SpeedrunnerDisconnectTracker();
        UUID playerId = UUID.randomUUID();

        tracker.registerDisconnect(playerId, 1L, 3);
        tracker.registerDisconnect(playerId, 1L, 3);
        SpeedrunnerDisconnectTracker.Decision third = tracker.registerDisconnect(playerId, 1L, 3);

        assertEquals(3, third.strikes());
        assertTrue(third.forfeit());
    }

    @Test
    void clearResetsStrikesForNextDisconnect() {
        SpeedrunnerDisconnectTracker tracker = new SpeedrunnerDisconnectTracker();
        UUID playerId = UUID.randomUUID();

        tracker.registerDisconnect(playerId, 1L, 3);
        tracker.clear(playerId);
        SpeedrunnerDisconnectTracker.Decision afterClear = tracker.registerDisconnect(playerId, 1L, 3);

        assertEquals(1, afterClear.strikes());
        assertFalse(afterClear.forfeit());
    }

    @Test
    void strikesResetWhenMatchIdChanges() {
        SpeedrunnerDisconnectTracker tracker = new SpeedrunnerDisconnectTracker();
        UUID playerId = UUID.randomUUID();

        tracker.registerDisconnect(playerId, 1L, 3);
        tracker.registerDisconnect(playerId, 1L, 3);
        // New match → strikes should reset
        SpeedrunnerDisconnectTracker.Decision newMatch = tracker.registerDisconnect(playerId, 2L, 3);

        assertEquals(1, newMatch.strikes());
        assertFalse(newMatch.forfeit());
    }

    @Test
    void maxStrikesOfOneCausesImmediateForfeit() {
        SpeedrunnerDisconnectTracker tracker = new SpeedrunnerDisconnectTracker();
        UUID playerId = UUID.randomUUID();

        SpeedrunnerDisconnectTracker.Decision decision = tracker.registerDisconnect(playerId, 1L, 1);

        assertEquals(1, decision.strikes());
        assertTrue(decision.forfeit());
    }

    @Test
    void zeroOrNegativeMaxStrikesIsNormalizedToOne() {
        SpeedrunnerDisconnectTracker tracker = new SpeedrunnerDisconnectTracker();
        UUID playerId = UUID.randomUUID();

        SpeedrunnerDisconnectTracker.Decision decision = tracker.registerDisconnect(playerId, 1L, 0);

        assertEquals(1, decision.strikes());
        assertTrue(decision.forfeit());
    }

    @Test
    void differentPlayersHaveIndependentStrikeCounts() {
        SpeedrunnerDisconnectTracker tracker = new SpeedrunnerDisconnectTracker();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        tracker.registerDisconnect(player1, 1L, 5);
        tracker.registerDisconnect(player1, 1L, 5);

        SpeedrunnerDisconnectTracker.Decision player2First = tracker.registerDisconnect(player2, 1L, 5);

        assertEquals(3, tracker.registerDisconnect(player1, 1L, 5).strikes());
        assertEquals(1, player2First.strikes());
    }
}