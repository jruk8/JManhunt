package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.player.Role;
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
    void zeroScrollCooldownNeverThrottles() {
        assertTrue(CompassManager.shouldRefresh(10_000L, 10_000L, 0L));
    }

    @Test
    void flatDistanceIgnoresHeight() {
        Location from = new Location(null, 0.0, 64.0, 0.0);
        Location to = new Location(null, 3.0, 200.0, 4.0);
        assertEquals(5.0, CompassManager.flatDistance(from, to), 1e-9);
    }

    @Test
    void coverSkipsTransparentBlocksOnlyWhenIgnored() {
        assertFalse(CompassManager.countsAsCover(false, false, true));
        assertFalse(CompassManager.countsAsCover(false, true, false));
        assertFalse(CompassManager.countsAsCover(true, false, true));
        assertTrue(CompassManager.countsAsCover(true, false, false));
        assertTrue(CompassManager.countsAsCover(true, true, true));
        assertTrue(CompassManager.countsAsCover(true, true, false));
    }

    @Test
    void rayClearSeesOpenRaysAndBlockedOnes() {
        assertTrue(CompassManager.rayClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 300.0,
                (x, y, z) -> false));
        assertFalse(CompassManager.rayClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 300.0,
                (x, y, z) -> x == 5 && y == 64 && z == 0));
    }

    @Test
    void rayClearFailsPastMaxDistance() {
        assertFalse(CompassManager.rayClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 5.0,
                (x, y, z) -> false));
        assertTrue(CompassManager.rayClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 10.0,
                (x, y, z) -> false));
    }

    @Test
    void rayClearSkipsStartBlockButCountsEndBlock() {
        assertTrue(CompassManager.rayClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 300.0,
                (x, y, z) -> x == 0 && y == 64 && z == 0));
        assertFalse(CompassManager.rayClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 300.0,
                (x, y, z) -> x == 10 && y == 64 && z == 0));
    }

    @Test
    void compassKeysFollowRoleWithLegacyFallback() {
        assertEquals("compass.hunter-name", CompassManager.compassNameKey(Role.HUNTER));
        assertEquals("compass.speedrunner-name", CompassManager.compassNameKey(Role.SPEEDRUNNER));
        assertEquals("compass.compass-name", CompassManager.compassNameKey(Role.NONE));
        assertEquals("compass.hunter-lore", CompassManager.compassLoreKey(Role.HUNTER));
        assertEquals("compass.speedrunner-lore", CompassManager.compassLoreKey(Role.SPEEDRUNNER));
        assertEquals("compass.compass-lore", CompassManager.compassLoreKey(Role.SPECTATOR));
    }
}
