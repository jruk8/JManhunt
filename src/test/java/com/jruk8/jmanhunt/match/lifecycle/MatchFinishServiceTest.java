package com.jruk8.jmanhunt.match.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

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
        assertEquals(65.0, MatchFinishService.recoveryY(
                world(World.Environment.NORMAL, 64, 320), 10.4, 20.7, 11.0), 0.0);
        assertEquals(65.0, MatchFinishService.recoveryY(
                world(World.Environment.THE_END, 64, 320), 10.4, 20.7, 11.0), 0.0);
    }

    @Test
    void recoveryKeepsLiveYInNether() {
        assertEquals(11.0, MatchFinishService.recoveryY(
                world(World.Environment.NETHER, 120, 320), 10.4, 20.7, 11.0), 0.0);
    }

    @Test
    void recoveryCapsBelowCeiling() {
        assertEquals(318.0, MatchFinishService.recoveryY(
                world(World.Environment.NORMAL, 400, 320), 10.4, 20.7, 11.0), 0.0);
    }

}
