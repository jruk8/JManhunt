package com.jruk8.jmanhunt.match.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.MatchConfig;
import com.jruk8.jmanhunt.config.PlayerSettings;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import com.jruk8.jmanhunt.world.WorldEngineService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class PlayerConnectionListenerAbandonTest {

    private record Fixture(PlayerConnectionListener listener, Player quitter, Player other,
            GameManager game, GameInstance instance, UUID quitterId, UUID otherId) {
    }

    private static Fixture fixture() {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.roleTeams()).thenReturn(mock(RoleTeamService.class));
        PlayerStateStore players = new PlayerStateStore();
        GameManager game = mock(GameManager.class);
        LobbyService lobbies = mock(LobbyService.class);
        when(lobbies.lobbyOf(any(UUID.class))).thenReturn(Optional.empty());
        when(lobbies.multiLobbyAllowed()).thenReturn(false);
        when(game.instanceForLobby(anyInt())).thenReturn(Optional.empty());
        GameInstance instance = mock(GameInstance.class);
        when(instance.active()).thenReturn(true);
        when(instance.matchId()).thenReturn(11L);
        Player quitter = mock(Player.class);
        UUID quitterId = UUID.randomUUID();
        when(quitter.getUniqueId()).thenReturn(quitterId);
        Player other = mock(Player.class);
        UUID otherId = UUID.randomUUID();
        when(other.getUniqueId()).thenReturn(otherId);
        when(instance.assignedPlayerIds()).thenReturn(Set.of(quitterId, otherId));
        // Spectators skip disconnect handling, isolating the abandon check.
        players.setRole(quitterId, Role.SPECTATOR);
        when(game.instanceOf(quitterId)).thenReturn(Optional.of(instance));
        Map<UUID, BukkitTask> disconnectTasks = new HashMap<>();
        PlayerSettings settings = new PlayerSettings();
        PlayerConnectionListener listener = new PlayerConnectionListener(
                new PlayerConnectionListener.ConnectReads(players,
                        mock(FakeSpectatorService.class), mock(MessageService.class),
                        new GameMessages()),
                new PlayerConnectionListener.ConnectMatch(game, lobbies,
                        mock(CompassManager.class),
                        mock(SpeedrunnerDisconnectTracker.class), disconnectTasks),
                new PlayerConnectionListener.ConnectWorld(mock(LobbyTeleporter.class),
                        mock(WorldEngineService.class)),
                new PlayerConnectionListener.ConnectConfig(settings,
                        new MatchConfig.DisconnectHandling()),
                new PlayerConnectionListener.ConnectEdge(plugin.roleTeams(),
                        mock(TaskScheduler.class), mock(PlayerRespawnListener.class)));
        return new Fixture(listener, quitter, other, game, instance, quitterId, otherId);
    }

    @Test
    void lastActiveQuitterCancelsImmediately() {
        Fixture fixture = fixture();
        // The quitter still counts as online during the quit event.
        when(fixture.game().onlineActivePlayers(fixture.instance()))
                .thenReturn(List.of(fixture.quitter()));

        fixture.listener().onQuit(new PlayerQuitEvent(fixture.quitter(), "quit"));

        verify(fixture.game()).cancel(fixture.instance(), true);
    }

    @Test
    void othersOnlineKeepsMatch() {
        Fixture fixture = fixture();
        when(fixture.game().onlineActivePlayers(fixture.instance()))
                .thenReturn(List.of(fixture.quitter(), fixture.other()));

        fixture.listener().onQuit(new PlayerQuitEvent(fixture.quitter(), "quit"));

        verify(fixture.game(), never()).cancel(fixture.instance(), true);
        verify(fixture.game(), never()).cancel(fixture.instance());
    }

    @Test
    void inactiveMatchSkipsCancel() {
        Fixture fixture = fixture();
        when(fixture.instance().active()).thenReturn(false);
        when(fixture.game().onlineActivePlayers(fixture.instance())).thenReturn(List.of());

        fixture.listener().onQuit(new PlayerQuitEvent(fixture.quitter(), "quit"));

        verify(fixture.game(), never()).cancel(fixture.instance(), true);
    }
}
