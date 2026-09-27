package com.jruk8.jmanhunt.command;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Canonical location lists: pitch before yaw, world as dimension. */
class TagLocationsTest {

    @Test
    void formatsWholeCoordsBare() {
        Location location = new Location(null, 100, 64, -30, 90f, 12f);

        assertEquals("[100, 64, -30, 12, 90, world]",
                TagLocations.formatLocation(location, "world"));
    }

    @Test
    void keepsFractionalCoords() {
        Location location = new Location(null, 100.5, 64.25, -30.75, 90.5f, 12.5f);

        assertEquals("[100.5, 64.25, -30.75, 12.5, 90.5, world_nether]",
                TagLocations.formatLocation(location, "world_nether"));
    }
}
