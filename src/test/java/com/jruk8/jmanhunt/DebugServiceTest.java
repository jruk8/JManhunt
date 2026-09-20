package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugServiceTest {

    @Test
    void everythingStartsDisabled() {
        DebugService debug = new DebugService();

        assertFalse(debug.isConsoleEnabled());
        assertFalse(debug.isPlayerEnabled(UUID.randomUUID()));
        assertFalse(debug.hasRecipients());
        assertTrue(debug.debugPlayerIds().isEmpty());
    }

    @Test
    void consoleToggleFlipsState() {
        DebugService debug = new DebugService();

        assertTrue(debug.toggleConsole());
        assertTrue(debug.isConsoleEnabled());
        assertTrue(debug.hasRecipients());
        assertFalse(debug.toggleConsole());
        assertFalse(debug.hasRecipients());
    }

    @Test
    void playerToggleIsPerPlayer() {
        DebugService debug = new DebugService();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(debug.togglePlayer(first));
        assertTrue(debug.isPlayerEnabled(first));
        assertFalse(debug.isPlayerEnabled(second));
        assertEquals(1, debug.debugPlayerIds().size());
        assertFalse(debug.togglePlayer(first));
        assertFalse(debug.hasRecipients());
    }

    @Test
    void explicitSetWinsOverToggle() {
        DebugService debug = new DebugService();
        UUID player = UUID.randomUUID();

        debug.toggleConsole();
        debug.togglePlayer(player);
        assertFalse(debug.setConsoleEnabled(false));
        assertFalse(debug.setPlayerEnabled(player, false));
        assertFalse(debug.hasRecipients());
    }

    @Test
    void resetAppliesConsoleDefaultAndClearsPlayers() {
        DebugService debug = new DebugService();
        debug.togglePlayer(UUID.randomUUID());

        debug.resetToDefaults(true);
        assertTrue(debug.isConsoleEnabled());
        assertTrue(debug.debugPlayerIds().isEmpty());

        debug.resetToDefaults(false);
        assertFalse(debug.isConsoleEnabled());
    }
}
