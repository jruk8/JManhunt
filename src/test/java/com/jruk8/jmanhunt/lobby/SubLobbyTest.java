package com.jruk8.jmanhunt.lobby;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubLobbyTest {

    @Test
    void formatRendersLobbyDashSub() {
        assertEquals("L2-0", new SubLobby(2, 0).format());
        assertEquals("L0-12", new SubLobby(0, 12).format());
    }

    @Test
    void parseRoundTripsFormat() {
        assertEquals(Optional.of(new SubLobby(2, 0)), SubLobby.parse("L2-0"));
        assertEquals(Optional.of(new SubLobby(0, 12)), SubLobby.parse("  L0-12  "));
    }

    @Test
    void parseRejectsNonSublobbies() {
        assertTrue(SubLobby.parse(null).isEmpty());
        assertTrue(SubLobby.parse("").isEmpty());
        assertTrue(SubLobby.parse("L2").isEmpty());
        assertTrue(SubLobby.parse("2-0").isEmpty());
        assertTrue(SubLobby.parse("Lx-0").isEmpty());
        assertTrue(SubLobby.parse("L99999999999-0").isEmpty());
    }
}
