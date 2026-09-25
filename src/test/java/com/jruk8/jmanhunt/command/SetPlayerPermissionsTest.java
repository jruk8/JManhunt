package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.player.Role;
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
        assertEquals("jmanhunt.spectator", ManhuntCommand.rolePermissionNode(Role.SPECTATOR));
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

    @Test
    void subcommandGateFollowsCommandNodes() {
        CommandSender sender = senderWith("jmanhunt.command.status");

        assertTrue(ManhuntCommand.canUseSubcommand(sender, "status"));
        assertFalse(ManhuntCommand.canUseSubcommand(sender, "start"));
    }

    @Test
    void statusArgsNeedTheirOwnNode() {
        CommandSender base = senderWith("jmanhunt.command.status");
        CommandSender full = senderWith("jmanhunt.command.status",
                "jmanhunt.command.status.other");

        assertFalse(ManhuntCommand.canUseStatusArgs(base));
        assertTrue(ManhuntCommand.canUseStatusArgs(full));
    }

    @Test
    void subcommandGateCoversSwapRoles() {
        CommandSender swap = senderWith("jmanhunt.command.swaproles");
        CommandSender other = senderWith("jmanhunt.command.status");

        assertTrue(ManhuntCommand.canUseSubcommand(swap, "swaproles"));
        assertFalse(ManhuntCommand.canUseSubcommand(other, "swaproles"));
        assertTrue(ManhuntCommand.subcommandOptions().contains("swaproles"));
    }

    @Test
    void subcommandGateAcceptsAliasesAndSelfNode() {
        CommandSender selfOnly = senderWith("jmanhunt.command.setplayer.self");
        CommandSender config = senderWith("jmanhunt.command.config");
        CommandSender quick = senderWith("jmanhunt.command.quickstart");

        assertTrue(ManhuntCommand.canUseSubcommand(selfOnly, "setplayer"));
        assertFalse(ManhuntCommand.canUseSubcommand(selfOnly, "start"));
        assertTrue(ManhuntCommand.canUseSubcommand(config, "config"));
        assertTrue(ManhuntCommand.canUseSubcommand(quick, "qs"));
    }

    @Test
    void worldEngineBaseNodeImpliesEveryAction() {
        CommandSender sender = senderWith("jmanhunt.command.worldengine");

        assertTrue(ManhuntCommand.canUseWorldEngineAction(sender, "setlobby"));
        assertTrue(ManhuntCommand.canUseWorldEngineAction(sender, "tpto"));
        assertTrue(ManhuntCommand.canUseWorldEngineAction(sender, "cellindex"));
    }

    @Test
    void worldEngineSubNodesGrantSingleActions() {
        CommandSender sender = senderWith("jmanhunt.command.worldengine.tpto");

        assertTrue(ManhuntCommand.canUseWorldEngineAction(sender, "tpto"));
        assertFalse(ManhuntCommand.canUseWorldEngineAction(sender, "setlobby"));
        assertFalse(ManhuntCommand.canUseWorldEngineAction(sender, "cellindex"));
        assertFalse(ManhuntCommand.canUseWorldEngineAction(sender, "bogus"));
    }

    @Test
    void modifiersGateUsesItsOwnNode() {
        CommandSender renamed = senderWith("jmanhunt.modifiers");
        CommandSender legacy = senderWith("jmanhunt.command.modifiers");

        assertTrue(ManhuntCommand.canUseSubcommand(renamed, "modifiers"));
        assertFalse(ManhuntCommand.canUseSubcommand(legacy, "modifiers"));
        assertFalse(ManhuntCommand.canUseSubcommand(renamed, "start"));
    }

    private static CommandSender senderWith(String... permissions) {
        CommandSender sender = mock(CommandSender.class);
        java.util.Set<String> granted = java.util.Set.of(permissions);
        when(sender.hasPermission(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> granted.contains(call.getArgument(0)));
        return sender;
    }
}
