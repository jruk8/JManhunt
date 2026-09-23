package com.jruk8.jmanhunt.lobby.bounds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class LobbyBoundsTest {

    /** Builds a Bound exactly like the service does from stored corners. */
    private static LobbyBounds.Bound bound(int x1, int y1, int z1, int x2, int y2, int z2) {
        LobbyConfig.Position pos1 = LobbyConfig.Position.of(x1, y1, z1);
        LobbyConfig.Position pos2 = LobbyConfig.Position.of(x2, y2, z2);
        return new LobbyBounds.Bound(
                pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ());
    }

    private static OptionalInt matchAt(Map<Integer, LobbyBounds.Bound> bounds,
            double x, double y, double z) {
        return LobbyBounds.match(bounds, x, y, z);
    }

    @Test
    void triggerIncludesAllEdgeBlocks() {
        Map<Integer, LobbyBounds.Bound> bounds = Map.of(0, bound(5, 64, 5, 7, 66, 7));

        assertEquals(OptionalInt.of(0), matchAt(bounds, 7.5, 65.0, 6.5));
        assertEquals(OptionalInt.of(0), matchAt(bounds, 7.9, 65.0, 6.5));
        assertEquals(OptionalInt.of(0), matchAt(bounds, 5.0, 65.0, 6.5));
        assertEquals(OptionalInt.of(0), matchAt(bounds, 6.5, 66.9, 6.5));
        assertEquals(OptionalInt.of(0), matchAt(bounds, 6.5, 64.0, 6.5));
        assertEquals(OptionalInt.of(0), matchAt(bounds, 6.5, 65.0, 7.9));
        assertEquals(OptionalInt.of(0), matchAt(bounds, 6.5, 65.0, 5.0));
        assertEquals(OptionalInt.of(0), matchAt(bounds, 7.9, 66.9, 7.9));
    }

    @Test
    void triggerExcludesFirstBlockOutsideEveryFace() {
        Map<Integer, LobbyBounds.Bound> bounds = Map.of(0, bound(5, 64, 5, 7, 66, 7));

        assertTrue(matchAt(bounds, 8.0, 65.0, 6.5).isEmpty());
        assertTrue(matchAt(bounds, 4.99, 65.0, 6.5).isEmpty());
        assertTrue(matchAt(bounds, 6.5, 67.0, 6.5).isEmpty());
        assertTrue(matchAt(bounds, 6.5, 63.99, 6.5).isEmpty());
        assertTrue(matchAt(bounds, 6.5, 65.0, 8.0).isEmpty());
        assertTrue(matchAt(bounds, 6.5, 65.0, 4.99).isEmpty());
    }

    @Test
    void edgePointsEncloseTheContainedBlocks() {
        List<double[]> points =
                LobbyBounds.edgePoints(new LobbyBounds.Bound(5, 64, 5, 7, 66, 7), 100.0);

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        for (double[] point : points) {
            minX = Math.min(minX, point[0]);
            maxX = Math.max(maxX, point[0]);
        }
        assertEquals(5.0, minX);
        assertEquals(8.0, maxX);
    }

    @Test
    void reversedSelectionMatchesTheSameBox() {
        Map<Integer, LobbyBounds.Bound> bounds = Map.of(0, bound(7, 66, 7, 5, 64, 5));

        assertEquals(OptionalInt.of(0), matchAt(bounds, 7.5, 65.0, 6.5));
        assertEquals(OptionalInt.of(0), matchAt(bounds, 5.5, 65.0, 6.5));
        assertTrue(matchAt(bounds, 8.0, 65.0, 6.5).isEmpty());
    }

    @Test
    void duplicateOfFindsLowestOtherIdRegardlessOfCornerOrder() {
        Map<String, LobbyConfig.LobbyEntry> entries = new HashMap<>();
        entries.put("0", entryWithBounds(0, 64, 0, 2, 66, 2));
        entries.put("3", entryWithBounds(5, 64, 5, 7, 66, 7));
        entries.put("5", entryWithBounds(7, 66, 7, 5, 64, 5));
        entries.put("bogus", entryWithBounds(5, 64, 5, 7, 66, 7));
        entries.put("-1", entryWithBounds(5, 64, 5, 7, 66, 7));
        entries.put("9", new LobbyConfig.LobbyEntry());

        assertEquals(OptionalInt.of(3),
                LobbyBounds.duplicateOf(entries, 1, 5, 64, 5, 7, 66, 7));
        assertEquals(OptionalInt.of(3),
                LobbyBounds.duplicateOf(entries, 1, 7, 66, 7, 5, 64, 5));
        assertEquals(OptionalInt.of(5), LobbyBounds.duplicateOf(entries, 3, 5, 64, 5, 7, 66, 7));
        assertTrue(LobbyBounds.duplicateOf(entries, 1, 0, 64, 0, 9, 66, 9).isEmpty());
    }

    private static LobbyConfig.LobbyEntry entryWithBounds(
            int x1, int y1, int z1, int x2, int y2, int z2) {
        LobbyConfig.BoundsData bounds = new LobbyConfig.BoundsData();
        bounds.setPos1(LobbyConfig.Position.of(x1, y1, z1));
        bounds.setPos2(LobbyConfig.Position.of(x2, y2, z2));
        LobbyConfig.LobbyEntry entry = new LobbyConfig.LobbyEntry();
        entry.setBounds(bounds);
        return entry;
    }
}
