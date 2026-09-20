package com.jruk8.jmanhunt.lobby;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RolePadServiceTest {

    @Test
    void padMaterialsParseCaseInsensitively() {
        assertEquals(Material.LIME_CONCRETE, RolePadService.parsePadMaterial("lime_concrete"));
        assertEquals(Material.RED_CONCRETE, RolePadService.parsePadMaterial("RED_CONCRETE"));
        assertEquals(Material.LIGHT_GRAY_CONCRETE, RolePadService.parsePadMaterial(" light_gray_concrete "));
    }

    @Test
    void padMaterialsRejectBlanksAndUnknown() {
        assertNull(RolePadService.parsePadMaterial(null));
        assertNull(RolePadService.parsePadMaterial(""));
        assertNull(RolePadService.parsePadMaterial("not-a-material"));
    }

    @Test
    void packedBlocksDistinguishPositions() {
        assertEquals(RolePadService.packBlock(10, 64, -3), RolePadService.packBlock(10, 64, -3));
        assertNotEquals(RolePadService.packBlock(10, 64, -3), RolePadService.packBlock(11, 64, -3));
        assertNotEquals(RolePadService.packBlock(10, 64, -3), RolePadService.packBlock(10, 65, -3));
        assertNotEquals(RolePadService.packBlock(10, 64, -3), RolePadService.packBlock(10, 64, -4));
    }
}
