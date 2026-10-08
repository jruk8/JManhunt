package com.jruk8.jmanhunt.match.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.PlayerSettings;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.match.WinConditionEngine;
import com.jruk8.jmanhunt.match.lifecycle.MatchMessaging;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import com.jruk8.jmanhunt.player.SpawnCampService;
import com.jruk8.jmanhunt.stats.Stats;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.WorldEngineService;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PlayerCombatListenerDeathTest {

    private record Fixture(PlayerCombatListener listener, Player victim, UUID victimId,
            PlayerStateStore players, GameInstance instance, CompassManager compass,
            FakeSpectatorService fakes, PlayerRespawnListener respawn,
            GameStateCommandManager commands) {
    }

    private static TaskScheduler immediateTasks() {
        TaskScheduler tasks = mock(TaskScheduler.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(tasks).run(any(Runnable.class));
        return tasks;
    }

    private static Fixture fixture(Role role, int lives, boolean begun) {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.spawnCamp()).thenReturn(mock(SpawnCampService.class));
        when(plugin.roleTeams()).thenReturn(mock(RoleTeamService.class));
        FakeSpectatorService fakes = mock(FakeSpectatorService.class);
        when(plugin.fakeSpectators()).thenReturn(fakes);
        PlayerStateStore players = new PlayerStateStore();
        GameManager game = mock(GameManager.class);
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        when(game.stateCommands()).thenReturn(commands);
        when(game.flagStore()).thenReturn(mock(FlagStore.class));
        when(game.messaging()).thenReturn(mock(MatchMessaging.class));
        StatsManager stats = mock(StatsManager.class);
        when(stats.getOrCreate(anyLong(), any(UUID.class))).thenAnswer(invocation -> new Stats());
        CompassManager compass = mock(CompassManager.class);
        PlayerRespawnListener respawn = mock(PlayerRespawnListener.class);
        GameInstance instance = new GameInstance(7L, 0, OptionalLong.empty(), 1_000L);
        instance.setBegun(begun);
        Player victim = mock(Player.class);
        UUID victimId = UUID.randomUUID();
        when(victim.getUniqueId()).thenReturn(victimId);
        when(victim.getName()).thenReturn("Victor");
        when(victim.getLocation()).thenReturn(mock(Location.class));
        players.setRole(victim, role);
        players.setLives(victimId, lives);
        if (role == Role.SPEEDRUNNER) {
            players.setSpeedrunnerAlive(victimId, true);
        }
        instance.activate(victimId);
        when(game.instanceOf(victimId)).thenReturn(Optional.of(instance));
        PlayerSettings settings = new PlayerSettings();
        settings.getRespawn().getHunter().setEnabled(false);
        PlayerCombatListener listener = new PlayerCombatListener(
                new PlayerCombatListener.CombatReads(players, fakes, settings, texts()),
                new PlayerCombatListener.CombatMatch(game, stats,
                        mock(WinConditionEngine.class),
                        mock(SpeedrunnerDisconnectTracker.class), new HashMap<>()),
                new PlayerCombatListener.CombatWorld(compass, mock(LobbyService.class),
                        mock(WorldEngineService.class), respawn),
                new PlayerCombatListener.CombatEdge(plugin.spawnCamp(), plugin.roleTeams(),
                        mock(JManhuntLogger.class), mock(LobbyConfig.class)),
                immediateTasks());
        return new Fixture(listener, victim, victimId, players, instance, compass, fakes,
                respawn, commands);
    }

    private static GameMessages texts() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "game.speedrunner-respawn-scheduled", "respawn tpl");
        return config.getGame();
    }

    /** Runs the death with an immediately executing scheduler. */
    private static void kill(Fixture fixture) {
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(fixture.victim());
        fixture.listener().onDeath(event);
    }

    @Test
    void deathFiresOnDeathWithNullKiller() {
        Fixture fixture = fixture(Role.HUNTER, -1, true);

        kill(fixture);

        verify(fixture.commands()).runEventModifiers(eq("ON_DEATH"), eq(fixture.victim()),
                eq(7L), eq(List.of("Victor", "null", "HUNTER")));
    }

    @Test
    void deathFiresOnDeathWithKillerName() {
        Fixture fixture = fixture(Role.SPEEDRUNNER, -1, true);
        Player killer = mock(Player.class);
        when(killer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(killer.getName()).thenReturn("Kira");
        when(fixture.victim().getKiller()).thenReturn(killer);

        kill(fixture);

        verify(fixture.commands()).runEventModifiers(eq("ON_DEATH"), eq(fixture.victim()),
                eq(7L), eq(List.of("Victor", "Kira", "SPEEDRUNNER")));
    }

    @Test
    void finalDeathReportsFormerRoleAfterElimination() {
        Fixture fixture = fixture(Role.SPEEDRUNNER, 1, true);

        kill(fixture);

        assertEquals(Role.SPECTATOR, fixture.players().role(fixture.victimId()));
        verify(fixture.commands()).runEventModifiers(eq("ON_DEATH"), eq(fixture.victim()),
                eq(7L), eq(List.of("Victor", "null", "SPEEDRUNNER")));
    }

    @Test
    void onDeathDispatchesAfterInternalDeathHandling() {
        Fixture fixture = fixture(Role.SPEEDRUNNER, 2, true);
        Player killer = mock(Player.class);
        UUID killerId = UUID.randomUUID();
        when(killer.getUniqueId()).thenReturn(killerId);
        when(killer.getName()).thenReturn("Kira");
        fixture.players().setRole(killerId, Role.SPEEDRUNNER);
        when(fixture.victim().getKiller()).thenReturn(killer);

        kill(fixture);

        // Lock clearing and the compass refresh both precede scripts, so
        // a converter like Infection cannot disturb internal handling.
        InOrder order = inOrder(fixture.compass(), fixture.commands());
        order.verify(fixture.compass()).clearLocksOnTargetDeath(fixture.victimId());
        order.verify(fixture.compass()).refreshInstance(fixture.instance());
        order.verify(fixture.commands()).runEventModifiers(eq("ON_DEATH"), eq(fixture.victim()),
                eq(7L), eq(List.of("Victor", "Kira", "SPEEDRUNNER")));
    }

    @Test
    void spectatorDeathFiresNoOnDeath() {
        Fixture fixture = fixture(Role.SPECTATOR, -1, true);

        kill(fixture);

        verify(fixture.commands(), never()).runEventModifiers(eq("ON_DEATH"), any(),
                anyLong(), any());
    }

    @Test
    void speedrunnerOutOfLivesBecomesSpectatorAndRecorded() {
        Fixture fixture = fixture(Role.SPEEDRUNNER, 1, true);

        kill(fixture);

        assertEquals(Role.SPECTATOR, fixture.players().role(fixture.victimId()));
        assertFalse(fixture.instance().isActive(fixture.victimId()));
        assertEquals(1, fixture.instance().deadPlayers().size());
        assertEquals(new GameInstance.DeadPlayer(fixture.victimId(), "Victor",
                Role.SPEEDRUNNER), fixture.instance().deadPlayers().get(0));
        verify(fixture.fakes()).enable(fixture.victim());
        verify(fixture.compass()).removeCompasses(fixture.victim());
        verify(fixture.compass()).clearHotspotHistory(fixture.victimId());
    }

    @Test
    void hunterOutOfLivesBecomesSpectatorAndRecorded() {
        Fixture fixture = fixture(Role.HUNTER, 1, true);

        kill(fixture);

        assertEquals(Role.SPECTATOR, fixture.players().role(fixture.victimId()));
        assertFalse(fixture.instance().isActive(fixture.victimId()));
        assertEquals(1, fixture.instance().deadPlayers().size());
        assertEquals(Role.HUNTER, fixture.instance().deadPlayers().get(0).formerRole());
        verify(fixture.fakes()).enable(fixture.victim());
        verify(fixture.compass()).removeCompasses(fixture.victim());
        verify(fixture.compass()).clearHotspotHistory(fixture.victimId());
    }

    @Test
    void speedrunnerWithLivesLeftRespawnsAlive() {
        Fixture fixture = fixture(Role.SPEEDRUNNER, 2, true);

        kill(fixture);

        assertEquals(Role.SPEEDRUNNER, fixture.players().role(fixture.victimId()));
        assertTrue(fixture.instance().isActive(fixture.victimId()));
        assertTrue(fixture.instance().deadPlayers().isEmpty());
        verify(fixture.respawn()).scheduleRespawn(fixture.victim(), fixture.instance(), false,
                0, "respawn tpl", 7L);
        verify(fixture.fakes(), never()).enable(any(Player.class));
        verify(fixture.compass(), never()).clearHotspotHistory(any(UUID.class));
    }

    @Test
    void hunterWithUnlimitedLivesStaysWithoutRespawnTask() {
        Fixture fixture = fixture(Role.HUNTER, -1, true);

        kill(fixture);

        assertEquals(Role.HUNTER, fixture.players().role(fixture.victimId()));
        assertTrue(fixture.instance().isActive(fixture.victimId()));
        assertTrue(fixture.instance().deadPlayers().isEmpty());
        verify(fixture.respawn(), never()).scheduleRespawn(any(Player.class),
                any(GameInstance.class), anyBoolean(), anyInt(), anyString(), anyLong());
    }

    @Test
    void deathBeforeBeginIsIgnored() {
        Fixture fixture = fixture(Role.SPEEDRUNNER, 1, false);

        kill(fixture);

        assertEquals(Role.SPEEDRUNNER, fixture.players().role(fixture.victimId()));
        assertTrue(fixture.instance().isActive(fixture.victimId()));
        assertTrue(fixture.instance().deadPlayers().isEmpty());
    }
}
