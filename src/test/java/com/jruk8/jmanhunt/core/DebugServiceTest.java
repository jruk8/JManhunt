package com.jruk8.jmanhunt.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugServiceTest {

    @Test
    void everythingStartsDisabled() {
        DebugService debug = new DebugService();

        assertNull(debug.consoleLevel());
        assertFalse(debug.isConsoleEnabled());
        assertFalse(debug.isPlayerEnabled(UUID.randomUUID()));
        assertFalse(debug.hasRecipients());
        assertTrue(debug.playerLevels().isEmpty());
    }

    @Test
    void consoleToggleEnablesInfoThenDisables() {
        DebugService debug = new DebugService();

        assertEquals(Optional.of(DebugLevel.INFO), debug.toggleConsole());
        assertEquals(DebugLevel.INFO, debug.consoleLevel());
        assertTrue(debug.isConsoleEnabled());
        assertTrue(debug.hasRecipients());

        assertEquals(Optional.empty(), debug.toggleConsole());
        assertNull(debug.consoleLevel());
        assertFalse(debug.isConsoleEnabled());
        assertFalse(debug.hasRecipients());
    }

    @Test
    void consoleToggleFromLevelDisables() {
        DebugService debug = new DebugService();
        debug.setConsoleLevel(DebugLevel.SEVERE);

        assertEquals(Optional.empty(), debug.toggleConsole());
        assertFalse(debug.hasRecipients());
    }

    @Test
    void playerToggleIsPerPlayer() {
        DebugService debug = new DebugService();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals(Optional.of(DebugLevel.INFO), debug.togglePlayer(first));
        assertEquals(DebugLevel.INFO, debug.playerLevel(first));
        assertTrue(debug.isPlayerEnabled(first));
        assertFalse(debug.isPlayerEnabled(second));
        assertEquals(Map.of(first, DebugLevel.INFO), debug.playerLevels());

        assertEquals(Optional.empty(), debug.togglePlayer(first));
        assertNull(debug.playerLevel(first));
        assertFalse(debug.hasRecipients());
    }

    @Test
    void explicitSetAndDisableWinOverToggle() {
        DebugService debug = new DebugService();
        UUID player = UUID.randomUUID();
        debug.toggleConsole();
        debug.togglePlayer(player);

        assertEquals(DebugLevel.WARN, debug.setConsoleLevel(DebugLevel.WARN));
        assertEquals(DebugLevel.SEVERE, debug.setPlayerLevel(player, DebugLevel.SEVERE));
        assertTrue(debug.hasRecipients());

        debug.disableConsole();
        debug.disablePlayer(player);
        assertFalse(debug.hasRecipients());
    }

    @Test
    void consoleAndPlayersStayIndependent() {
        DebugService debug = new DebugService();
        UUID player = UUID.randomUUID();
        debug.setConsoleLevel(DebugLevel.WARN);
        debug.setPlayerLevel(player, DebugLevel.SEVERE);

        debug.disableConsole();

        assertFalse(debug.isConsoleEnabled());
        assertTrue(debug.isPlayerEnabled(player));
        assertTrue(debug.hasRecipients());
    }
}
