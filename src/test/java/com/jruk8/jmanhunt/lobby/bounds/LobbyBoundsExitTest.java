package com.jruk8.jmanhunt.lobby.bounds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Bounds exit behavior: parsing, transitions, and destinations. */
class LobbyBoundsExitTest {

    @Test
    void parseAcceptsBothModesCaseInsensitively() {
        assertEquals(LobbyBoundsExitBehavior.KEEP_IN_LOBBY,
                LobbyBoundsExitBehavior.parse("KEEP_IN_LOBBY"));
        assertEquals(LobbyBoundsExitBehavior.EXIT_LOBBY,
                LobbyBoundsExitBehavior.parse("exit_lobby"));
        assertEquals(LobbyBoundsExitBehavior.EXIT_LOBBY,
                LobbyBoundsExitBehavior.parse("  Exit_Lobby  "));
    }

    @Test
    void parseFallsBackToKeep() {
        assertEquals(LobbyBoundsExitBehavior.KEEP_IN_LOBBY,
                LobbyBoundsExitBehavior.parse(null));
        assertEquals(LobbyBoundsExitBehavior.KEEP_IN_LOBBY,
                LobbyBoundsExitBehavior.parse(""));
        assertEquals(LobbyBoundsExitBehavior.KEEP_IN_LOBBY,
                LobbyBoundsExitBehavior.parse("  "));
        assertEquals(LobbyBoundsExitBehavior.KEEP_IN_LOBBY,
                LobbyBoundsExitBehavior.parse("BLOCK_EXIT"));
    }

    private static Map<Integer, LobbyBounds.Bound> bounds() {
        return Map.of(
                0, new LobbyBounds.Bound(0, 60, 0, 9, 70, 9),
                1, new LobbyBounds.Bound(100, 60, 100, 109, 70, 109));
    }

    @Test
    void transitionFindsDifferentLobbyBox() {
        assertEquals(OptionalInt.of(1),
                LobbyBoundsService.transitionTarget(bounds(), 0, 105.5, 65.0, 105.5));
        assertEquals(OptionalInt.of(0),
                LobbyBoundsService.transitionTarget(bounds(), 1, 5.5, 65.0, 5.5));
    }

    @Test
    void transitionIgnoresOwnBoxAndOpenGround() {
        assertTrue(LobbyBoundsService.transitionTarget(bounds(), 0, 5.5, 65.0, 5.5).isEmpty());
        assertTrue(LobbyBoundsService.transitionTarget(bounds(), 0, 50.5, 65.0, 50.5).isEmpty());
        assertTrue(LobbyBoundsService.transitionTarget(Map.of(), 0, 5.5, 65.0, 5.5).isEmpty());
    }

    @Test
    void destinationMapping() {
        // Same target: the exit changes nothing.
        assertEquals(0, LobbyBoundsService.exitDestination(0, 0, true));
        assertEquals(1, LobbyBoundsService.exitDestination(1, 1, false));
        // Negative target: lobby-less.
        assertEquals(-1, LobbyBoundsService.exitDestination(0, -1, true));
        assertEquals(-1, LobbyBoundsService.exitDestination(2, -5, true));
        // Engine off: only lobby 0 is reachable, anything else drops out.
        assertEquals(0, LobbyBoundsService.exitDestination(1, 0, false));
        assertEquals(-1, LobbyBoundsService.exitDestination(0, 2, false));
        // Engine on: the target lobby wins.
        assertEquals(2, LobbyBoundsService.exitDestination(0, 2, true));
        assertEquals(0, LobbyBoundsService.exitDestination(2, 0, true));
    }
}
