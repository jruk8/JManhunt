package com.jruk8.jmanhunt.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import org.mockito.MockedStatic;

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
    void syncTeamReportsRoleHolders() {
        PlayerStateStore states = new PlayerStateStore();
        Player hunter = mock(Player.class);
        when(hunter.getName()).thenReturn("Hunter");
        when(hunter.getUniqueId()).thenReturn(UUID.randomUUID());
        Player none = mock(Player.class);
        when(none.getName()).thenReturn("None");
        when(none.getUniqueId()).thenReturn(UUID.randomUUID());
        states.setRole(hunter, Role.HUNTER);
        states.setRole(none, Role.NONE);
        RoleTeamService teams = new RoleTeamService(states, () -> true);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScoreboardManager).thenReturn(null);

            assertTrue(teams.syncTeam(hunter));
            assertFalse(teams.syncTeam(none));
        }
    }
}
