package com.jruk8.jmanhunt.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dev-data preset lookups over the bundled Core/dev.yml schema. */
class DevConfigTest {

    @Test
    void presetKeysListDefaultsInOrder() {
        DevConfig dev = new DevConfig();

        assertEquals(List.of("EMPTY", "DEFAULT", "ADVANCED"),
                List.copyOf(dev.presetKeys()));
    }

    @Test
    void schematicAndCommandsResolveCaseInsensitively() {
        DevConfig dev = new DevConfig();

        assertEquals("default-lobby", dev.schematicFor("DEFAULT"));
        assertEquals("default-lobby", dev.schematicFor("default"));
        assertTrue(dev.commandsFor("DEFAULT").isEmpty());
    }

    @Test
    void unknownPresetFallsBackToBlankAndEmpty() {
        DevConfig dev = new DevConfig();

        assertEquals("", dev.schematicFor("BOGUS"));
        assertEquals(Set.of(), Set.copyOf(dev.commandsFor("BOGUS")));
    }

    @Test
    void nullMapReadsEmpty() {
        DevConfig dev = new DevConfig();
        dev.setLobbyPresets(null);

        assertEquals(Set.of(), dev.presetKeys());
        assertEquals("", dev.schematicFor("DEFAULT"));
        assertTrue(dev.commandsFor("DEFAULT").isEmpty());
    }
}
