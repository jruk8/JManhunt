package com.jruk8.jmanhunt.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRoleTest {

    @Test
    void participantsAreHuntersAndSpeedrunners() {
        assertTrue(PlayerRole.HUNTER.isParticipant());
        assertTrue(PlayerRole.SPEEDRUNNER.isParticipant());
        assertFalse(PlayerRole.NONE.isParticipant());
        assertFalse(PlayerRole.AFK.isParticipant());
    }
}