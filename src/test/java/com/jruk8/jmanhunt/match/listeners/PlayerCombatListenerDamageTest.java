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
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import com.jruk8.jmanhunt.player.SpawnCampService;
import com.jruk8.jmanhunt.stats.Stats;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.WorldEngineService;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

class PlayerCombatListenerDamageTest {

    private record Fixture(PlayerCombatListener listener, Player victim, Player attacker,
            GameManager game, GameInstance instance, GameStateCommandManager commands) {
    }

    private static Fixture fixture(Role victimRole, Role attackerRole, boolean begun) {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.spawnCamp()).thenReturn(mock(SpawnCampService.class));
        PlayerStateStore players = new PlayerStateStore();
        GameManager game = mock(GameManager.class);
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        when(game.stateCommands()).thenReturn(commands);
        StatsManager stats = mock(StatsManager.class);
        when(stats.getOrCreate(anyLong(), any(UUID.class))).thenReturn(new Stats());
        GameInstance instance = mock(GameInstance.class);
        when(instance.begun()).thenReturn(begun);
        when(instance.matchId()).thenReturn(7L);
        Player victim = mock(Player.class);
        UUID victimId = UUID.randomUUID();
        when(victim.getUniqueId()).thenReturn(victimId);
        when(victim.getName()).thenReturn("Victor");
        players.setRole(victim, victimRole);
        when(game.instanceOf(victimId)).thenReturn(Optional.of(instance));
        Player attacker = mock(Player.class);
        UUID attackerId = UUID.randomUUID();
        when(attacker.getUniqueId()).thenReturn(attackerId);
        when(attacker.getName()).thenReturn("Steve");
        players.setRole(attacker, attackerRole);
        when(game.instanceOf(attackerId)).thenReturn(Optional.of(instance));
        PlayerSettings settings = new PlayerSettings();
        settings.getFriendlyFire().setHunter(false);
        settings.getFriendlyFire().setSpeedrunner(false);
        PlayerCombatListener listener = new PlayerCombatListener(
                new PlayerCombatListener.CombatReads(players,
                        mock(FakeSpectatorService.class), settings, new GameMessages()),
                new PlayerCombatListener.CombatMatch(game, stats,
                        mock(WinConditionEngine.class),
                        mock(SpeedrunnerDisconnectTracker.class), new HashMap<>()),
                new PlayerCombatListener.CombatWorld(mock(CompassManager.class),
                        mock(LobbyService.class), mock(WorldEngineService.class),
                        mock(PlayerRespawnListener.class)),
                new PlayerCombatListener.CombatEdge(plugin.spawnCamp(),
                        mock(RoleTeamService.class), mock(JManhuntLogger.class),
                        mock(LobbyConfig.class)),
                mock(TaskScheduler.class));
        return new Fixture(listener, victim, attacker, game, instance, commands);
    }

    private static EntityDamageByEntityEvent hit(Player victim, double damage) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(victim);
        when(event.getFinalDamage()).thenReturn(damage);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        return event;
    }

    @Test
    void playerDealerFiresWithNameAndHalfHearts() {
        Fixture fixture = fixture(Role.HUNTER, Role.SPEEDRUNNER, true);
        EntityDamageByEntityEvent event = hit(fixture.victim(), 4.5);
        when(event.getDamager()).thenReturn(fixture.attacker());

        fixture.listener().onDamage(event);

        verify(fixture.commands()).runEventModifiers(eq("ON_DAMAGE_TAKEN"), eq(fixture.victim()),
                eq(7L), eq(List.of("Victor", "4.5", "Steve")));
    }

    @Test
    void mobDealerFiresWithNullDealer() {
        Fixture fixture = fixture(Role.HUNTER, Role.SPEEDRUNNER, true);
        EntityDamageByEntityEvent event = hit(fixture.victim(), 4.5);
        when(event.getDamager()).thenReturn(mock(LivingEntity.class));

        fixture.listener().onDamage(event);

        verify(fixture.commands()).runEventModifiers(eq("ON_DAMAGE_TAKEN"), eq(fixture.victim()),
                eq(7L), eq(List.of("Victor", "4.5", "null")));
    }

    @Test
    void environmentDamageFiresWithNullDealer() {
        Fixture fixture = fixture(Role.HUNTER, Role.SPEEDRUNNER, true);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(fixture.victim());
        when(event.getFinalDamage()).thenReturn(3.0);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);

        fixture.listener().onDamage(event);

        verify(fixture.commands()).runEventModifiers(eq("ON_DAMAGE_TAKEN"), eq(fixture.victim()),
                eq(7L), eq(List.of("Victor", "3.0", "null")));
    }

    @Test
    void friendlyFireCancelDoesNotFire() {
        Fixture fixture = fixture(Role.HUNTER, Role.HUNTER, true);
        EntityDamageByEntityEvent event = hit(fixture.victim(), 4.5);
        when(event.getDamager()).thenReturn(fixture.attacker());

        fixture.listener().onDamage(event);

        verify(fixture.commands(), never()).runEventModifiers(eq("ON_DAMAGE_TAKEN"), any(),
                anyLong(), anyList());
    }

    @Test
    void preStartDamageDoesNotFire() {
        Fixture fixture = fixture(Role.HUNTER, Role.SPEEDRUNNER, false);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(fixture.victim());
        when(event.getFinalDamage()).thenReturn(3.0);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);

        fixture.listener().onDamage(event);

        verify(fixture.commands(), never()).runEventModifiers(eq("ON_DAMAGE_TAKEN"), any(),
                anyLong(), anyList());
    }
}
