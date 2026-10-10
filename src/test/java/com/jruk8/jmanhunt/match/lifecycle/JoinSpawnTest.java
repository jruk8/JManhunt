package com.jruk8.jmanhunt.match.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.JoinTiming;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.config.MatchSettingsFacade;
import com.jruk8.jmanhunt.lobby.config.PlayersSettingsFacade;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.match.listeners.PlayerRespawnListener;
import com.jruk8.jmanhunt.match.autostart.AutostartService;
import com.jruk8.jmanhunt.match.prestart.HeadstartState;
import com.jruk8.jmanhunt.match.prestart.PrestartService;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.stats.Stats;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.WorldEngineService;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/** Cell-less joins scatter around the start center; entrants arrive vulnerable. */
class JoinSpawnTest {

    private record Fixture(MatchStartService starts, GameInstance instance, PlayerStateStore players,
            Player player, World world, Location center, GameStateCommandManager commands) {
    }

    private static GameMessages texts() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "game.join-announce", "join tpl");
        return config.getGame();
    }

    private static Fixture fixture() {
        return fixture(() -> null);
    }

    private static Fixture fixture(Supplier<PlayerRespawnListener> respawn) {
        PlayerStateStore players = new PlayerStateStore();
        LobbyService lobbies = mock(LobbyService.class);
        when(lobbies.joinTiming()).thenReturn(JoinTiming.INSTANT);
        when(lobbies.lobbyOf(any())).thenReturn(Optional.empty());
        World world = mock(World.class);
        Location center = new Location(world, 100.5, 64.0, 200.5);
        GameInstance instance = mock(GameInstance.class);
        when(instance.active()).thenReturn(true);
        when(instance.ending()).thenReturn(false);
        when(instance.matchId()).thenReturn(7L);
        when(instance.originLobbyId()).thenReturn(0);
        when(instance.cellIndex()).thenReturn(OptionalLong.empty());
        when(instance.startCenter()).thenReturn(center);
        when(instance.assignedPlayerIds()).thenReturn(Set.of());
        when(instance.headstart(any())).thenReturn(new HeadstartState());
        UUID playerId = UUID.randomUUID();
        when(instance.isActive(playerId)).thenReturn(false);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Alex");
        when(player.getLocation()).thenReturn(new Location(world, 0.5, 64.0, 0.5));
        StatsManager stats = mock(StatsManager.class);
        when(stats.getOrCreate(anyLong(), any())).thenReturn(new Stats());
        MessageService messages = mock(MessageService.class);
        when(messages.roleName(any())).thenReturn("hunter");
        com.jruk8.jmanhunt.config.WorldEngineConfig engineSettings =
                new JManhuntConfig().getWorldEngine();
        engineSettings.getSpawnpointAlgorithm().setEnabled(false);
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        MatchStartService starts = new MatchStartService(
                new MatchStartWiring.StartReads(mock(MatchSettingsFacade.class),
                        mock(PlayersSettingsFacade.class), mock(ConfigService.class),
                        engineSettings, mock(JManhuntLogger.class)),
                new MatchStartWiring.StartMatch(players, mock(CompassManager.class), stats,
                        commands, mock(WorldEngineService.class),
                        lobbies, mock(MatchStore.class), mock(TimeLimitService.class),
                        mock(PrestartService.class), mock(AutostartService.class)),
                new MatchStartWiring.StartEdge(mock(FakeSpectatorService.class),
                        mock(RoleTeamService.class), respawn, mock(SoundService.class),
                        mock(TaskScheduler.class)),
                new MatchStartWiring.StartTexts(messages, texts(), new ManhuntMessages(),
                        mock(MatchMessaging.class)));
        return new Fixture(starts, instance, players, player, world, center, commands);
    }

    @Test
    void engineOffJoinScattersAroundStartCenter() {
        Fixture fixture = fixture();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            assertEquals(1, fixture.starts().joinPlayers(fixture.instance(),
                    List.of(fixture.player()), Role.HUNTER));
        }

        ArgumentCaptor<Location> spawn = ArgumentCaptor.forClass(Location.class);
        verify(fixture.player()).teleport(spawn.capture());
        assertEquals(fixture.world(), spawn.getValue().getWorld());
        assertTrue(Math.abs(spawn.getValue().getX() - 100.5) <= 5.0);
        assertTrue(Math.abs(spawn.getValue().getZ() - 200.5) <= 5.0);
        verify(fixture.player()).setRespawnLocation(fixture.center(), true);
        verify(fixture.player()).setInvulnerable(false);
    }

    @Test
    void engineOffWatcherJoinFallsBackToStartCenter() {
        Fixture fixture = fixture();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            assertEquals(1, fixture.starts().joinPlayers(fixture.instance(),
                    List.of(fixture.player()), Role.SPECTATOR));
        }

        verify(fixture.player()).teleport(fixture.center());
        verify(fixture.player()).setRespawnLocation(fixture.center(), true);
    }

    @Test
    void switchIntoGameClearsInvulnerability() {
        Fixture fixture = fixture();
        fixture.players().setRole(fixture.player(), Role.SPECTATOR);

        assertTrue(fixture.starts().switchPlayerRole(fixture.instance(), fixture.player(),
                Role.HUNTER));

        verify(fixture.player()).setInvulnerable(false);
    }

    @Test
    void switchDuringEndDelayKeepsInvulnerability() {
        Fixture fixture = fixture();
        fixture.players().setRole(fixture.player(), Role.SPECTATOR);
        when(fixture.instance().ending()).thenReturn(true);

        assertTrue(fixture.starts().switchPlayerRole(fixture.instance(), fixture.player(),
                Role.HUNTER));

        verify(fixture.player(), never()).setInvulnerable(false);
    }

    @Test
    void switchIntoGameScattersToStartCenter() {
        Fixture fixture = fixture();
        fixture.players().setRole(fixture.player(), Role.SPECTATOR);

        assertTrue(fixture.starts().switchPlayerRole(fixture.instance(), fixture.player(),
                Role.HUNTER));

        verify(fixture.player(), atLeastOnce()).teleport(argThat((Location spawn) ->
                spawn != null && spawn.getWorld() == fixture.world()
                        && Math.abs(spawn.getX() - 100.5) <= 5.0
                        && Math.abs(spawn.getZ() - 200.5) <= 5.0));
        verify(fixture.player()).setRespawnLocation(fixture.center(), true);
    }

    @Test
    void switchIntoGameWhileDeadKeepsPosition() {
        Fixture fixture = fixture();
        fixture.players().setRole(fixture.player(), Role.SPECTATOR);
        when(fixture.player().isDead()).thenReturn(true);

        assertTrue(fixture.starts().switchPlayerRole(fixture.instance(), fixture.player(),
                Role.HUNTER));

        verify(fixture.player(), never()).teleport(any(Location.class));
    }

    @Test
    void switchWithoutHoldsFiresBothCatchups() {
        Fixture fixture = fixture();
        fixture.players().setRole(fixture.player(), Role.SPECTATOR);
        when(fixture.instance().markStartFired(any())).thenReturn(true);
        when(fixture.instance().markRespawnFired(any(), anyInt())).thenReturn(true);

        assertTrue(fixture.starts().switchPlayerRole(fixture.instance(), fixture.player(),
                Role.HUNTER));

        verify(fixture.commands()).runStartForPlayer(7L, fixture.player());
        verify(fixture.commands()).runRespawnForPlayer(7L, fixture.player());
    }

    @Test
    void switchWhileHeadstartHeldDefersRespawnCatchup() {
        Fixture fixture = fixture();
        fixture.players().setRole(fixture.player(), Role.SPECTATOR);
        when(fixture.instance().isHeadstartHeld(any())).thenReturn(true);
        when(fixture.instance().markStartFired(any())).thenReturn(true);
        when(fixture.instance().markRespawnFired(any(), anyInt())).thenReturn(true);

        assertTrue(fixture.starts().switchPlayerRole(fixture.instance(), fixture.player(),
                Role.HUNTER));

        verify(fixture.commands()).runStartForPlayer(7L, fixture.player());
        verify(fixture.commands(), never()).runRespawnForPlayer(anyLong(),
                eq(fixture.player()));
    }

    @Test
    void switchWhileJoinHoldRunsDefersRespawnCatchup() {
        PlayerRespawnListener respawn = mock(PlayerRespawnListener.class);
        when(respawn.hasPendingRespawn(any())).thenReturn(true);
        Fixture fixture = fixture(() -> respawn);
        fixture.players().setRole(fixture.player(), Role.SPECTATOR);
        when(fixture.instance().markStartFired(any())).thenReturn(true);
        when(fixture.instance().markRespawnFired(any(), anyInt())).thenReturn(true);

        assertTrue(fixture.starts().switchPlayerRole(fixture.instance(), fixture.player(),
                Role.HUNTER));

        verify(fixture.commands()).runStartForPlayer(7L, fixture.player());
        verify(fixture.commands(), never()).runRespawnForPlayer(anyLong(),
                eq(fixture.player()));
    }
}
