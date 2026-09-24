package com.jruk8.jmanhunt.compass;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompassItemTest {

    @Test
    void resolvesModernNames() {
        assertEquals(Material.COMPASS, CompassItemService.resolveCompassMaterial("compass"));
        assertEquals(Material.COMPASS, CompassItemService.resolveCompassMaterial("minecraft:compass"));
        assertEquals(Material.RECOVERY_COMPASS,
                CompassItemService.resolveCompassMaterial("recovery_compass"));
        assertEquals(Material.CLOCK, CompassItemService.resolveCompassMaterial("minecraft:clock"));
    }

    @Test
    void rejectsUnknownNames() {
        assertNull(CompassItemService.resolveCompassMaterial("compas"));
        assertNull(CompassItemService.resolveCompassMaterial(null));
        assertNull(CompassItemService.resolveCompassMaterial("  "));
        assertNull(CompassItemService.resolveCompassMaterial("other:compass"));
    }

    @Test
    void rejectsPlaceableItems() {
        assertNull(CompassItemService.resolveCompassMaterial("dirt"));
        assertNull(CompassItemService.resolveCompassMaterial("minecraft:oak_sign"));
        assertNull(CompassItemService.resolveCompassMaterial("redstone"));
        assertNull(CompassItemService.resolveCompassMaterial("minecraft:water_bucket"));
        assertNull(CompassItemService.resolveCompassMaterial("zombie_spawn_egg"));
    }

    @Test
    void allowsRawItems() {
        assertTrue(CompassItemService.isAllowedCompassItem(Material.COMPASS));
        assertTrue(CompassItemService.isAllowedCompassItem(Material.CLOCK));
        assertTrue(CompassItemService.isAllowedCompassItem(Material.RECOVERY_COMPASS));
        assertFalse(CompassItemService.isAllowedCompassItem(Material.DIRT));
        assertFalse(CompassItemService.isAllowedCompassItem(null));
    }

    @Test
    void skipsOnlyRespawningSpectators() {
        assertTrue(CompassManager.skipLastSeen(true, GameMode.SPECTATOR));
        assertFalse(CompassManager.skipLastSeen(true, GameMode.SURVIVAL));
        assertFalse(CompassManager.skipLastSeen(true, GameMode.ADVENTURE));
        assertFalse(CompassManager.skipLastSeen(false, GameMode.SPECTATOR));
        assertFalse(CompassManager.skipLastSeen(false, null));
    }

    @Test
    void analyzeDelayTicksConvertsSeconds() {
        assertEquals(20L, CompassLockService.analyzeDelayTicks(1.0));
        assertEquals(10L, CompassLockService.analyzeDelayTicks(0.5));
        assertEquals(1L, CompassLockService.analyzeDelayTicks(0.0));
        assertEquals(1L, CompassLockService.analyzeDelayTicks(-2.0));
    }

    @Test
    void analysisTickIntervalRoundsToWholeTicks() {
        assertEquals(10L, CompassLockService.analysisTickInterval(0.5));
        assertEquals(20L, CompassLockService.analysisTickInterval(1.0));
        assertEquals(1L, CompassLockService.analysisTickInterval(0.07));
        assertEquals(2L, CompassLockService.analysisTickInterval(0.08));
        assertEquals(1L, CompassLockService.analysisTickInterval(0.0));
        assertEquals(1L, CompassLockService.analysisTickInterval(-1.0));
    }
}
