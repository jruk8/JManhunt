package com.jruk8.jmanhunt.world.border;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

class WorldBorderServiceTest {

    private static World world(String name, World.Environment environment) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getEnvironment()).thenReturn(environment);
        return world;
    }

    @Test
    void overworldAndNetherGameWorldsAreBorderable() {
        assertTrue(WorldBorderService.isBorderable(
                world("world", World.Environment.NORMAL), "jmh_lobby"));
        assertTrue(WorldBorderService.isBorderable(
                world("world_nether", World.Environment.NETHER), "jmh_lobby"));
    }

    @Test
    void lobbyWorldIsNeverBorderable() {
        assertFalse(WorldBorderService.isBorderable(
                world("jmh_lobby", World.Environment.NORMAL), "jmh_lobby"));
        assertFalse(WorldBorderService.isBorderable(
                world("JMH_LOBBY", World.Environment.NORMAL), "jmh_lobby"));
        assertFalse(WorldBorderService.isBorderable(
                world("world", World.Environment.NORMAL), "world"));
    }

    @Test
    void endAndCustomWorldsAreNeverBorderable() {
        assertFalse(WorldBorderService.isBorderable(
                world("world_the_end", World.Environment.THE_END), "jmh_lobby"));
        assertFalse(WorldBorderService.isBorderable(
                world("custom", World.Environment.CUSTOM), "jmh_lobby"));
    }

    @Test
    void nullWorldIsNeverBorderable() {
        assertFalse(WorldBorderService.isBorderable(null, "jmh_lobby"));
    }

    @Test
    void nullLobbyNameStillGuardsEnvironment() {
        assertTrue(WorldBorderService.isBorderable(
                world("world", World.Environment.NORMAL), null));
        assertFalse(WorldBorderService.isBorderable(
                world("world_the_end", World.Environment.THE_END), null));
    }
}
