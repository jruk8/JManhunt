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
        assertEquals(Material.COMPASS, CompassManager.resolveCompassMaterial("compass"));
        assertEquals(Material.COMPASS, CompassManager.resolveCompassMaterial("minecraft:compass"));
        assertEquals(Material.RECOVERY_COMPASS,
                CompassManager.resolveCompassMaterial("recovery_compass"));
        assertEquals(Material.CLOCK, CompassManager.resolveCompassMaterial("minecraft:clock"));
    }

    @Test
    void rejectsUnknownNames() {
        assertNull(CompassManager.resolveCompassMaterial("compas"));
        assertNull(CompassManager.resolveCompassMaterial(null));
        assertNull(CompassManager.resolveCompassMaterial("  "));
        assertNull(CompassManager.resolveCompassMaterial("other:compass"));
    }

    @Test
    void rejectsPlaceableItems() {
        assertNull(CompassManager.resolveCompassMaterial("dirt"));
        assertNull(CompassManager.resolveCompassMaterial("minecraft:oak_sign"));
        assertNull(CompassManager.resolveCompassMaterial("redstone"));
        assertNull(CompassManager.resolveCompassMaterial("minecraft:water_bucket"));
        assertNull(CompassManager.resolveCompassMaterial("zombie_spawn_egg"));
    }

    @Test
    void allowsRawItems() {
        assertTrue(CompassManager.isAllowedCompassItem(Material.COMPASS));
        assertTrue(CompassManager.isAllowedCompassItem(Material.CLOCK));
        assertTrue(CompassManager.isAllowedCompassItem(Material.RECOVERY_COMPASS));
        assertFalse(CompassManager.isAllowedCompassItem(Material.DIRT));
        assertFalse(CompassManager.isAllowedCompassItem(null));
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
        assertEquals(20L, CompassManager.analyzeDelayTicks(1.0));
        assertEquals(10L, CompassManager.analyzeDelayTicks(0.5));
        assertEquals(1L, CompassManager.analyzeDelayTicks(0.0));
        assertEquals(1L, CompassManager.analyzeDelayTicks(-2.0));
    }
}
