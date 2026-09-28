package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
    void blinkDelayRoundsToTicks() {
        assertEquals(12L, CompassDeltaRenderer.blinkDelayTicks(0.6));
        assertEquals(0L, CompassDeltaRenderer.blinkDelayTicks(0.0));
        assertEquals(0L, CompassDeltaRenderer.blinkDelayTicks(-1.0));
        assertEquals(20L, CompassDeltaRenderer.blinkDelayTicks(1.0));
    }

    @Test
    void deltaReverseNeedsHunterAndToggle() {
        assertTrue(CompassDeltaRenderer.deltaReverse(Role.HUNTER, true));
        assertFalse(CompassDeltaRenderer.deltaReverse(Role.HUNTER, false));
        assertFalse(CompassDeltaRenderer.deltaReverse(Role.SPEEDRUNNER, true));
        assertFalse(CompassDeltaRenderer.deltaReverse(Role.NONE, true));
    }

    @Test
    void deltaFormatSwapsOnlyWhenReversed() {
        assertEquals("<red>▼", CompassDeltaRenderer.deltaFormat(
                DistanceDelta.Kind.FURTHER, "<green>▲", "<red>▼", true));
        assertEquals("<green>▲", CompassDeltaRenderer.deltaFormat(
                DistanceDelta.Kind.CLOSER, "<green>▲", "<red>▼", true));
        assertEquals("<green>▲", CompassDeltaRenderer.deltaFormat(
                DistanceDelta.Kind.FURTHER, "<green>▲", "<red>▼", false));
        assertEquals("<red>▼", CompassDeltaRenderer.deltaFormat(
                DistanceDelta.Kind.CLOSER, "<green>▲", "<red>▼", false));
    }

    @Test
    void autoRefreshDueNeedsFullInterval() {
        assertTrue(CompassManager.autoRefreshDue(10_000L, 0L, 10.0));
        assertFalse(CompassManager.autoRefreshDue(9_999L, 0L, 10.0));
        assertTrue(CompassManager.autoRefreshDue(13_000L, 3_000L, 10.0));
        assertFalse(CompassManager.autoRefreshDue(12_999L, 3_000L, 10.0));
    }

    @Test
    void autoRefreshDueNegativeDisables() {
        assertFalse(CompassManager.autoRefreshDue(10_000L, 0L, -1.0));
        assertFalse(CompassManager.autoRefreshDue(Long.MAX_VALUE, 0L, -1.0));
    }

    @Test
    void effectiveSpotPrefersCachedSameWorldSpot() {
        World world = mock(World.class);
        Location cached = new Location(world, 1.0, 64.0, 1.0);
        Location live = new Location(world, 9.0, 64.0, 9.0);

        assertSame(cached, CompassManager.effectiveSpot(cached, live));
        assertSame(live, CompassManager.effectiveSpot(null, live));
    }

    @Test
    void effectiveSpotFallsBackAcrossWorlds() {
        Location cached = new Location(mock(World.class), 1.0, 64.0, 1.0);
        Location live = new Location(mock(World.class), 9.0, 64.0, 9.0);

        assertSame(live, CompassManager.effectiveSpot(cached, live));
    }

    @Test
    void flatDistanceIgnoresHeight() {
        Location from = new Location(null, 0.0, 64.0, 0.0);
        Location to = new Location(null, 3.0, 200.0, 4.0);
        assertEquals(5.0, CompassTargetService.flatDistance(from, to), 1e-9);
    }

    @Test
    void coverSkipsTransparentBlocksOnlyWhenIgnored() {
        assertFalse(CompassSignalService.countsAsCover(false, false, true));
        assertFalse(CompassSignalService.countsAsCover(false, true, false));
        assertFalse(CompassSignalService.countsAsCover(true, false, true));
        assertTrue(CompassSignalService.countsAsCover(true, false, false));
        assertTrue(CompassSignalService.countsAsCover(true, true, true));
        assertTrue(CompassSignalService.countsAsCover(true, true, false));
    }

    @Test
    void rayClearSeesOpenRaysAndBlockedOnes() {
        assertTrue(CompassSignalService.rayClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 300.0,
                (x, y, z) -> false));
        assertFalse(CompassSignalService.rayClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 300.0,
                (x, y, z) -> x == 5 && y == 64 && z == 0));
    }

    @Test
    void rayClearFailsPastMaxDistance() {
        assertFalse(CompassSignalService.rayClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 5.0,
                (x, y, z) -> false));
        assertTrue(CompassSignalService.rayClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 10.0,
                (x, y, z) -> false));
    }

    @Test
    void rayClearSkipsStartBlockButCountsEndBlock() {
        assertTrue(CompassSignalService.rayClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 300.0,
                (x, y, z) -> x == 0 && y == 64 && z == 0));
        assertFalse(CompassSignalService.rayClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 300.0,
                (x, y, z) -> x == 10 && y == 64 && z == 0));
    }

    @Test
    void movedBlocksMeasuresSameWorldDistance() {
        World world = mock(World.class);
        Location from = new Location(world, 0.0, 64.0, 0.0);
        Location to = new Location(world, 3.0, 64.0, 4.0);

        assertEquals(5.0, CompassSignalService.movedBlocks(from, to), 1e-9);
    }

    @Test
    void movedBlocksIsZeroWithoutSpotsAndHugeAcrossWorlds() {
        Location spot = new Location(mock(World.class), 0.0, 64.0, 0.0);
        Location other = new Location(mock(World.class), 0.0, 64.0, 0.0);
        Location nowhere = new Location(null, 0.0, 64.0, 0.0);

        assertEquals(0.0, CompassSignalService.movedBlocks(null, null), 1e-9);
        assertEquals(0.0, CompassSignalService.movedBlocks(null, spot), 1e-9);
        assertEquals(0.0, CompassSignalService.movedBlocks(spot, null), 1e-9);
        assertEquals(Double.MAX_VALUE, CompassSignalService.movedBlocks(spot, other), 0.0);
        assertEquals(Double.MAX_VALUE, CompassSignalService.movedBlocks(spot, nowhere), 0.0);
    }

    @Test
    void compassKeysFollowRoleWithLegacyFallback() {
        assertEquals("compass.hunter-name", CompassItemService.compassNameKey(Role.HUNTER));
        assertEquals("compass.speedrunner-name", CompassItemService.compassNameKey(Role.SPEEDRUNNER));
        assertEquals("compass.compass-name", CompassItemService.compassNameKey(Role.NONE));
        assertEquals("compass.hunter-lore", CompassItemService.compassLoreKey(Role.HUNTER));
        assertEquals("compass.speedrunner-lore", CompassItemService.compassLoreKey(Role.SPEEDRUNNER));
        assertEquals("compass.compass-lore", CompassItemService.compassLoreKey(Role.SPECTATOR));
    }
}
