package com.jruk8.jmanhunt.lobby;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyBoundsTest {

    private static LobbyBounds.Bound bound(double x1, double y1, double z1,
            double x2, double y2, double z2) {
        return new LobbyBounds.Bound(x1, y1, z1, x2, y2, z2);
    }

    @Test
    void containsMatchesBlocksInclusivelyRegardlessOfCornerOrder() {
        LobbyBounds.Bound box = bound(10.0, 70.0, 10.0, 0.0, 64.0, 0.0);

        assertTrue(LobbyBounds.contains(box, 0, 64, 0));
        assertTrue(LobbyBounds.contains(box, 10, 70, 10));
        assertTrue(LobbyBounds.contains(box, 5, 66, 5));
        assertFalse(LobbyBounds.contains(box, 11, 66, 5));
        assertFalse(LobbyBounds.contains(box, 5, 63, 5));
        assertFalse(LobbyBounds.contains(box, 5, 66, -1));
    }

    @Test
    void matchFloorsFractionalPositionsToTheirBlock() {
        Map<Integer, LobbyBounds.Bound> bounds = Map.of(0, bound(0.0, 64.0, 0.0, 10.0, 70.0, 10.0));

        assertEquals(OptionalInt.of(0), LobbyBounds.match(bounds, 10.9, 70.9, 10.9));
        assertEquals(OptionalInt.empty(), LobbyBounds.match(bounds, 11.0, 66.0, 5.0));
        assertEquals(OptionalInt.empty(), LobbyBounds.match(bounds, 5.0, 63.9, 5.0));
    }

    @Test
    void matchResolvesOverlapsByNearestMidpoint() {
        // Wide box 0 spans 0-100, small box 1 sits at 90-100: inside the
        // overlap the nearer midpoint (95) beats the far one (50).
        Map<Integer, LobbyBounds.Bound> bounds = Map.of(
                0, bound(0.0, 0.0, 0.0, 100.0, 255.0, 100.0),
                1, bound(90.0, 0.0, 90.0, 100.0, 255.0, 100.0));

        assertEquals(OptionalInt.of(1), LobbyBounds.match(bounds, 95.0, 65.0, 95.0));
        assertEquals(OptionalInt.of(0), LobbyBounds.match(bounds, 10.0, 65.0, 10.0));
        assertEquals(OptionalInt.empty(), LobbyBounds.match(bounds, 200.0, 65.0, 200.0));
    }

    @Test
    void matchSkipsNullBounds() {
        Map<Integer, LobbyBounds.Bound> bounds = new java.util.HashMap<>();
        bounds.put(0, null);
        bounds.put(1, bound(0.0, 64.0, 0.0, 10.0, 70.0, 10.0));

        assertEquals(OptionalInt.of(1), LobbyBounds.match(bounds, 5.0, 66.0, 5.0));
        assertEquals(OptionalInt.empty(), LobbyBounds.match(Map.of(), 5.0, 66.0, 5.0));
    }
}
