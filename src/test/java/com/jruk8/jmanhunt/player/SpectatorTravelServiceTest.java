package com.jruk8.jmanhunt.player;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Travel-limit anchor picks: nearest, fallback, and cross-world. */
class SpectatorTravelServiceTest {

    private static World world(UUID id) {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(id);
        return world;
    }

    @Test
    void withinCapStaysPut() {
        World overworld = world(UUID.randomUUID());
        Location spectator = new Location(overworld, 0.0, 64.0, 0.0);
        Location anchor = new Location(overworld, 100.0, 64.0, 0.0);
        Location center = new Location(overworld, 500.0, 64.0, 0.0);

        assertEquals(Optional.empty(), SpectatorTravelService.teleportTarget(
                spectator, List.of(anchor), center, 125.0));
    }

    @Test
    void beyondCapReturnsNearestAnchor() {
        World overworld = world(UUID.randomUUID());
        Location spectator = new Location(overworld, 0.0, 64.0, 0.0);
        Location near = new Location(overworld, 200.0, 64.0, 0.0);
        Location far = new Location(overworld, 400.0, 64.0, 0.0);
        Location center = new Location(overworld, 500.0, 64.0, 0.0);

        assertEquals(Optional.of(near), SpectatorTravelService.teleportTarget(
                spectator, List.of(far, near), center, 125.0));
    }

    @Test
    void noAnchorsComparesAgainstCellCenter() {
        World overworld = world(UUID.randomUUID());
        Location spectator = new Location(overworld, 0.0, 64.0, 0.0);
        Location center = new Location(overworld, 500.0, 64.0, 0.0);

        assertEquals(Optional.of(center), SpectatorTravelService.teleportTarget(
                spectator, List.of(), center, 125.0));
        Location close = new Location(overworld, 490.0, 64.0, 0.0);
        assertEquals(Optional.empty(), SpectatorTravelService.teleportTarget(
                close, List.of(), center, 125.0));
    }

    @Test
    void crossWorldFallsBackToCenter() {
        World overworld = world(UUID.randomUUID());
        World nether = world(UUID.randomUUID());
        Location spectator = new Location(nether, 0.0, 64.0, 0.0);
        Location anchor = new Location(overworld, 0.0, 64.0, 0.0);
        Location center = new Location(overworld, 500.0, 64.0, 0.0);

        assertEquals(Optional.of(center), SpectatorTravelService.teleportTarget(
                spectator, List.of(anchor), center, 125.0));
    }

    @Test
    void noAnchorsAndNoCenterStaysPut() {
        World overworld = world(UUID.randomUUID());
        Location spectator = new Location(overworld, 0.0, 64.0, 0.0);

        assertEquals(Optional.empty(), SpectatorTravelService.teleportTarget(
                spectator, List.of(), null, 125.0));
    }
}
