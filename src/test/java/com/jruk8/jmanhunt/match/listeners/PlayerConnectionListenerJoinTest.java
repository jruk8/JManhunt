package com.jruk8.jmanhunt.match.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import com.jruk8.jmanhunt.world.WorldEngineService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class PlayerConnectionListenerJoinTest {

    @Test
    void joinAppliesPendingEndWipe() {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.roleTeams()).thenReturn(mock(RoleTeamService.class));
        GameManager game = mock(GameManager.class);
        LobbyService lobbies = mock(LobbyService.class);
        when(lobbies.lobbyOf(any(UUID.class))).thenReturn(Optional.empty());
        when(lobbies.multiLobbyAllowed()).thenReturn(false);
        when(game.instanceForLobby(anyInt())).thenReturn(Optional.empty());
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(game.instanceOf(playerId)).thenReturn(Optional.empty());
        Map<UUID, BukkitTask> disconnectTasks = new HashMap<>();
        PlayerConnectionListener listener = new PlayerConnectionListener(plugin,
                new PlayerStateStore(), game, mock(MessageService.class),
                mock(ConfigService.class), lobbies, mock(LobbyTeleporter.class),
                mock(WorldEngineService.class), mock(SpeedrunnerDisconnectTracker.class),
                disconnectTasks, mock(CompassManager.class));

        listener.onJoin(new PlayerJoinEvent(player, "join"));

        verify(game).applyPendingEndWipe(player);
    }

    private record JoinFixture(PlayerConnectionListener listener, Player player, UUID playerId,
            PlayerStateStore players, GameManager game, ConfigService config,
            FakeSpectatorService fakes) {
    }

    /** Joiner whose lobby runs a match they are not part of. */
    private static JoinFixture joinFixture(Role role) {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.roleTeams()).thenReturn(mock(RoleTeamService.class));
        FakeSpectatorService fakes = mock(FakeSpectatorService.class);
        when(plugin.fakeSpectators()).thenReturn(fakes);
        GameManager game = mock(GameManager.class);
        PlayerStateStore players = new PlayerStateStore();
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        players.setRole(player, role);
        when(game.instanceOf(playerId)).thenReturn(Optional.empty());
        LobbyService lobbies = mock(LobbyService.class);
        Lobby lobby = mock(Lobby.class);
        when(lobby.id()).thenReturn(0);
        when(lobbies.lobbyOf(any(UUID.class))).thenReturn(Optional.of(lobby));
        when(lobbies.multiLobbyAllowed()).thenReturn(false);
        when(game.instanceForLobby(anyInt())).thenReturn(Optional.of(mock(GameInstance.class)));
        when(game.hasLobbyLocation(anyInt())).thenReturn(true);
        ConfigService config = mock(ConfigService.class);
        PlayerConnectionListener listener = new PlayerConnectionListener(plugin, players, game,
                mock(MessageService.class), config, lobbies, mock(LobbyTeleporter.class),
                mock(WorldEngineService.class), mock(SpeedrunnerDisconnectTracker.class),
                new HashMap<>(), mock(CompassManager.class));
        return new JoinFixture(listener, player, playerId, players, game, config, fakes);
    }

    private static void join(JoinFixture fixture) {
        fixture.listener().onJoin(new PlayerJoinEvent(fixture.player(), "join"));
    }

    @Test
    void heldHunterKeepsRoleDuringMatch() {
        JoinFixture fixture = joinFixture(Role.HUNTER);

        join(fixture);

        assertEquals(Role.HUNTER, fixture.players().role(fixture.playerId()));
    }

    @Test
    void queuedSpectatorKeepsRoleDuringMatch() {
        JoinFixture fixture = joinFixture(Role.SPECTATOR);

        join(fixture);

        assertEquals(Role.SPECTATOR, fixture.players().role(fixture.playerId()));
        verify(fixture.fakes(), never()).enable(any(Player.class));
    }

    @Test
    void noneStaysNoneWithoutFakeMode() {
        JoinFixture fixture = joinFixture(Role.NONE);

        join(fixture);

        assertEquals(Role.NONE, fixture.players().role(fixture.playerId()));
        verify(fixture.fakes(), never()).enable(any(Player.class));
    }

    @Test
    void noneTakesFakeModeWithToggle() {
        JoinFixture fixture = joinFixture(Role.NONE);
        when(fixture.config().getBoolean(anyString(), anyBoolean())).thenReturn(true);

        join(fixture);

        verify(fixture.fakes()).enable(fixture.player());
    }

    @Test
    void afkSkipsNewestMatchFallbackWithoutLobby() {
        JoinFixture fixture = joinFixture(Role.AFK);
        when(fixture.game().hasLobbyLocation(anyInt())).thenReturn(false);

        join(fixture);

        assertEquals(Role.AFK, fixture.players().role(fixture.playerId()));
        verify(fixture.game(), never()).joinLeastTimeMatch(any(Player.class));
    }

    @Test
    void newcomerWithoutLobbyJoinsNewestMatch() {
        JoinFixture fixture = joinFixture(Role.NONE);
        when(fixture.game().hasLobbyLocation(anyInt())).thenReturn(false);

        join(fixture);

        verify(fixture.game()).joinLeastTimeMatch(fixture.player());
    }
}
