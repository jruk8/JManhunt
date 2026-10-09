package com.jruk8.jmanhunt.match.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.config.MatchSettingsFacade;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.world.WorldEngineService;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Leave announcements stay inside the match compartment. */
class MatchLeaveServiceTest {

    private record Fixture(MatchLeaveService leaves, MatchMessaging messaging, GameInstance instance,
            Player player, LobbyService lobbies) {
    }

    private static GameMessages texts() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "game.hunter-left", "hunter left tpl");
        ConfigPathMapper.set(config, "game.speedrunner-left", "runner left tpl");
        return config.getGame();
    }

    private static Fixture fixture(Role role, String destination) {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        MatchSettingsFacade match = mock(MatchSettingsFacade.class);
        when(match.gameLeaveDestination(any())).thenReturn(destination);
        when(plugin.roleTeams()).thenReturn(mock(RoleTeamService.class));
        when(plugin.fakeSpectators()).thenReturn(mock(FakeSpectatorService.class));
        PlayerStateStore players = new PlayerStateStore();
        MatchStore store = mock(MatchStore.class);
        when(store.activeHunterCount(any())).thenReturn(1);
        when(store.activeRunnerCount(any())).thenReturn(2);
        MatchMessaging messaging = mock(MatchMessaging.class);
        LobbyService lobbies = mock(LobbyService.class);
        @SuppressWarnings("unchecked")
        Consumer<GameInstance> afterLeave = mock(Consumer.class);
        MatchLeaveService leaves = new MatchLeaveService(
                new MatchLeaveService.LeaveReads(match,
                        new JManhuntConfig().getWorldEngine(), plugin.fakeSpectators(),
                        plugin.roleTeams(), lobbies),
                new MatchLeaveService.LeaveMatch(players, mock(CompassManager.class),
                        mock(GameStateCommandManager.class), mock(WorldEngineService.class),
                        store, mock(FlagStore.class), afterLeave),
                mock(MessageService.class), texts(), messaging);
        GameInstance instance = mock(GameInstance.class);
        when(instance.active()).thenReturn(true);
        when(instance.begun()).thenReturn(false);
        when(instance.matchId()).thenReturn(7L);
        when(instance.originLobbyId()).thenReturn(0);
        when(instance.cellIndex()).thenReturn(OptionalLong.empty());
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Alex");
        when(instance.isActive(id)).thenReturn(true);
        players.setRole(player, role);
        return new Fixture(leaves, messaging, instance, player, lobbies);
    }

    @Test
    void hunterLeaveAnnouncesToMatchOnly() {
        Fixture fixture = fixture(Role.HUNTER, "SPECTATOR");

        assertEquals(1, fixture.leaves().leaveMatch(fixture.instance(),
                List.of(fixture.player()), false));

        verify(fixture.messaging()).sendToInstance(fixture.instance(), "hunter left tpl",
                Map.of("player", "Alex", "remaining", "1"));
        verify(fixture.messaging(), never()).sendToLobby(anyInt(), anyString(), any());
    }

    @Test
    void speedrunnerLeaveAnnouncesToMatchOnly() {
        Fixture fixture = fixture(Role.SPEEDRUNNER, "SPECTATOR");

        assertEquals(1, fixture.leaves().leaveMatch(fixture.instance(),
                List.of(fixture.player()), false));

        verify(fixture.messaging()).sendToInstance(fixture.instance(), "runner left tpl",
                Map.of("player", "Alex", "remaining", "2"));
        verify(fixture.messaging(), never()).sendToLobby(anyInt(), anyString(), any());
    }

    @Test
    void lobbyLeaveAppliesLobbyCollisions() {
        Fixture fixture = fixture(Role.HUNTER, "LOBBY");

        assertEquals(1, fixture.leaves().leaveMatch(fixture.instance(),
                List.of(fixture.player()), false));

        verify(fixture.lobbies()).applyLobbyCollisions(fixture.player());
    }

    @Test
    void spectatorLeaveSkipsLobbyCollisions() {
        Fixture fixture = fixture(Role.HUNTER, "SPECTATOR");

        assertEquals(1, fixture.leaves().leaveMatch(fixture.instance(),
                List.of(fixture.player()), false));

        verify(fixture.lobbies(), never()).applyLobbyCollisions(any());
    }
}
