package com.jruk8.jmanhunt.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundsConfigTest {

    @Test
    void matchStartedDefault() {
        SoundsConfig.Game game = new SoundsConfig.Game();

        assertTrue(game.getMatchStarted().isEnabled());
        assertEquals("minecraft:entity.illusioner.ambient", game.getMatchStarted().getSound());
        assertEquals(0.9, game.getMatchStarted().getPitch());
        assertEquals(1.0, game.getMatchStarted().getVolume());
    }
}
