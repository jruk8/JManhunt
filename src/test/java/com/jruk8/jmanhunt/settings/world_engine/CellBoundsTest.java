package com.jruk8.jmanhunt.settings.world_engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellBoundsTest {

    @Test
    void cellZeroSpansHalfSizeAroundOrigin() {
        CellBounds bounds = CellBounds.forCell(0L, 1000, 20, false);

        assertTrue(bounds.contains(0, 0));
        assertTrue(bounds.contains(500, -500));
        assertFalse(bounds.contains(501, 0));
        assertFalse(bounds.contains(0, -501));
    }

    @Test
    void laterCellsCenterOnTheirOrigin() {
        // Cell 1 sits at grid (1, 0): block center (1000, 0) at size 1000.
        CellBounds bounds = CellBounds.forCell(1L, 1000, 20, false);

        assertTrue(bounds.contains(1000, 0));
        assertTrue(bounds.contains(1499, 499));
        assertFalse(bounds.contains(1501, 0));
        assertFalse(bounds.contains(499, 0));
    }

    @Test
    void startPhaseUsesStartDiameter() {
        CellBounds bounds = CellBounds.forCell(0L, 1000, 20, true);

        assertTrue(bounds.contains(10, -10));
        assertFalse(bounds.contains(11, 0));
    }

    @Test
    void netherUsesEighthScale() {
        CellBounds bounds = CellBounds.forCell(1L, 1000, 20, false);

        assertTrue(bounds.contains(125, 0, true));
        assertTrue(bounds.contains(187, 62, true));
        assertFalse(bounds.contains(188, 0, true));
    }

    @Test
    void outsideByMeasuresWorstAxis() {
        CellBounds bounds = CellBounds.forCell(0L, 1000, 20, false);

        assertEquals(0.0, bounds.outsideBy(100, 100, false), 0.0001);
        assertEquals(3.0, bounds.outsideBy(503, -100, false), 0.0001);
        assertEquals(100.0, bounds.outsideBy(600, 0, false), 0.0001);
    }

    @Test
    void outsideByScalesInNether() {
        CellBounds bounds = CellBounds.forCell(0L, 1000, 20, false);

        assertEquals(0.0, bounds.outsideBy(62, 0, true), 0.0001);
        assertEquals(1.0, bounds.outsideBy(63.5, 0, true), 0.0001);
    }

    @Test
    void clampInsideReturnsNullWhenInside() {
        CellBounds bounds = CellBounds.forCell(0L, 1000, 20, false);

        assertNull(bounds.clampInside(100, -100, false, 2.0));
        assertNull(bounds.clampInside(500, 500, false, 0.0));
    }

    @Test
    void clampInsidePullsEscapedAxisOffTheEdge() {
        CellBounds bounds = CellBounds.forCell(0L, 1000, 20, false);

        // Cell 0 spans -500..500; margin 2 pulls the escaped axis to 498.
        double[] clamped = bounds.clampInside(600, 100, false, 2.0);

        assertNotNull(clamped);
        assertEquals(498.0, clamped[0], 0.0001);
        assertEquals(100.0, clamped[1], 0.0001);
    }

    @Test
    void clampInsideScalesInNether() {
        CellBounds bounds = CellBounds.forCell(0L, 1000, 20, false);

        // Nether span is -62.5..62.5; margin 2 pulls the escaped axis to 60.5.
        double[] clamped = bounds.clampInside(100, 10, true, 2.0);

        assertNotNull(clamped);
        assertEquals(60.5, clamped[0], 0.0001);
        assertEquals(10.0, clamped[1], 0.0001);
    }
}
