package com.jruk8.jmanhunt.match.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.command.TagCooldownStore;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.config.WorldEngineConfig;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.config.MatchSettingsFacade;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.lobby.config.PlayersSettingsFacade;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.match.autostart.AutostartService;
import com.jruk8.jmanhunt.match.prestart.PrestartService;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpawnCampService;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.WorldEngineService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MatchFinishServiceTest {

    private static World world(World.Environment environment, int top, int maxHeight) {
        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(environment);
        when(world.getHighestBlockYAt(10, 20)).thenReturn(top);
        when(world.getMaxHeight()).thenReturn(maxHeight);
        return world;
    }

    @Test
    void recoveryUsesSurfacePlusOneOutsideNether() {
        assertEquals(65.0, MatchBorderEnforcer.recoveryY(
                world(World.Environment.NORMAL, 64, 320), 10.4, 20.7, 11.0), 0.0);
        assertEquals(65.0, MatchBorderEnforcer.recoveryY(
                world(World.Environment.THE_END, 64, 320), 10.4, 20.7, 11.0), 0.0);
    }

    @Test
    void recoveryKeepsLiveYInNether() {
        assertEquals(11.0, MatchBorderEnforcer.recoveryY(
                world(World.Environment.NETHER, 120, 320), 10.4, 20.7, 11.0), 0.0);
    }

    @Test
    void recoveryCapsBelowCeiling() {
        assertEquals(318.0, MatchBorderEnforcer.recoveryY(
                world(World.Environment.NORMAL, 400, 320), 10.4, 20.7, 11.0), 0.0);
    }

    private record Fixture(MatchFinishService finish, PlayerStateStore states,
            GameStateCommandManager stateCommands, GameInstance instance,
            List<Runnable> deferred, UUID hunterId, UUID spectatorId) {
    }

    private static Fixture fixture() {
        PlayerStateStore players = new PlayerStateStore();
        UUID hunterId = UUID.randomUUID();
        UUID spectatorId = UUID.randomUUID();
        players.setRole(hunterId, Role.HUNTER);
        players.setRole(spectatorId, Role.SPECTATOR);
        GameInstance instance = mock(GameInstance.class);
        when(instance.active()).thenReturn(true);
        when(instance.matchId()).thenReturn(11L);
        when(instance.originLobbyId()).thenReturn(0);
        when(instance.cellIndex()).thenReturn(OptionalLong.empty());
        when(instance.assignedPlayerIds()).thenReturn(Set.of(hunterId, spectatorId));
        GameStateCommandManager stateCommands = mock(GameStateCommandManager.class);
        when(stateCommands.endWipeEnabled(anyInt())).thenReturn(true);
        MatchStore store = mock(MatchStore.class);
        when(store.onlineAssignedPlayers(instance)).thenReturn(List.of());
        when(store.onlineActivePlayers(instance)).thenReturn(List.of());
        when(store.instances()).thenReturn(Map.of(11L, instance));
        List<Runnable> deferred = new ArrayList<>();
        TaskScheduler tasks = mock(TaskScheduler.class);
        when(tasks.runLater(any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            deferred.add(invocation.getArgument(0));
            return mock(BukkitTask.class);
        });
        MatchFinishService finish = new MatchFinishService(
                new MatchFinishService.FinishReads(mock(PlayersSettingsFacade.class),
                        mock(MatchSettingsFacade.class), mock(WorldEngineConfig.class),
                        mock(OverrideService.class), mock(JManhuntLogger.class)),
                new MatchFinishService.FinishMatch(players, mock(CompassManager.class),
                        mock(StatsManager.class), stateCommands, mock(WorldEngineService.class),
                        store, mock(TimeLimitService.class), mock(PrestartService.class),
                        mock(AutostartService.class), mock(FlagStore.class),
                        mock(TagCooldownStore.class)),
                new MatchFinishService.FinishEdge(mock(FakeSpectatorService.class),
                        mock(RoleTeamService.class), mock(SpawnCampService.class), tasks,
                        JManhuntConfig::new),
                new MatchFinishService.FinishTexts(mock(MessageService.class), new GameMessages(),
                        mock(MatchMessaging.class)));
        return new Fixture(finish, players, stateCommands, instance, deferred, hunterId, spectatorId);
    }

    @Test
    void cancelMarksOfflineParticipantsBeforeRoleReset() {
        Fixture fixture = fixture();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

            fixture.finish().cancel(fixture.instance(), true);
            fixture.deferred().forEach(Runnable::run);
        }

        assertEquals(Role.NONE, fixture.states().role(fixture.hunterId()));
        assertEquals(Role.NONE, fixture.states().role(fixture.spectatorId()));
        verify(fixture.stateCommands(), atLeastOnce())
                .markPendingEndWipe(argThat(ids -> ids.contains(fixture.hunterId())));
        verify(fixture.stateCommands(), never())
                .markPendingEndWipe(argThat(ids -> ids.contains(fixture.spectatorId())));
    }
}
