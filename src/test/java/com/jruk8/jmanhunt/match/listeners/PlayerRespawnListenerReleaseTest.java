package com.jruk8.jmanhunt.match.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.match.CountdownService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.match.LimboFeedbackService;
import com.jruk8.jmanhunt.match.lifecycle.MatchMessaging;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

/**
 * Join-hold releases land the player on a fresh origin spawn and run
 * the deferred ON_RESPAWN catch-up; vanilla respawns and revives fire
 * triggers only when no hold owns them.
 */
class PlayerRespawnListenerReleaseTest {

    private record Fixture(PlayerRespawnListener listener, GameInstance instance,
            GameManager game, FakeSpectatorService fakes, LimboFeedbackService limbo,
            PlayerStateStore states, CountdownService countdowns,
            AtomicReference<Runnable> done) {
    }

    private static Fixture fixture() {
        TaskScheduler tasks = mock(TaskScheduler.class);
        when(tasks.run(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return mock(BukkitTask.class);
        });
        FakeSpectatorService fakes = mock(FakeSpectatorService.class);
        CountdownService countdowns = mock(CountdownService.class);
        LimboFeedbackService limbo = mock(LimboFeedbackService.class);
        PlayerStateStore states = mock(PlayerStateStore.class);
        PlayerRespawnListener.RespawnPlayers players =
                new PlayerRespawnListener.RespawnPlayers(states, fakes, countdowns, limbo);
        GameManager game = mock(GameManager.class);
        GameInstance instance = mock(GameInstance.class);
        when(instance.matchId()).thenReturn(7L);
        when(game.instance(7L)).thenReturn(Optional.of(instance));
        AtomicReference<Runnable> done = new AtomicReference<>();
        doAnswer(invocation -> {
            done.set(invocation.getArgument(3));
            return null;
        }).when(countdowns).start(any(), anyInt(), any(), any());
        PlayerRespawnListener listener = new PlayerRespawnListener(tasks, players, game,
                mock(CompassManager.class), mock(GameMessages.class));
        return new Fixture(listener, instance, game, fakes, limbo, states, countdowns, done);
    }

