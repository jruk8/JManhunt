package com.jruk8.jmanhunt;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SetPlayerPermissionsTest {

    @Test
    void rolePermissionNodes() {
        assertEquals("jmanhunt.hunter", ManhuntCommand.rolePermissionNode(Role.HUNTER));
        assertEquals("jmanhunt.speedrunner", ManhuntCommand.rolePermissionNode(Role.SPEEDRUNNER));
        assertEquals("jmanhunt.afk", ManhuntCommand.rolePermissionNode(Role.AFK));
        assertEquals("jmanhunt.none", ManhuntCommand.rolePermissionNode(Role.NONE));
    }

    @Test
    void selfOnlySelectionAcceptsExactlyTheSender() {
        Player self = player(UUID.randomUUID());

        assertTrue(ManhuntCommand.isSelfOnlySelection(self, List.of(self)));
    }

    @Test
    void selfOnlySelectionRejectsOtherPlayers() {
        Player self = player(UUID.randomUUID());
        Player other = player(UUID.randomUUID());

        assertFalse(ManhuntCommand.isSelfOnlySelection(self, List.of(other)));
    }

    @Test
    void selfOnlySelectionRejectsGroupsContainingSelf() {
        Player self = player(UUID.randomUUID());
        Player other = player(UUID.randomUUID());

        assertFalse(ManhuntCommand.isSelfOnlySelection(self, List.of(self, other)));
    }

    @Test
    void selfOnlySelectionRejectsEmptySelections() {
        Player self = player(UUID.randomUUID());

        assertFalse(ManhuntCommand.isSelfOnlySelection(self, List.of()));
    }

    @Test
    void selfOnlySelectionRejectsConsoleSenders() {
        CommandSender console = mock(CommandSender.class);
        Player self = player(UUID.randomUUID());

        assertFalse(ManhuntCommand.isSelfOnlySelection(console, List.of(self)));
    }

    @Test
    void selfOnlySelectionRejectsNonPlayerTargets() {
        Player self = player(UUID.randomUUID());
        Entity entity = mock(Entity.class);

        assertFalse(ManhuntCommand.isSelfOnlySelection(self, List.of(entity)));
    }

    private static Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }
}
