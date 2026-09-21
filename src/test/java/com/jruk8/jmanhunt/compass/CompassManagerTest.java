package com.jruk8.jmanhunt.compass;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompassManagerTest {

    @Test
    void shouldRefreshFiresExactlyAtCooldown() {
        assertTrue(CompassManager.shouldRefresh(10_000L, 0L, 10_000L));
        assertFalse(CompassManager.shouldRefresh(9_999L, 0L, 10_000L));
        assertTrue(CompassManager.shouldRefresh(5_000L, 0L, 3_000L));
    }

    @Test
    void flatDistanceIgnoresHeight() {
        Location from = new Location(null, 0.0, 64.0, 0.0);
        Location to = new Location(null, 3.0, 200.0, 4.0);
        assertEquals(5.0, CompassManager.flatDistance(from, to), 1e-9);
    }
}
