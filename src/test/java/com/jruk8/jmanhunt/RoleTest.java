package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleTest {

    @Test
    void hunterIsParticipant() {
        assertTrue(Role.HUNTER.isParticipant());
    }

    @Test
    void speedrunnerIsParticipant() {
        assertTrue(Role.SPEEDRUNNER.isParticipant());
    }

    @Test
    void afkIsNotParticipant() {
        assertFalse(Role.AFK.isParticipant());
    }

    @Test
    void noneIsNotParticipant() {
        assertFalse(Role.NONE.isParticipant());
    }

    @Test
    void displayNamesArePretty() {
        assertEquals("Hunter", Role.HUNTER.displayName());
        assertEquals("Speedrunner", Role.SPEEDRUNNER.displayName());
        assertEquals("Afk", Role.AFK.displayName());
        assertEquals("None", Role.NONE.displayName());
    }

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(Optional.of(Role.HUNTER), Role.parse("hunter"));
        assertEquals(Optional.of(Role.SPEEDRUNNER), Role.parse("SpeedRunner"));
        assertEquals(Optional.of(Role.AFK), Role.parse("AFK"));
        assertEquals(Optional.of(Role.NONE), Role.parse(" none "));
    }

    @Test
    void parseRejectsUnknownAndNull() {
        assertEquals(Optional.empty(), Role.parse("admin"));
        assertEquals(Optional.empty(), Role.parse(""));
        assertEquals(Optional.empty(), Role.parse(null));
    }
}
