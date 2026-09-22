package com.jruk8.jmanhunt.lobby;

import org.bukkit.Location;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jruk8.jmanhunt.lobby.world.LobbySchematicService;

class LobbySchematicServiceTest {

    @Test
    void oddSizesCenterExactly() {
        assertEquals(-4, LobbySchematicService.cornerAxis(0, 9));
        assertEquals(60, LobbySchematicService.cornerAxis(64, 9));
    }

    @Test
    void evenSizesLeanNegative() {
        assertEquals(-5, LobbySchematicService.cornerAxis(0, 10));
        assertEquals(59, LobbySchematicService.cornerAxis(64, 10));
    }

    @Test
    void singleBlockLandsOnOrigin() {
        assertEquals(0, LobbySchematicService.cornerAxis(0, 1));
        assertEquals(64, LobbySchematicService.cornerAxis(64, 1));
    }

    @Test
    void cornerCentersOnMidpointBlock() {
        Location corner = LobbySchematicService.cornerFor(null,
                new Location(null, 100.5, 70.0, -50.2), new BlockVector(9, 5, 10));
        assertEquals(96, corner.getBlockX());
        assertEquals(68, corner.getBlockY());
        assertEquals(-56, corner.getBlockZ());
    }

    @Test
    void presetMidpointMatchesOrigin() {
        Location corner = LobbySchematicService.cornerFor(null,
                new Location(null, 0, 64, 0), new BlockVector(9, 9, 10));
        assertEquals(-4, corner.getBlockX());
        assertEquals(60, corner.getBlockY());
        assertEquals(-5, corner.getBlockZ());
    }
}
