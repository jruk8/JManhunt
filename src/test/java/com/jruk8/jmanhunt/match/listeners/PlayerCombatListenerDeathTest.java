package com.jruk8.jmanhunt.match.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
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
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PlayerCombatListenerDeathTest {

    private record Fixture(PlayerCombatListener listener, Player victim, UUID victimId,
            PlayerStateStore players, GameInstance instance, CompassManager compass,
            FakeSpectatorService fakes, PlayerRespawnListener respawn) {
    }

    private static Fixture fixture(Role role, int lives, boolean begun) {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.spawnCamp()).thenReturn(mock(SpawnCampService.class));
        when(plugin.roleTeams()).thenReturn(mock(RoleTeamService.class));
        FakeSpectatorService fakes = mock(FakeSpectatorService.class);
        when(plugin.fakeSpectators()).thenReturn(fakes);
        PlayerStateStore players = new PlayerStateStore();
        GameManager game = mock(GameManager.class);
        when(game.stateCommands()).thenReturn(mock(GameStateCommandManager.class));
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
        PlayerCombatListener listener = new PlayerCombatListener(plugin, players, game,
                mock(ConfigService.class), compass, stats, mock(LobbyService.class),
                mock(WorldEngineService.class), mock(WinConditionEngine.class), respawn,
                mock(SpeedrunnerDisconnectTracker.class), new HashMap<>(), texts());
        return new Fixture(listener, victim, victimId, players, instance, compass, fakes,
                respawn);
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
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            when(Bukkit.getScheduler()).thenReturn(scheduler);
            when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(
                    invocation -> {
                        invocation.getArgument(1, Runnable.class).run();
                        return mock(BukkitTask.class);
                    });
            fixture.listener().onDeath(event);
        }
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
