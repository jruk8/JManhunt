package com.jruk8.jmanhunt.world;

import com.jruk8.jmanhunt.lobby.LobbyConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEngineServiceTest {
    @Test
    void careDueRunsOncePerInterval() {
        assertTrue(WorldEngineService.careDue(100_000L, 0L, 15));
        assertTrue(WorldEngineService.careDue(115_000L, 100_000L, 15));
        assertFalse(WorldEngineService.careDue(114_999L, 100_000L, 15));
        assertFalse(WorldEngineService.careDue(100_000L, 100_000L, 15));
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
