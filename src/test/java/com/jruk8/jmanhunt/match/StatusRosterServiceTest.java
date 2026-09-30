package com.jruk8.jmanhunt.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StatusRosterServiceTest {

    private record Fixture(StatusRosterService roster, MessageService messages,
            PlayerStateStore players, CommandSender recipient) {
    }

    private static Fixture fixture() {
        MessageService messages = mock(MessageService.class);
        when(messages.string(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        PlayerStateStore players = new PlayerStateStore();
        CommandSender recipient = mock(CommandSender.class);
        return new Fixture(new StatusRosterService(messages, players), messages, players, recipient);
    }

    private static Player named(String name, Fixture fixture, Role role) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        fixture.players().setRole(player, role);
        return player;
    }

    @Test
    void roleSectionJoinsAliveOxfordAndGhostsNewestFirst() {
        Fixture fixture = fixture();
        Player amy = named("amy", fixture, Role.HUNTER);
        Player bob = named("bob", fixture, Role.HUNTER);
        named("zed", fixture, Role.SPEEDRUNNER);
        UUID oldId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        List<GameInstance.DeadPlayer> dead = List.of(
                new GameInstance.DeadPlayer(oldId, "old", Role.HUNTER),
                new GameInstance.DeadPlayer(newId, "new", Role.HUNTER),
                new GameInstance.DeadPlayer(UUID.randomUUID(), "runner",
                        Role.SPEEDRUNNER));

        fixture.roster().sendRoleSection(fixture.recipient(), List.of(bob, amy), Role.HUNTER,
                "manhunt.hunters-header", dead, id -> false);

        verify(fixture.messages()).message(fixture.recipient(), "manhunt.hunters-header", Map.of());
        verify(fixture.messages()).message(eq(fixture.recipient()), eq("manhunt.status-player"),
                eq(Map.of("player", "amy <gray>and</gray> bob")));
        verify(fixture.messages()).message(eq(fixture.recipient()),
                eq("manhunt.status-dead-line"),
                eq(Map.of("dead_players", "💀new <gray>and</gray> 💀old")));
    }

    @Test
    void roleSectionTruncatesAliveToRoleLimit() {
        Fixture fixture = fixture();
        List<Player> hunters = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            hunters.add(named("h" + index, fixture, Role.HUNTER));
        }

        fixture.roster().sendRoleSection(fixture.recipient(), hunters, Role.HUNTER,
                "manhunt.hunters-header", List.of(), id -> false);

        verify(fixture.messages()).message(eq(fixture.recipient()), eq("manhunt.status-player"),
                eq(Map.of("player", "h0<gray>,</gray> h1<gray>,</gray> h10<gray>,</gray> "
                        + "h11<gray>,</gray> h2<gray>,</gray> h3<gray>,</gray> h4<gray>,</gray> "
                        + "h5<gray>,</gray> h6<gray>,</gray> h7<gray>,</gray> <gray>and</gray> "
                        + "2 more")));
    }

    @Test
    void roleSectionPrefixesRespawningSkull() {
        Fixture fixture = fixture();
        Player amy = named("amy", fixture, Role.SPEEDRUNNER);

        fixture.roster().sendRoleSection(fixture.recipient(), List.of(amy), Role.SPEEDRUNNER,
                "manhunt.speedrunners-header", List.of(), amy.getUniqueId()::equals);

        verify(fixture.messages()).message(eq(fixture.recipient()), eq("manhunt.status-player"),
                eq(Map.of("player", "💀amy")));
    }

    @Test
    void deadLimitZeroRendersOverflowWord() {
        Fixture fixture = fixture();
        when(fixture.messages().string(eq("manhunt.status-limit-dead"), anyString()))
                .thenReturn("0");
        List<GameInstance.DeadPlayer> dead = List.of(
                new GameInstance.DeadPlayer(UUID.randomUUID(), "a", Role.HUNTER),
                new GameInstance.DeadPlayer(UUID.randomUUID(), "b", Role.HUNTER));

        fixture.roster().sendRoleSection(fixture.recipient(), List.of(), Role.HUNTER,
                "manhunt.hunters-header", dead, id -> false);

        verify(fixture.messages()).message(eq(fixture.recipient()),
                eq("manhunt.status-dead-line"), eq(Map.of("dead_players", "2 dead")));
        verify(fixture.messages(), never()).message(eq(fixture.recipient()),
                eq("manhunt.status-player"), anyMap());
    }

    @Test
    void emptyRoleSectionStaysSilent() {
        Fixture fixture = fixture();
        Player zed = named("zed", fixture, Role.SPEEDRUNNER);

        fixture.roster().sendRoleSection(fixture.recipient(), List.of(zed), Role.HUNTER,
                "manhunt.hunters-header", List.of(), id -> false);

        verifyNoInteractions(fixture.messages());
    }

    @Test
    void spectatorLineTruncatesToFive() {
        Fixture fixture = fixture();
        List<Player> players = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            players.add(named("s" + index, fixture, Role.SPECTATOR));
        }

        fixture.roster().sendSpectatorLine(fixture.recipient(), players);

        verify(fixture.messages()).message(eq(fixture.recipient()), eq("manhunt.spectators-line"),
                eq(Map.of("value", "s0<gray>,</gray> s1<gray>,</gray> s2<gray>,</gray> "
                        + "s3<gray>,</gray> s4<gray>,</gray> <gray>and</gray> 2 more")));
    }

    @Test
    void emptySpectatorLineStaysSilent() {
        Fixture fixture = fixture();
        Player zed = named("zed", fixture, Role.SPEEDRUNNER);

        fixture.roster().sendSpectatorLine(fixture.recipient(), List.of(zed));

        verifyNoInteractions(fixture.messages());
    }

    @Test
    void lobbyDetailSplitsLobbyAndMatch() {
        assertEquals("1 in lobby, 4 in match", StatusRosterService.lobbyDetail(1, 4));
        assertEquals("4 in match", StatusRosterService.lobbyDetail(0, 4));
        assertEquals("4 in lobby", StatusRosterService.lobbyDetail(4, 0));
    }
}
