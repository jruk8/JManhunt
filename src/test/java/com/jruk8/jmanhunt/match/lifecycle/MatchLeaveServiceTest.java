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
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.message.MessageService;
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
            Player player) {
    }

    private static Fixture fixture(Role role) {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        OverrideService overrides = mock(OverrideService.class);
        when(overrides.getString(any(), anyString(), anyString())).thenReturn("SPECTATOR");
        when(plugin.overrides()).thenReturn(overrides);
        when(plugin.roleTeams()).thenReturn(mock(RoleTeamService.class));
        when(plugin.fakeSpectators()).thenReturn(mock(FakeSpectatorService.class));
        PlayerStateStore players = new PlayerStateStore();
        MatchStore store = mock(MatchStore.class);
        when(store.activeHunterCount(any())).thenReturn(1);
        when(store.activeRunnerCount(any())).thenReturn(2);
        MatchMessaging messaging = mock(MatchMessaging.class);
        @SuppressWarnings("unchecked")
        Consumer<GameInstance> afterLeave = mock(Consumer.class);
        MatchLeaveService leaves = new MatchLeaveService(plugin, mock(MessageService.class),
                players, mock(CompassManager.class), mock(GameStateCommandManager.class),
                mock(ConfigService.class), mock(WorldEngineService.class), store, messaging,
                mock(FlagStore.class), afterLeave);
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
        return new Fixture(leaves, messaging, instance, player);
    }

    @Test
    void hunterLeaveAnnouncesToMatchOnly() {
        Fixture fixture = fixture(Role.HUNTER);

        assertEquals(1, fixture.leaves().leaveMatch(fixture.instance(),
                List.of(fixture.player()), false));

        verify(fixture.messaging()).sendToInstance(fixture.instance(), "game.hunter-left",
                Map.of("player", "Alex", "remaining", "1"));
        verify(fixture.messaging(), never()).sendToLobby(anyInt(), anyString(), any());
    }

    @Test
    void speedrunnerLeaveAnnouncesToMatchOnly() {
        Fixture fixture = fixture(Role.SPEEDRUNNER);

        assertEquals(1, fixture.leaves().leaveMatch(fixture.instance(),
                List.of(fixture.player()), false));

        verify(fixture.messaging()).sendToInstance(fixture.instance(), "game.speedrunner-left",
                Map.of("player", "Alex", "remaining", "2"));
        verify(fixture.messaging(), never()).sendToLobby(anyInt(), anyString(), any());
    }
}
