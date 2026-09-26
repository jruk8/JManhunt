package com.jruk8.jmanhunt.world.border;

import com.jruk8.jmanhunt.world.border.BorderGrid.BorderVertex;
import org.junit.jupiter.api.Test;
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

    @Test
    void budgetKeepsNearestFirst() {
        List<BorderVertex> candidates = List.of(
                new BorderVertex(0, 64, 5, 5.0, 5, 64, BorderPlane.NEG_Z),
                new BorderVertex(0, 64, 1, 1.0, 1, 64, BorderPlane.NEG_Z),
                new BorderVertex(0, 64, 3, 3.0, 3, 64, BorderPlane.NEG_Z),
                new BorderVertex(0, 64, 2, 2.0, 2, 64, BorderPlane.NEG_Z));

        List<BorderVertex> kept = BorderGrid.truncateNearest(candidates, 2);

        assertEquals(List.of(1.0, 2.0), kept.stream().map(BorderVertex::planeDistance).toList());
    }

    @Test
    void budgetBreaksTiesByCoordinates() {
        List<BorderVertex> candidates = List.of(
                new BorderVertex(9, 64, 2, 2.0, 9, 64, BorderPlane.NEG_Z),
                new BorderVertex(1, 64, 2, 2.0, 1, 64, BorderPlane.NEG_Z));

        List<BorderVertex> kept = BorderGrid.truncateNearest(candidates, 1);

        assertEquals(1.0, kept.get(0).x(), 0.0);
    }

    @Test
    void budgetKeepsAllWhenUnderCap() {
        List<BorderVertex> candidates = List.of(
                new BorderVertex(0, 64, 3, 3.0, 3, 64, BorderPlane.NEG_Z),
                new BorderVertex(0, 64, 1, 1.0, 1, 64, BorderPlane.NEG_Z));

        assertEquals(2, BorderGrid.truncateNearest(candidates, 100).size());
        assertTrue(BorderGrid.truncateNearest(candidates, 0).isEmpty());
    }
}
