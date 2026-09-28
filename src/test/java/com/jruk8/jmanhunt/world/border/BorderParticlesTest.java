package com.jruk8.jmanhunt.world.border;

import org.bukkit.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BorderParticlesTest {

    @Test
    void intervalBlinksOnPulseTicks() {
        assertTrue(BorderParticles.intervalVisible(10L, 0.5));
        assertFalse(BorderParticles.intervalVisible(5L, 0.5));
        assertTrue(BorderParticles.intervalVisible(7L, 0.0));
    }

    @Test
    void intervalIgnoresLookDirection() {
        assertTrue(BorderParticles.intervalVisible(20L, 1.0));
        assertFalse(BorderParticles.intervalVisible(10L, 1.0));
        assertFalse(BorderParticles.intervalVisible(19L, 1.0));
    }

    @Test
    void intervalShowsEveryTickWhenNonPositive() {
        assertTrue(BorderParticles.intervalVisible(7L, 0.0));
        assertTrue(BorderParticles.intervalVisible(7L, -1.0));
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
    void colorOffsetsNormalizeToUnitRgb() {
        double[] rgb = BorderParticles.colorOffsets(Color.fromRGB(222, 119, 102));

        assertEquals(222.0 / 255.0, rgb[0], 1e-9);
        assertEquals(119.0 / 255.0, rgb[1], 1e-9);
        assertEquals(102.0 / 255.0, rgb[2], 1e-9);
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
