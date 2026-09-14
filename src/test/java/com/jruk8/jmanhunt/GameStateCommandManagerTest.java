package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GameStateCommandManagerTest {

    @Test
    void legacyDimensionKeysMapToPerPlayerCanonicalNames() {
        assertEquals("ON_NETHER_ENTER",
                GameStateCommandManager.normalizeTrigger("ON_FIRST_ENTER_NETHER"));
        assertEquals("ON_END_ENTER",
                GameStateCommandManager.normalizeTrigger("ON_FIRST_ENTER_END"));
    }

    @Test
    void canonicalAndUnknownKeysPassThrough() {
        assertEquals("ON_NETHER_ENTER",
                GameStateCommandManager.normalizeTrigger("ON_NETHER_ENTER"));
        assertEquals("ON_FIRST_NETHER_ENTER",
                GameStateCommandManager.normalizeTrigger("ON_FIRST_NETHER_ENTER"));
        assertEquals("ON_FIRST_END_ENTER",
                GameStateCommandManager.normalizeTrigger("ON_FIRST_END_ENTER"));
        assertEquals("ON_EVERY_KILL",
                GameStateCommandManager.normalizeTrigger("ON_EVERY_KILL"));
    }

    @Test
    void matchingIgnoresCaseAndSurroundingWhitespace() {
        assertEquals("ON_NETHER_ENTER",
                GameStateCommandManager.normalizeTrigger("  on_first_enter_nether "));
        assertEquals("on_start",
                GameStateCommandManager.normalizeTrigger("  on_start "));
    }

    @Test
    void nullStaysNull() {
        assertNull(GameStateCommandManager.normalizeTrigger(null));
    }
}
