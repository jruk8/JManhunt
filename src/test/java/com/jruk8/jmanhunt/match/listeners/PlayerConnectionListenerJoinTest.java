package com.jruk8.jmanhunt.match.listeners;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import com.jruk8.jmanhunt.player.PlayerStateStore;
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
}
