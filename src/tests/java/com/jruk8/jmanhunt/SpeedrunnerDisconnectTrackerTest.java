package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedrunnerDisconnectTrackerTest {
    @Test
    void forfeitsOnConfiguredStrikeLimit() {
        SpeedrunnerDisconnectTracker tracker = new SpeedrunnerDisconnectTracker();
        UUID playerId = UUID.randomUUID();

        assertFalse(tracker.registerDisconnect(playerId, 1L, 3).forfeit());
        assertFalse(tracker.registerDisconnect(playerId, 1L, 3).forfeit());
        assertTrue(tracker.registerDisconnect(playerId, 1L, 3).forfeit());
    }

    @Test
    void normalizesNonPositiveStrikeLimitToOne() {
        SpeedrunnerDisconnectTracker tracker = new SpeedrunnerDisconnectTracker();
        UUID playerId = UUID.randomUUID();

        assertTrue(tracker.registerDisconnect(playerId, 1L, 0).forfeit());
    }

    @Test
    void clearsPlayerStrikesIndependently() {
        SpeedrunnerDisconnectTracker tracker = new SpeedrunnerDisconnectTracker();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        tracker.registerDisconnect(first, 1L, 2);
        tracker.registerDisconnect(second, 1L, 2);
        tracker.clear(first);

        assertFalse(tracker.registerDisconnect(first, 1L, 2).forfeit());
        assertTrue(tracker.registerDisconnect(second, 1L, 2).forfeit());
    }

    @Test
    void resetsStrikesWhenMatchIdChanges() {
        SpeedrunnerDisconnectTracker tracker = new SpeedrunnerDisconnectTracker();
        UUID playerId = UUID.randomUUID();

        assertFalse(tracker.registerDisconnect(playerId, 1L, 2).forfeit());
        assertFalse(tracker.registerDisconnect(playerId, 2L, 2).forfeit());
    }
}
