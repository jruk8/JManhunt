package com.jruk8.jmanhunt.match;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.config.MiscConfig;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.lobby.config.PlayersSettingsFacade;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Per-player catch-up runs only for live matches and live players. */
class GameStateCommandCatchupTest {

    private record Fixture(GameStateCommandManager manager, ConfigService config,
            GameManager game, GameInstance instance, PlayerStateStore states, Player player) {
    }

    private static Fixture fixture() {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        OverrideService overrides = mock(OverrideService.class);
        when(plugin.overrides()).thenReturn(overrides);
        when(overrides.modifierEnabled(any(), eq("herald"))).thenReturn(true);
        ConfigService config = mock(ConfigService.class);
        when(config.modifierNames()).thenReturn(Set.of("herald"));
        when(config.behaviorIndexes("herald")).thenReturn(List.of(0));
        when(config.runsOn("herald", 0)).thenReturn(List.of("INTERVAL"));
        PlayerStateStore states = new PlayerStateStore();
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        states.setRole(player, Role.HUNTER);
        GameManager game = mock(GameManager.class);
        GameInstance instance = mock(GameInstance.class);
        when(instance.matchId()).thenReturn(7L);
        when(instance.active()).thenReturn(true);
        when(instance.ending()).thenReturn(false);
        when(instance.isActive(playerId)).thenReturn(true);
        when(game.instance(7L)).thenReturn(Optional.of(instance));
        GameStateCommandManager manager = new GameStateCommandManager(
                new GameStateCommandManager.CommandReads(states, config,
                        new MiscConfig.Interop(), mock(PlayersSettingsFacade.class)),
                new GameStateCommandManager.CommandEdge(
                        mock(EngineStateRepository.class), plugin.fakeSpectators(),
                        plugin.logger(), overrides, plugin.placeholderValues(),
                        mock(TaskScheduler.class)),
                mock(MessageService.class), mock(SoundService.class), game);
        return new Fixture(manager, config, game, instance, states, player);
    }

    @Test
    void respawnCatchupRunsWhenLive() {
        Fixture fixture = fixture();

        fixture.manager().runRespawnForPlayer(7L, fixture.player());

        verify(fixture.config()).behaviorIndexes("herald");
    }

    @Test
    void respawnCatchupSkipsEndingOrMissingMatches() {
        Fixture ending = fixture();
        when(ending.instance().ending()).thenReturn(true);

        ending.manager().runRespawnForPlayer(7L, ending.player());

        verify(ending.config(), never()).behaviorIndexes(anyString());

        Fixture missing = fixture();
        when(missing.game().instance(7L)).thenReturn(Optional.empty());

        missing.manager().runRespawnForPlayer(7L, missing.player());

        verify(missing.config(), never()).behaviorIndexes(anyString());
    }

    @Test
    void respawnCatchupSkipsInactiveOrEliminatedPlayers() {
        Fixture idle = fixture();
        when(idle.instance().isActive(any())).thenReturn(false);

        idle.manager().runRespawnForPlayer(7L, idle.player());

        verify(idle.config(), never()).behaviorIndexes(anyString());

        Fixture out = fixture();
        out.states().setRole(out.player(), Role.SPECTATOR);

        out.manager().runRespawnForPlayer(7L, out.player());

        verify(out.config(), never()).behaviorIndexes(anyString());
    }

    @Test
    void startCatchupSkipsDeadMatches() {
        Fixture live = fixture();

        live.manager().runStartForPlayer(7L, live.player());

        verify(live.config()).behaviorIndexes("herald");

        Fixture ending = fixture();
        when(ending.instance().ending()).thenReturn(true);

        ending.manager().runStartForPlayer(7L, ending.player());

        verify(ending.config(), never()).behaviorIndexes(anyString());
    }
}
