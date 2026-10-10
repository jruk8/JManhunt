package com.jruk8.jmanhunt.compass;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Accuracy math pins: error share, tenth steps, percents, hex lerp
 * with junk fallbacks, and the self-closing bar segment. No Bukkit
 * server needed.
 */
class CompassAccuracyRendererTest {

    @Test
    void accuracySharesErrorOverTheory() {
        assertEquals(1.0, CompassAccuracyRenderer.accuracy(0.0, 600.0), 0.0);
        assertEquals(1.0, CompassAccuracyRenderer.accuracy(0.0, 0.0), 0.0);
        assertEquals(0.5, CompassAccuracyRenderer.accuracy(300.0, 600.0), 1e-9);
        assertEquals(0.0, CompassAccuracyRenderer.accuracy(600.0, 600.0), 0.0);
        assertEquals(0.0, CompassAccuracyRenderer.accuracy(900.0, 600.0), 0.0);
        assertEquals(1.0, CompassAccuracyRenderer.accuracy(Double.NaN, 600.0), 0.0);
    }

    @Test
    void steppedSnapsToTenths() {
        assertEquals(0.9, CompassAccuracyRenderer.stepped(0.94), 0.0);
        assertEquals(1.0, CompassAccuracyRenderer.stepped(0.95), 0.0);
        assertEquals(0.0, CompassAccuracyRenderer.stepped(0.04), 0.0);
        assertEquals(0.0, CompassAccuracyRenderer.stepped(Double.NaN), 0.0);
        assertEquals(1.0, CompassAccuracyRenderer.stepped(1.5), 0.0);
    }

    @Test
    void percentReadsWholeTens() {
        assertEquals(90, CompassAccuracyRenderer.percent(0.9));
        assertEquals(100, CompassAccuracyRenderer.percent(1.0));
        assertEquals(0, CompassAccuracyRenderer.percent(0.0));
    }

    @Test
    void lerpColorHitsBothEndsAndMiddle() {
        assertEquals("#cc472d",
                CompassAccuracyRenderer.lerpColor("#cc472d", "#63d42a", 0.0));
        assertEquals("#63d42a",
                CompassAccuracyRenderer.lerpColor("#cc472d", "#63d42a", 1.0));
        assertEquals("#808080",
                CompassAccuracyRenderer.lerpColor("#000000", "#ffffff", 0.5));
        assertEquals("#808080",
                CompassAccuracyRenderer.lerpColor("000000", "FFFFFF", 0.5));
    }

    @Test
    void lerpColorFallsBackOnJunk() {
        assertEquals("#cc472d",
                CompassAccuracyRenderer.lerpColor("bogus", "#63d42a", 0.0));
        assertEquals("#63d42a",
                CompassAccuracyRenderer.lerpColor("#cc472d", null, 1.0));
        assertEquals("#cc472d", CompassAccuracyRenderer.lerpColor("#12345", "#xyz", 0.0));
    }

    @Test
    void segmentClosesItsOwnColor() {
        assertEquals("<white>(<#a1b2c3>80%<white>)</white>",
                CompassAccuracyRenderer.segment(80, "#a1b2c3"));
    }
}
