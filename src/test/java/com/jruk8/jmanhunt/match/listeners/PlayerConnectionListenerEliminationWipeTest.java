package com.jruk8.jmanhunt.match.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.MatchConfig;
import com.jruk8.jmanhunt.config.PlayerSettings;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.match.MaxHealthService;
import com.jruk8.jmanhunt.match.lifecycle.MatchMessaging;
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
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PlayerConnectionListenerEliminationWipeTest {

    private record Fixture(PlayerConnectionListener listener, PlayerStateStore states,
            GameStateCommandManager stateCommands, Player quitter, UUID quitterId) {
    }

    private static Fixture fixture() {
        PlayerStateStore players = new PlayerStateStore();
        GameManager game = mock(GameManager.class);
        when(game.maxHealth()).thenReturn(mock(MaxHealthService.class));
        GameStateCommandManager stateCommands = mock(GameStateCommandManager.class);
        when(game.stateCommands()).thenReturn(stateCommands);
        when(stateCommands.endWipeEnabled(anyInt())).thenReturn(true);
        when(game.flagStore()).thenReturn(mock(FlagStore.class));
        when(game.messaging()).thenReturn(mock(MatchMessaging.class));
        LobbyService lobbies = mock(LobbyService.class);
        when(lobbies.lobbyOf(any(UUID.class))).thenReturn(Optional.empty());
        when(game.instanceForLobby(anyInt())).thenReturn(Optional.empty());
        GameInstance instance = mock(GameInstance.class);
        when(instance.active()).thenReturn(true);
        when(instance.matchId()).thenReturn(11L);
        when(instance.originLobbyId()).thenReturn(0);
        Player quitter = mock(Player.class);
        UUID quitterId = UUID.randomUUID();
        when(quitter.getUniqueId()).thenReturn(quitterId);
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        players.setRole(quitterId, Role.HUNTER);
        when(game.instanceOf(quitterId)).thenReturn(Optional.of(instance));
        when(game.instance(anyLong())).thenReturn(Optional.of(instance));
        when(instance.isActive(quitterId)).thenReturn(true);
        // Someone else stays online, isolating elimination from the abandon cancel.
        when(game.onlineActivePlayers(instance)).thenReturn(List.of(quitter, other));
        PlayerSettings settings = new PlayerSettings();
        PlayerConnectionListener listener = new PlayerConnectionListener(
                new PlayerConnectionListener.ConnectReads(players,
                        mock(FakeSpectatorService.class), mock(MessageService.class),
                        new GameMessages()),
                new PlayerConnectionListener.ConnectMatch(game, lobbies,
                        mock(CompassManager.class),
                        mock(SpeedrunnerDisconnectTracker.class), new HashMap<UUID, BukkitTask>()),
                new PlayerConnectionListener.ConnectWorld(mock(LobbyTeleporter.class),
                        mock(WorldEngineService.class)),
                new PlayerConnectionListener.ConnectConfig(settings,
                        new MatchConfig.DisconnectHandling()),
                new PlayerConnectionListener.ConnectEdge(mock(RoleTeamService.class),
                        mock(TaskScheduler.class), mock(PlayerRespawnListener.class)));
        return new Fixture(listener, players, stateCommands, quitter, quitterId);
    }

    @Test
    void preStartQuitMarksEndWipeForEliminated() {
        Fixture fixture = fixture();
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.getName()).thenReturn("Quitter");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer(fixture.quitterId())).thenReturn(offline);
            bukkit.when(() -> Bukkit.getPlayer(fixture.quitterId())).thenReturn(null);

            fixture.listener().onQuit(new PlayerQuitEvent(fixture.quitter(), "quit"));
        }

        assertEquals(Role.NONE, fixture.states().role(fixture.quitterId()));
        verify(fixture.stateCommands())
                .markPendingEndWipe(argThat(ids -> ids.contains(fixture.quitterId())));
    }
}
