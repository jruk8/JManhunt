package com.jruk8.jmanhunt.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import java.util.Optional;

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

    @Test
    void colorForPaintsSides() {
        assertEquals(NamedTextColor.RED, RoleTeamService.colorFor(Role.HUNTER));
        assertEquals(NamedTextColor.GREEN, RoleTeamService.colorFor(Role.SPEEDRUNNER));
    }

    @Test
    void colorForLeavesOthersDefault() {
        assertEquals(NamedTextColor.WHITE, RoleTeamService.colorFor(Role.SPECTATOR));
        assertEquals(NamedTextColor.WHITE, RoleTeamService.colorFor(Role.NONE));
        assertEquals(NamedTextColor.WHITE, RoleTeamService.colorFor(Role.AFK));
        assertEquals(NamedTextColor.WHITE, RoleTeamService.colorFor(null));
    }

    @Test
    void lobbyTeamForMapsEveryRole() {
        assertEquals("jl_hunter", RoleTeamService.lobbyTeamFor(Role.HUNTER));
        assertEquals("jl_speedrunner", RoleTeamService.lobbyTeamFor(Role.SPEEDRUNNER));
        assertEquals("jl_spectator", RoleTeamService.lobbyTeamFor(Role.SPECTATOR));
        assertEquals("jl_none", RoleTeamService.lobbyTeamFor(Role.NONE));
        assertEquals("jl_none", RoleTeamService.lobbyTeamFor(Role.AFK));
        assertEquals("jl_none", RoleTeamService.lobbyTeamFor(null));
    }
}
