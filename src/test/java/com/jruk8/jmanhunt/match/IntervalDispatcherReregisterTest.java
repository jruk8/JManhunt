package com.jruk8.jmanhunt.match;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Targeted interval re-registration: only affected chains churn. */
class IntervalDispatcherReregisterTest {

    private record Fixture(IntervalDispatcher dispatcher, BukkitScheduler scheduler,
            List<BukkitTask> timers, List<Runnable> runnables, List<BukkitTask> laters) {
    }

    private Fixture fixture(long delayTicks) {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        OverrideService overrides = mock(OverrideService.class);
        when(plugin.overrides()).thenReturn(overrides);
        when(overrides.modifierEnabled(any(), anyString())).thenReturn(true);
        ConfigService config = mock(ConfigService.class);
        when(config.modifierNames()).thenReturn(new LinkedHashSet<>(List.of("a", "b")));
        when(config.behaviorIndexes(anyString())).thenReturn(List.of(0));
        when(config.runsOn(anyString(), anyInt())).thenReturn(List.of("INTERVAL"));
        when(config.intervalSeconds(anyString(), anyInt())).thenReturn(2.0);
        when(config.intervalDeviation(anyString(), anyInt())).thenReturn(0.0);
        when(config.intervalBehavior(anyString(), anyInt())).thenReturn("PER_INVOKE");
        when(config.delayTicks(anyString(), anyInt())).thenReturn(delayTicks);
        GameManager game = mock(GameManager.class);
        when(game.lobbyOf(anyLong())).thenReturn(null);
        when(game.onlineParticipants(anyLong())).thenReturn(List.of());
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        List<BukkitTask> timers = new ArrayList<>();
        List<Runnable> runnables = new ArrayList<>();
        List<BukkitTask> laters = new ArrayList<>();
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    runnables.add(invocation.getArgument(1, Runnable.class));
                    BukkitTask task = mock(BukkitTask.class);
                    timers.add(task);
                    return task;
                });
        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong()))
                .thenAnswer(invocation -> {
                    BukkitTask task = mock(BukkitTask.class);
                    laters.add(task);
                    return task;
                });
        IntervalDispatcher dispatcher = new IntervalDispatcher(plugin, config, game,
                mock(PlayerStateStore.class), (name, index, targets, matchId, args) -> {
                });
        return new Fixture(dispatcher, scheduler, timers, runnables, laters);
    }

    @Test
    void reregisterCancelsOnlyAffectedChains() {
        Fixture fixture = fixture(0L);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler());

            fixture.dispatcher().startIntervalModifiers(1L);
            BukkitTask taskA = fixture.timers().get(0);
            BukkitTask taskB = fixture.timers().get(1);

            fixture.dispatcher().reregisterIntervalModifiers(1L, Set.of("a"));

            verify(taskA).cancel();
            verify(taskB, never()).cancel();
            verify(fixture.scheduler(), times(3)).runTaskTimer(any(Plugin.class),
                    any(Runnable.class), anyLong(), anyLong());
            verify(fixture.timers().get(2), never()).cancel();
        }
    }

    @Test
    void reregisterEmptyIsNoop() {
        Fixture fixture = fixture(0L);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler());

            fixture.dispatcher().startIntervalModifiers(1L);
            fixture.dispatcher().reregisterIntervalModifiers(1L, Set.of());

            verify(fixture.timers().get(0), never()).cancel();
            verify(fixture.timers().get(1), never()).cancel();
            verify(fixture.scheduler(), times(2)).runTaskTimer(any(Plugin.class),
                    any(Runnable.class), anyLong(), anyLong());
        }
    }

    @Test
    void reregisterCancelsAffectedDelayedDispatches() {
        Fixture fixture = fixture(5L);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler());

            fixture.dispatcher().startIntervalModifiers(1L);
            fixture.runnables().get(0).run();
            BukkitTask delayedA = fixture.laters().get(0);

            fixture.dispatcher().reregisterIntervalModifiers(1L, Set.of("a"));

            verify(delayedA).cancel();
            verify(fixture.timers().get(1), never()).cancel();
        }
    }
}
