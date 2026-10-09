package com.jruk8.jmanhunt.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.command.TagLocations;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TeleportServiceTest {

    private static World world(String name, World.Environment environment) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getEnvironment()).thenReturn(environment);
        return world;
    }

    @Test
    void resolveWorldPrefersExactName() {
        World literal = world("nether", World.Environment.NORMAL);
        World actual = world("world_nether", World.Environment.NETHER);
        assertEquals(literal,
                TeleportService.resolveWorld("nether", List.of(literal, actual)).orElseThrow());
    }

    @Test
    void resolveWorldFallsBackToEnvironmentAlias() {
        World overworld = world("world", World.Environment.NORMAL);
        World nether = world("world_nether", World.Environment.NETHER);
        World end = world("world_the_end", World.Environment.THE_END);
        List<World> worlds = List.of(overworld, nether, end);
        assertEquals(nether, TeleportService.resolveWorld("nether", worlds).orElseThrow());
        assertEquals(end, TeleportService.resolveWorld("END", worlds).orElseThrow());
        assertEquals(overworld, TeleportService.resolveWorld("world", worlds).orElseThrow());
        assertTrue(TeleportService.resolveWorld("moon", worlds).isEmpty());
    }

    @Test
    void teleportUsesGivenAngles() {
        World world = world("world", World.Environment.NORMAL);
        Player player = mock(Player.class);
        List<String> warnings = new ArrayList<>();
        TagLocations.TeleportRequest target =
                new TagLocations.TeleportRequest(100, 64, -30, "world", 10.0f, 20.0f);

        assertTrue(TeleportService.teleport(player, target, List.of(world), warnings::add));

        ArgumentCaptor<Location> destination = ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(destination.capture());
        assertEquals(world, destination.getValue().getWorld());
        assertEquals(100, destination.getValue().getX());
        assertEquals(64, destination.getValue().getY());
        assertEquals(-30, destination.getValue().getZ());
        assertEquals(10.0f, destination.getValue().getPitch());
        assertEquals(20.0f, destination.getValue().getYaw());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void teleportKeepsCurrentViewWhenAnglesOmitted() {
        World world = world("world", World.Environment.NORMAL);
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 0, 0, 0, 30.0f, 40.0f));
        List<String> warnings = new ArrayList<>();
        TagLocations.TeleportRequest target =
                new TagLocations.TeleportRequest(1, 2, 3, "world", null, null);

        assertTrue(TeleportService.teleport(player, target, List.of(world), warnings::add));

        ArgumentCaptor<Location> destination = ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(destination.capture());
        assertEquals(40.0f, destination.getValue().getPitch());
        assertEquals(30.0f, destination.getValue().getYaw());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void teleportUnknownWorldWarnsAndMovesNothing() {
        World world = world("world", World.Environment.NORMAL);
        Player player = mock(Player.class);
        List<String> warnings = new ArrayList<>();
        TagLocations.TeleportRequest target =
                new TagLocations.TeleportRequest(1, 2, 3, "moon", null, null);

        assertFalse(TeleportService.teleport(player, target, List.of(world), warnings::add));

        verify(player, never()).teleport(any(Location.class));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("moon"));
    }
}
