package com.jruk8.jmanhunt.compass;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompassManagerTest {

    @Test
    void spinAngleStartsAtZero() {
        assertEquals(0.0, CompassManager.spinAngle(0L), 1e-9);
    }

    @Test
    void spinAngleAdvancesOneDegreePer200ms() {
        assertEquals(1.0, CompassManager.spinAngle(200L), 1e-9);
        assertEquals(1.5, CompassManager.spinAngle(300L), 1e-9);
    }

    @Test
    void spinAngleWrapsAfterFullTurn() {
        assertEquals(359.0, CompassManager.spinAngle(200L * 359), 1e-9);
        assertEquals(0.0, CompassManager.spinAngle(200L * 360), 1e-9);
    }

    @Test
    void flatDistanceIgnoresHeight() {
        Location from = new Location(null, 0.0, 64.0, 0.0);
        Location to = new Location(null, 3.0, 200.0, 4.0);
        assertEquals(5.0, CompassManager.flatDistance(from, to), 1e-9);
    }
}
