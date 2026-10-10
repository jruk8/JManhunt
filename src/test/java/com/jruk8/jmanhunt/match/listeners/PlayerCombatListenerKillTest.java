package com.jruk8.jmanhunt.match.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
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
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import com.jruk8.jmanhunt.player.SpawnCampService;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.WorldEngineService;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.Test;

class PlayerCombatListenerKillTest {

    private record Fixture(PlayerCombatListener listener, Player killer, PlayerStateStore players,
            GameManager game, GameInstance instance, GameStateCommandManager commands) {
    }

    private static Fixture fixture() {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.spawnCamp()).thenReturn(mock(SpawnCampService.class));
        PlayerStateStore players = new PlayerStateStore();
        GameManager game = mock(GameManager.class);
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        when(game.stateCommands()).thenReturn(commands);
        GameInstance instance = mock(GameInstance.class);
        when(instance.begun()).thenReturn(true);
        when(instance.matchId()).thenReturn(7L);
        Player killer = mock(Player.class);
        UUID killerId = UUID.randomUUID();
        when(killer.getUniqueId()).thenReturn(killerId);
        when(killer.getName()).thenReturn("Steve");
        players.setRole(killer, Role.HUNTER);
        when(game.instanceOf(killerId)).thenReturn(Optional.of(instance));
        PlayerSettings settings = new PlayerSettings();
        settings.getFriendlyFire().setHunter(false);
        settings.getFriendlyFire().setSpeedrunner(false);
        settings.getRespawn().getHunter().setEnabled(false);
        PlayerCombatListener listener = new PlayerCombatListener(
                new PlayerCombatListener.CombatReads(players,
                        mock(FakeSpectatorService.class), settings, new GameMessages(),
                        mock(MessageService.class), new ManhuntMessages()),
                new PlayerCombatListener.CombatMatch(game, mock(StatsManager.class),
                        mock(WinConditionEngine.class),
                        mock(SpeedrunnerDisconnectTracker.class), new HashMap<>()),
                new PlayerCombatListener.CombatWorld(mock(CompassManager.class),
                        mock(LobbyService.class), mock(WorldEngineService.class),
                        mock(PlayerRespawnListener.class)),
                new PlayerCombatListener.CombatEdge(plugin.spawnCamp(),
                        mock(RoleTeamService.class), mock(JManhuntLogger.class),
                        mock(LobbyConfig.class)),
                mock(TaskScheduler.class));
        return new Fixture(listener, killer, players, game, instance, commands);
    }

    @Test
    void mobKillDispatchesMobKillOnly() {
        Fixture fixture = fixture();
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getKiller()).thenReturn(fixture.killer());
        when(victim.getType()).thenReturn(EntityType.ZOMBIE);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(victim);

        fixture.listener().onEntityDeath(event);

        verify(fixture.commands()).runEventModifiers(eq("ON_MOB_KILLED"), eq(fixture.killer()), eq(7L),
                eq(List.of("ZOMBIE")));
        verify(fixture.commands(), never()).runEventModifiers(eq("ON_PLAYER_KILLS"), any(), anyLong(),
                anyList());
        verify(fixture.commands(), never()).runEventModifiers(eq("ON_EVERY_KILL"), any(), anyLong(),
                anyList());
    }

    @Test
    void hunterKillDispatchesPlayerAndHunterKills() {
        Fixture fixture = fixture();
        Player victim = mock(Player.class);
        UUID victimId = UUID.randomUUID();
        when(victim.getUniqueId()).thenReturn(victimId);
        when(victim.getName()).thenReturn("Alex");
        when(victim.getKiller()).thenReturn(fixture.killer());
        fixture.players().setRole(victim, Role.SPEEDRUNNER);
        when(fixture.game().instanceOf(victimId)).thenReturn(Optional.of(fixture.instance()));
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(victim);

        fixture.listener().onEntityDeath(event);

        verify(fixture.commands()).runEventModifiers(
                eq("ON_PLAYER_KILLS"), eq(fixture.killer()), eq(7L), eq(List.of("Steve", "Alex")));
        verify(fixture.commands()).runEventModifiers(
                eq("ON_HUNTER_KILLS"), eq(fixture.killer()), eq(7L), eq(List.of("Steve", "Alex")));
        verify(fixture.commands(), never()).runEventModifiers(eq("ON_SPEEDRUNNER_KILLS"), any(),
                anyLong(), anyList());
        verify(fixture.commands(), never()).runEventModifiers(eq("ON_MOB_KILLED"), any(), anyLong(),
                anyList());
        verify(fixture.commands(), never()).runEventModifiers(eq("ON_EVERY_KILL"), any(), anyLong(),
                anyList());
    }

    @Test
    void speedrunnerKillDispatchesPlayerAndSpeedrunnerKills() {
        Fixture fixture = fixture();
        fixture.players().setRole(fixture.killer(), Role.SPEEDRUNNER);
        Player victim = mock(Player.class);
        UUID victimId = UUID.randomUUID();
        when(victim.getUniqueId()).thenReturn(victimId);
        when(victim.getName()).thenReturn("Alex");
        when(victim.getKiller()).thenReturn(fixture.killer());
        fixture.players().setRole(victim, Role.HUNTER);
        when(fixture.game().instanceOf(victimId)).thenReturn(Optional.of(fixture.instance()));
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(victim);

        fixture.listener().onEntityDeath(event);

        verify(fixture.commands()).runEventModifiers(
                eq("ON_PLAYER_KILLS"), eq(fixture.killer()), eq(7L), eq(List.of("Steve", "Alex")));
        verify(fixture.commands()).runEventModifiers(
                eq("ON_SPEEDRUNNER_KILLS"), eq(fixture.killer()), eq(7L), eq(List.of("Steve", "Alex")));
        verify(fixture.commands(), never()).runEventModifiers(eq("ON_HUNTER_KILLS"), any(),
                anyLong(), anyList());
        verify(fixture.commands(), never()).runEventModifiers(eq("ON_MOB_KILLED"), any(), anyLong(),
                anyList());
    }
}
