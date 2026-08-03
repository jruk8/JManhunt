package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

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
}
