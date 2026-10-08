package com.jruk8.jmanhunt.match.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.config.PlayerSettings;
import com.jruk8.jmanhunt.config.WorldEngineConfig;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.world.WorldEngineService;
import com.jruk8.jmanhunt.world.cell.CellBounds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MatchBorderEnforcerTest {

    private record Fixture(Map<Long, Runnable> timers, Player player, World world,
            AtomicReference<Location> spot, CellBounds bounds) {
    }

    private static Fixture fixture() {
        WorldEngineConfig settings = new WorldEngineConfig();
        settings.setEnabled(true);
        settings.setCellSize(200);
        settings.getWorldBorder().getStartBorder().setEnabled(false);
        Map<Long, Runnable> timers = new HashMap<>();
        TaskScheduler tasks = mock(TaskScheduler.class);
        when(tasks.runTimer(any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            timers.put(call.getArgument(2), call.getArgument(0));
            return mock(BukkitTask.class);
        });
        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        WorldEngineService worldEngine = mock(WorldEngineService.class);
        when(worldEngine.isBorderedWorld(any(World.class))).thenReturn(true);
        PlayerSettings.Spectator.Travel travel = mock(PlayerSettings.Spectator.Travel.class);
        when(travel.isEnabled()).thenReturn(false);
        FakeSpectatorService fakes = mock(FakeSpectatorService.class);
        when(fakes.isFakeSpectator(any(Player.class))).thenReturn(false);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        CellBounds bounds = CellBounds.forCell(0L, 200, 0, false);
        AtomicReference<Location> spot = new AtomicReference<>(
                new Location(world, bounds.centerX() + 10.0, 64.0, bounds.centerZ() + 20.0));
        when(player.getLocation()).thenAnswer(call -> spot.get());
        MatchStore store = mock(MatchStore.class);
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.of(0L),
                System.currentTimeMillis());
        when(store.liveInstances()).thenReturn(List.of(instance));
        when(store.onlineActivePlayers(instance)).thenReturn(List.of(player));
        new MatchBorderEnforcer(tasks,
                new MatchBorderEnforcer.BorderEngine(settings, worldEngine),
                travel, store,
                new MatchBorderEnforcer.BorderPlayers(new PlayerStateStore(), fakes));
        return new Fixture(timers, player, world, spot, bounds);
    }

    private static Location outside(Fixture fixture, World world, double y) {
        return new Location(world, fixture.bounds().centerX() + fixture.bounds().halfSize() + 50.0,
                y, fixture.bounds().centerZ() + 20.0);
    }

    private static Location captureTeleport(Player player) {
        ArgumentCaptor<Location> landing = ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(landing.capture());
        return landing.getValue();
    }

    @Test
    void escapeRubberbandsToLastInsideSnapshot() {
        Fixture fixture = fixture();
        fixture.timers().get(6L).run();
        fixture.spot().set(outside(fixture, fixture.world(), 70.0));

        fixture.timers().get(5L).run();

        Location landing = captureTeleport(fixture.player());
        assertSame(fixture.world(), landing.getWorld());
        assertEquals(fixture.bounds().centerX() + 10.0, landing.getX(), 0.0);
        assertEquals(64.0, landing.getY(), 0.0);
        assertEquals(fixture.bounds().centerZ() + 20.0, landing.getZ(), 0.0);
        verify(fixture.world(), never()).getHighestBlockYAt(anyInt(), anyInt());
    }

    @Test
    void outsideSpotsNeverStored() {
        Fixture fixture = fixture();
        fixture.spot().set(outside(fixture, fixture.world(), 70.0));
        fixture.timers().get(6L).run();

        fixture.timers().get(5L).run();

        Location landing = captureTeleport(fixture.player());
        assertEquals(fixture.bounds().centerX() + fixture.bounds().halfSize() - 0.25,
                landing.getX(), 0.0);
        assertEquals(70.0, landing.getY(), 0.0);
        assertEquals(fixture.bounds().centerZ() + 20.0, landing.getZ(), 0.0);
    }

    @Test
    void escapeWithoutSnapshotFallsBackToClamp() {
        Fixture fixture = fixture();
        fixture.spot().set(outside(fixture, fixture.world(), 70.0));

        fixture.timers().get(5L).run();

        Location landing = captureTeleport(fixture.player());
        assertSame(fixture.world(), landing.getWorld());
        assertEquals(fixture.bounds().centerX() + fixture.bounds().halfSize() - 0.25,
                landing.getX(), 0.0);
        assertEquals(70.0, landing.getY(), 0.0);
    }

    @Test
    void snapshotFromAnotherWorldFallsBackToClamp() {
        Fixture fixture = fixture();
        fixture.timers().get(6L).run();
        World other = mock(World.class);
        when(other.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(fixture.player().getWorld()).thenReturn(other);
        fixture.spot().set(outside(fixture, other, 70.0));

        fixture.timers().get(5L).run();

        Location landing = captureTeleport(fixture.player());
        assertSame(other, landing.getWorld());
        assertEquals(fixture.bounds().centerX() + fixture.bounds().halfSize() - 0.25,
                landing.getX(), 0.0);
    }

    @Test
    void insidePlayerNeverTeleported() {
        Fixture fixture = fixture();
        fixture.timers().get(6L).run();

        fixture.timers().get(5L).run();

        verify(fixture.player(), never()).teleport(any(Location.class));
    }
}
