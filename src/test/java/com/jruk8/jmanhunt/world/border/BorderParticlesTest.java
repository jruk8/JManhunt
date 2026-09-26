package com.jruk8.jmanhunt.world.border;

import org.bukkit.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BorderParticlesTest {

    @Test
    void thinningRampsFromEdgeToFull() {
        assertEquals(1.0, BorderParticles.thinningFraction(0.0, 10.0), 1e-9);
        assertEquals(0.65, BorderParticles.thinningFraction(5.0, 10.0), 1e-9);
        assertEquals(0.3, BorderParticles.thinningFraction(10.0, 10.0), 1e-9);
    }

    @Test
    void thinningClampsOutsideAndDegenerate() {
        assertEquals(0.3, BorderParticles.thinningFraction(20.0, 10.0), 1e-9);
        assertEquals(1.0, BorderParticles.thinningFraction(5.0, 0.0), 1e-9);
    }

    @Test
    void hashIsDeterministicInsideUnitRange() {
        double first = BorderParticles.hash01(100.0, 64.0, -200.0);
        assertEquals(first, BorderParticles.hash01(100.0, 64.0, -200.0), 0.0);
        for (int index = 0; index < 64; index++) {
            double hash = BorderParticles.hash01(index, 2L * index, -3L * index);
            assertTrue(hash >= 0.0 && hash < 1.0, "hash out of range: " + hash);
        }
    }

    @Test
    void viewingAngleRunsPerpendicularToParallel() {
        assertEquals(0.0, BorderParticles.viewingAngleDegrees(1.0, 0.0, BorderPlane.NEG_X), 1e-9);
        assertEquals(90.0, BorderParticles.viewingAngleDegrees(0.0, 1.0, BorderPlane.NEG_X), 1e-9);
        double diagonal = Math.sqrt(0.5);
        assertEquals(45.0, BorderParticles.viewingAngleDegrees(diagonal, diagonal, BorderPlane.POS_X), 1e-9);
        assertEquals(0.0, BorderParticles.viewingAngleDegrees(0.0, 1.0, BorderPlane.NEG_Z), 1e-9);
        assertEquals(90.0, BorderParticles.viewingAngleDegrees(1.0, 0.0, BorderPlane.POS_Z), 1e-9);
    }

    @Test
    void intervalBlinksOnPulseTicks() {
        assertTrue(BorderParticles.intervalVisible(10L, 0.5, 0.0));
        assertFalse(BorderParticles.intervalVisible(5L, 0.5, 0.0));
        assertTrue(BorderParticles.intervalVisible(7L, 0.0, 45.0));
    }

    @Test
    void intervalSlowsTowardDoubleByThirtyDegrees() {
        assertTrue(BorderParticles.intervalVisible(15L, 0.5, 15.0));
        assertFalse(BorderParticles.intervalVisible(10L, 0.5, 15.0));
        assertFalse(BorderParticles.intervalVisible(10L, 0.5, 30.0));
        assertTrue(BorderParticles.intervalVisible(20L, 0.5, 30.0));
        assertTrue(BorderParticles.intervalVisible(20L, 0.5, 90.0));
    }

    @Test
    void wavePhaseProjectsOntoTravelDirection() {
        assertEquals(2.0 * Math.PI, BorderParticles.wavePhase(8.0, 0.0, 0.0, 8.0), 1e-9);
        assertEquals(Math.PI, BorderParticles.wavePhase(4.0, 0.0, 0.0, 8.0), 1e-9);
        assertEquals(2.0 * Math.PI, BorderParticles.wavePhase(0.0, 8.0, 90.0, 8.0), 1e-9);
    }

    @Test
    void waveTravelsTowardPlusUAtZeroDegrees() {
        double behind = BorderParticles.wavePhase(0.0, 0.0, 0.0, 8.0);
        double ahead = BorderParticles.wavePhase(2.0, 0.0, 0.0, 8.0);

        assertTrue(BorderParticles.waveVisible(behind, 0.5, 5L));
        assertFalse(BorderParticles.waveVisible(ahead, 0.5, 5L));
        assertTrue(BorderParticles.waveVisible(ahead, 0.5, 15L));
    }

    @Test
    void waveTravelsUpAtNinetyDegrees() {
        double below = BorderParticles.wavePhase(0.0, 0.0, 90.0, 8.0);
        double above = BorderParticles.wavePhase(0.0, 2.0, 90.0, 8.0);

        assertTrue(BorderParticles.waveVisible(below, 0.5, 5L));
        assertFalse(BorderParticles.waveVisible(above, 0.5, 5L));
        assertTrue(BorderParticles.waveVisible(above, 0.5, 15L));
    }

    @Test
    void hexParsesWithOrWithoutHash() {
        Color parsed = BorderParticles.parseHexColor("#de7766", Color.WHITE);

        assertEquals(222, parsed.getRed());
        assertEquals(119, parsed.getGreen());
        assertEquals(102, parsed.getBlue());
        assertEquals(parsed, BorderParticles.parseHexColor("de7766", Color.WHITE));
        assertEquals(parsed, BorderParticles.parseHexColor("  #De7766  ", Color.WHITE));
    }

    @Test
    void hexFallsBackOnGarbage() {
        Color fallback = Color.fromRGB(1, 2, 3);

        assertEquals(fallback, BorderParticles.parseHexColor("xyz", fallback));
        assertEquals(fallback, BorderParticles.parseHexColor("#12345", fallback));
        assertEquals(fallback, BorderParticles.parseHexColor("#gggggg", fallback));
        assertEquals(fallback, BorderParticles.parseHexColor("", fallback));
        assertEquals(fallback, BorderParticles.parseHexColor(null, fallback));
    }
}