    private static Player namedPlayer(boolean online) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(online);
        return player;
    }

    @Test
    void releaseScattersOnlinePlayerToOriginSpawn() {
        Fixture fixture = fixture();
        Player player = namedPlayer(true);
        when(fixture.game().isActiveInInstance(7L, player.getUniqueId())).thenReturn(true);

        fixture.listener().scheduleJoinHold(player, fixture.instance(), 35);
        fixture.done().get().run();

        verify(fixture.game()).scatterEntrantToSpawn(fixture.instance(), player);
        verify(fixture.fakes()).disable(player);
        verify(fixture.limbo()).announceSpawned(fixture.instance(), player);
    }

    @Test
    void releaseSkipsScatterForOfflinePlayer() {
        Fixture fixture = fixture();
        Player player = namedPlayer(false);
        when(fixture.game().isActiveInInstance(7L, player.getUniqueId())).thenReturn(true);

        fixture.listener().scheduleJoinHold(player, fixture.instance(), 35);
        fixture.done().get().run();

        verify(fixture.game(), never()).scatterEntrantToSpawn(any(), any());
        verify(fixture.fakes()).disable(player);
        verify(fixture.limbo()).announceSpawned(fixture.instance(), player);
    }

    @Test
    void releaseFiresDeferredRespawnCatchupOncePerLife() {
        Fixture fixture = fixture();
        Player player = namedPlayer(true);
        when(fixture.game().isActiveInInstance(7L, player.getUniqueId())).thenReturn(true);
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        when(fixture.game().stateCommands()).thenReturn(commands);
        when(fixture.instance().markRespawnFired(any(), anyInt())).thenReturn(true);

        fixture.listener().scheduleJoinHold(player, fixture.instance(), 35);
        fixture.done().get().run();

        verify(commands).runRespawnForPlayer(7L, player);
    }

    @Test
    void releaseSkipsCatchupWhenLifeAlreadyFired() {
        Fixture fixture = fixture();
        Player player = namedPlayer(true);
        when(fixture.game().isActiveInInstance(7L, player.getUniqueId())).thenReturn(true);
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        when(fixture.game().stateCommands()).thenReturn(commands);

        fixture.listener().scheduleJoinHold(player, fixture.instance(), 35);
        fixture.done().get().run();

        verify(commands, never()).runRespawnForPlayer(anyLong(), any());
    }

    @Test
    void vanillaRespawnSkipsTriggerWhileHoldPending() {
        Fixture fixture = fixture();
        Player player = namedPlayer(true);
        when(fixture.game().instanceOf(player.getUniqueId()))
                .thenReturn(Optional.of(fixture.instance()));
        when(fixture.instance().begun()).thenReturn(true);
        when(fixture.states().role(player)).thenReturn(Role.HUNTER);
        when(fixture.countdowns().running(any())).thenReturn(true);
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        when(fixture.game().stateCommands()).thenReturn(commands);
        PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(player);

        fixture.listener().onRespawn(event);

        verify(commands, never()).runEventModifiers(eq("ON_RESPAWN"), any(), anyLong(),
                any());
    }

    @Test
    void vanillaRespawnSkipsTriggerWhileHeadstartHeld() {
        Fixture fixture = fixture();
        Player player = namedPlayer(true);
        when(fixture.game().instanceOf(player.getUniqueId()))
                .thenReturn(Optional.of(fixture.instance()));
        when(fixture.instance().begun()).thenReturn(true);
        when(fixture.states().role(player)).thenReturn(Role.HUNTER);
        when(fixture.instance().isHeadstartHeld(player.getUniqueId())).thenReturn(true);
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        when(fixture.game().stateCommands()).thenReturn(commands);
        PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(player);

        fixture.listener().onRespawn(event);

        verify(commands, never()).runEventModifiers(eq("ON_RESPAWN"), any(), anyLong(),
                any());
    }

    @Test
    void vanillaRespawnFiresTriggerWhenUnhindered() {
        Fixture fixture = fixture();
        Player player = namedPlayer(true);
        when(fixture.game().instanceOf(player.getUniqueId()))
                .thenReturn(Optional.of(fixture.instance()));
        when(fixture.instance().begun()).thenReturn(true);
        when(fixture.instance().active()).thenReturn(true);
        when(fixture.instance().isActive(player.getUniqueId())).thenReturn(true);
        when(fixture.states().role(player)).thenReturn(Role.HUNTER);
        when(fixture.instance().markRespawnFired(any(), anyInt())).thenReturn(true);
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        when(fixture.game().stateCommands()).thenReturn(commands);
        PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(player);

        fixture.listener().onRespawn(event);

        verify(commands).runEventModifiers(eq("ON_RESPAWN"), eq(player), eq(7L), any());
        verify(commands).runEventModifiers(eq("ON_HUNTER_RESPAWN"), eq(player), eq(7L),
                any());
    }

    @Test
    void vanillaRespawnSkipsTriggerWhenEnding() {
        Fixture fixture = fixture();
        Player player = namedPlayer(true);
        when(fixture.game().instanceOf(player.getUniqueId()))
                .thenReturn(Optional.of(fixture.instance()));
        when(fixture.instance().begun()).thenReturn(true);
        when(fixture.instance().active()).thenReturn(true);
        when(fixture.instance().ending()).thenReturn(true);
        when(fixture.instance().isActive(player.getUniqueId())).thenReturn(true);
        when(fixture.states().role(player)).thenReturn(Role.HUNTER);
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        when(fixture.game().stateCommands()).thenReturn(commands);
        PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(player);

        fixture.listener().onRespawn(event);

        verify(commands, never()).runEventModifiers(eq("ON_RESPAWN"), any(), anyLong(),
                any());
    }

    @Test
    void reviveSkipsTriggerWhileHeadstartHeld() {
        Fixture fixture = fixture();
        Player player = namedPlayer(true);
        when(player.getName()).thenReturn("Alex");
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(world.getSpawnLocation()).thenReturn(mock(Location.class));
        when(fixture.game().isActiveInInstance(7L, player.getUniqueId())).thenReturn(true);
        when(fixture.instance().isActive(player.getUniqueId())).thenReturn(true);
        when(fixture.instance().begun()).thenReturn(true);
        when(fixture.instance().isHeadstartHeld(player.getUniqueId())).thenReturn(true);
        when(fixture.states().role(player)).thenReturn(Role.HUNTER);
        when(fixture.game().messaging()).thenReturn(mock(MatchMessaging.class));
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        when(fixture.game().stateCommands()).thenReturn(commands);

        fixture.listener().scheduleRespawn(player, 5, 7L);
        fixture.done().get().run();

        verify(player).setHealth(anyDouble());
        verify(commands, never()).runEventModifiers(eq("ON_RESPAWN"), any(), anyLong(),
                any());
    }

    @Test
    void reviveFiresTriggerAfterDelayWhenUnhindered() {
        Fixture fixture = fixture();
        Player player = namedPlayer(true);
        when(player.getName()).thenReturn("Alex");
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(world.getSpawnLocation()).thenReturn(mock(Location.class));
        when(fixture.game().isActiveInInstance(7L, player.getUniqueId())).thenReturn(true);
        when(fixture.instance().isActive(player.getUniqueId())).thenReturn(true);
        when(fixture.instance().begun()).thenReturn(true);
        when(fixture.instance().active()).thenReturn(true);
        when(fixture.states().role(player)).thenReturn(Role.HUNTER);
        when(fixture.instance().markRespawnFired(any(), anyInt())).thenReturn(true);
        when(fixture.game().messaging()).thenReturn(mock(MatchMessaging.class));
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        when(fixture.game().stateCommands()).thenReturn(commands);

        fixture.listener().scheduleRespawn(player, 5, 7L);
        fixture.done().get().run();

        verify(commands).runEventModifiers(eq("ON_RESPAWN"), eq(player), eq(7L), any());
    }
}
