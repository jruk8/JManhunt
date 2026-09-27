package com.jruk8.jmanhunt.world.border;

import com.jruk8.jmanhunt.world.border.BorderGrid.BorderVertex;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BorderGridTest {
    private static final BorderBox BOX = new BorderBox(0.0, 100.0, 0.0, 100.0);

    @Test
    void outOfRadiusWallRendersNothing() {
        assertTrue(BorderGrid.verticesForPlane(BorderPlane.NEG_Z, BOX,
                50.0, 64.0, 50.0, 2, 10.0, -64, 320).isEmpty());
    }

    @Test
    void wallPatchSitsOnLatticeInsideRadius() {
        List<BorderVertex> vertices = BorderGrid.verticesForPlane(BorderPlane.NEG_Z, BOX,
                50.0, 64.0, 8.0, 2, 10.0, -64, 320);

        assertEquals(121, vertices.size());
        for (BorderVertex vertex : vertices) {
            assertEquals(0.0, vertex.z(), 0.0);
            assertEquals(8.0, vertex.planeDistance(), 1e-9);
            assertEquals(BorderPlane.NEG_Z, vertex.plane());
            assertEquals(0L, Math.round(vertex.u()) % 2);
            assertEquals(0L, Math.round(vertex.v()) % 2);
            assertTrue(Math.abs(vertex.u() - 50.0) <= 10.0);
            assertTrue(Math.abs(vertex.v() - 64.0) <= 10.0);
        }
    }

    @Test
    void xWallsOwnCornersSoZWallsSkipEndColumns() {
        List<BorderVertex> negX = BorderGrid.verticesForPlane(BorderPlane.NEG_X, BOX,
                2.0, 64.0, 2.0, 1, 10.0, -64, 320);
        List<BorderVertex> negZ = BorderGrid.verticesForPlane(BorderPlane.NEG_Z, BOX,
                2.0, 64.0, 2.0, 1, 10.0, -64, 320);

        assertTrue(negX.stream().anyMatch(vertex -> vertex.z() == 0.0));
        assertTrue(negZ.stream().noneMatch(vertex -> vertex.x() == 0.0));
        assertTrue(negZ.stream().noneMatch(vertex -> vertex.x() == 100.0));
    }

    @Test
    void farCornerSeamBelongsToPosX() {
        List<BorderVertex> posX = BorderGrid.verticesForPlane(BorderPlane.POS_X, BOX,
                98.0, 64.0, 98.0, 1, 10.0, -64, 320);
        List<BorderVertex> posZ = BorderGrid.verticesForPlane(BorderPlane.POS_Z, BOX,
                98.0, 64.0, 98.0, 1, 10.0, -64, 320);

        assertTrue(posX.stream().anyMatch(vertex -> vertex.z() == 100.0));
        assertTrue(posZ.stream().noneMatch(vertex -> vertex.x() == 100.0));
    }

    @Test
    void verticalRangeClampsToWorldHeight() {
        List<BorderVertex> top = BorderGrid.verticesForPlane(BorderPlane.NEG_Z, BOX,
                50.0, 319.0, 8.0, 1, 10.0, -64, 320);
        List<BorderVertex> bottom = BorderGrid.verticesForPlane(BorderPlane.NEG_Z, BOX,
                50.0, -60.0, 8.0, 1, 10.0, -64, 320);

        assertEquals(319.0, top.stream().mapToDouble(BorderVertex::v).max().orElseThrow(), 0.0);
        assertEquals(-64.0, bottom.stream().mapToDouble(BorderVertex::v).min().orElseThrow(), 0.0);
    }

    @Test
    void ceilMultipleRoundsUpToLattice() {
        assertEquals(-10L, BorderGrid.ceilMultiple(-10.0, 2));
        assertEquals(-8L, BorderGrid.ceilMultiple(-9.5, 2));
        assertEquals(0L, BorderGrid.ceilMultiple(0.0, 2));
        assertEquals(4L, BorderGrid.ceilMultiple(3.0, 2));
        assertEquals(40L, BorderGrid.ceilMultiple(40.0, 2));
    }

    private static List<BorderVertex> wall(BorderPlane plane, int count, double distance) {
        ArrayList<BorderVertex> vertices = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            vertices.add(new BorderVertex(index, 64, index, distance, index, 64, plane));
        }
        return vertices;
    }

    private static long planeCount(List<BorderVertex> kept, BorderPlane plane) {
        return kept.stream().filter(vertex -> vertex.plane() == plane).count();
    }

    @Test
    void budgetCoversEveryWallEvenly() {
        ArrayList<BorderVertex> candidates = new ArrayList<>();
        for (BorderPlane plane : BorderPlane.values()) {
            candidates.addAll(wall(plane, 50, 5.0));
        }

        List<BorderVertex> kept = BorderGrid.spreadBudget(candidates, 100);

        assertEquals(100, kept.size());
        for (BorderPlane plane : BorderPlane.values()) {
            assertEquals(25, planeCount(kept, plane));
        }
    }

    @Test
    void budgetStrideSamplesAcrossEachWall() {
        List<BorderVertex> kept = BorderGrid.spreadBudget(wall(BorderPlane.NEG_Z, 10, 5.0), 4);

        assertEquals(List.of(0.0, 2.0, 5.0, 7.0),
                kept.stream().map(BorderVertex::x).toList());
    }

    @Test
    void budgetWaterFillsSmallWallsFirst() {
        ArrayList<BorderVertex> candidates = new ArrayList<>();
        candidates.addAll(wall(BorderPlane.NEG_Z, 5, 9.0));
        candidates.addAll(wall(BorderPlane.POS_Z, 500, 1.0));

        List<BorderVertex> kept = BorderGrid.spreadBudget(candidates, 100);

        assertEquals(100, kept.size());
        assertEquals(5, planeCount(kept, BorderPlane.NEG_Z));
        assertEquals(95, planeCount(kept, BorderPlane.POS_Z));
    }

    @Test
    void budgetNeverDuplicatesAndStaysStable() {
        ArrayList<BorderVertex> candidates = new ArrayList<>();
        for (BorderPlane plane : BorderPlane.values()) {
            candidates.addAll(wall(plane, 500, 5.0));
        }

        List<BorderVertex> first = BorderGrid.spreadBudget(candidates, 100);
        List<BorderVertex> second = BorderGrid.spreadBudget(candidates, 100);

        assertEquals(100, first.size());
        assertEquals(100, new HashSet<>(first).size());
        assertEquals(first, second);
    }

    @Test
    void budgetKeepsAllWhenUnderCap() {
        List<BorderVertex> candidates = List.of(
                new BorderVertex(0, 64, 3, 3.0, 3, 64, BorderPlane.NEG_Z),
                new BorderVertex(0, 64, 1, 1.0, 1, 64, BorderPlane.NEG_Z));

        assertEquals(2, BorderGrid.spreadBudget(candidates, 100).size());
        assertTrue(BorderGrid.spreadBudget(candidates, 0).isEmpty());
        assertTrue(BorderGrid.spreadBudget(candidates, -5).isEmpty());
        assertTrue(BorderGrid.spreadBudget(List.of(), 100).isEmpty());
    }
}
