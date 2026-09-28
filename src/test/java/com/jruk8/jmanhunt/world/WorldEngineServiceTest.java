package com.jruk8.jmanhunt.world;

import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jruk8.jmanhunt.world.teleport.LobbyWorldService;

class WorldEngineServiceTest {
    @Test
    void careDueRunsOncePerInterval() {
        assertTrue(LobbyWorldService.careDue(100_000L, 0L, 15));
        assertTrue(LobbyWorldService.careDue(115_000L, 100_000L, 15));
        assertFalse(LobbyWorldService.careDue(114_999L, 100_000L, 15));
        assertFalse(LobbyWorldService.careDue(100_000L, 100_000L, 15));
    }

    @Test
    void borderedWorldSkipsLobbyAndUnsupportedEnvironments() {
        assertTrue(WorldEngineService.borderedWorld(World.Environment.NORMAL, false));
        assertTrue(WorldEngineService.borderedWorld(World.Environment.NETHER, false));
        assertFalse(WorldEngineService.borderedWorld(World.Environment.NORMAL, true));
        assertFalse(WorldEngineService.borderedWorld(World.Environment.NETHER, true));
        assertFalse(WorldEngineService.borderedWorld(World.Environment.THE_END, false));
        assertFalse(WorldEngineService.borderedWorld(World.Environment.CUSTOM, false));
    }

    @Test
    void lobbyCareDefaultsToHealAndFeed() {
        LobbyConfig.CareData care = new LobbyConfig().getCare();

        assertNotNull(care);
        assertNotNull(care.getHeal());
        assertNotNull(care.getSaturate());
        assertTrue(care.getHeal().isEnabled());
        assertTrue(care.getSaturate().isEnabled());
        assertEquals(15, care.getInterval());
    }
}
