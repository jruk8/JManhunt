package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleTeamServiceTest {

    @Test
    void teamForMapsSides() {
        assertEquals(Optional.of("HUNTER"), RoleTeamService.teamFor(Role.HUNTER));
        assertEquals(Optional.of("SPEEDRUNNER"), RoleTeamService.teamFor(Role.SPEEDRUNNER));
        assertEquals(Optional.of("SPECTATOR"), RoleTeamService.teamFor(Role.SPECTATOR));
    }

    @Test
    void teamForLeavesNonSidesUnteamed() {
        assertEquals(Optional.empty(), RoleTeamService.teamFor(Role.NONE));
        assertEquals(Optional.empty(), RoleTeamService.teamFor(Role.AFK));
        assertEquals(Optional.empty(), RoleTeamService.teamFor(null));
    }
}
