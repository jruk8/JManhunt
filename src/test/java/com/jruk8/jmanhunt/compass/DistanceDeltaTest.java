package com.jruk8.jmanhunt.compass;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DistanceDeltaTest {

    @Test
    void furtherAndCloser() {
        assertEquals(DistanceDelta.Kind.FURTHER, DistanceDelta.of(10L, 20L, 200.0, 5.0));
        assertEquals(DistanceDelta.Kind.CLOSER, DistanceDelta.of(20L, 10L, 200.0, 5.0));
    }

    @Test
    void firstSightingAndEqualAreSame() {
        assertEquals(DistanceDelta.Kind.SAME, DistanceDelta.of(null, 20L, 200.0, 5.0));
        assertEquals(DistanceDelta.Kind.SAME, DistanceDelta.of(20L, 20L, 200.0, 5.0));
    }

    @Test
    void belowMinDeltaIsSame() {
        assertEquals(DistanceDelta.Kind.SAME, DistanceDelta.of(10L, 14L, 200.0, 5.0));
        assertEquals(DistanceDelta.Kind.FURTHER, DistanceDelta.of(10L, 15L, 200.0, 5.0));
    }

    @Test
    void pastMaxDistanceIsSame() {
        assertEquals(DistanceDelta.Kind.SAME, DistanceDelta.of(10L, 201L, 200.0, 5.0));
        assertEquals(DistanceDelta.Kind.FURTHER, DistanceDelta.of(10L, 200L, 200.0, 5.0));
    }

    @Test
    void modeParsesWithBlinkFallback() {
        assertEquals(DistanceDelta.Mode.HOLD, DistanceDelta.Mode.parse("HOLD"));
        assertEquals(DistanceDelta.Mode.HOLD, DistanceDelta.Mode.parse(" hold "));
        assertEquals(DistanceDelta.Mode.BLINK, DistanceDelta.Mode.parse("BLINK"));
        assertEquals(DistanceDelta.Mode.BLINK, DistanceDelta.Mode.parse("blink"));
        assertEquals(DistanceDelta.Mode.BLINK, DistanceDelta.Mode.parse("banana"));
        assertEquals(DistanceDelta.Mode.BLINK, DistanceDelta.Mode.parse(null));
    }
}
