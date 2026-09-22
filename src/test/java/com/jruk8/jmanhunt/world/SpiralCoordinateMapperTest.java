package com.jruk8.jmanhunt.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jruk8.jmanhunt.world.cell.CellCoordinate;
import com.jruk8.jmanhunt.world.cell.SpiralCoordinateMapper;

class SpiralCoordinateMapperTest {
    @Test
    void mapsFirstCellsInSquareSpiralOrder() {
        assertCell(0, 0, 0);
        assertCell(1, 1, 0);
        assertCell(2, 1, 1);
        assertCell(3, 0, 1);
        assertCell(4, -1, 1);
        assertCell(5, -1, 0);
        assertCell(6, -1, -1);
        assertCell(7, 0, -1);
        assertCell(8, 1, -1);
        assertCell(9, 2, -1);
    }

    @Test
    void inverseMapsCoordinatesBackToIndex() {
        assertIndex(0, 0, 0);
        assertIndex(1, 1, 0);
        assertIndex(2, 1, 1);
        assertIndex(3, 0, 1);
        assertIndex(4, -1, 1);
        assertIndex(5, -1, 0);
        assertIndex(6, -1, -1);
        assertIndex(7, 0, -1);
        assertIndex(8, 1, -1);
        assertIndex(9, 2, -1);
        assertIndex(12, 2, 2);
        assertIndex(16, -2, 2);
        assertIndex(20, -2, -2);
        assertIndex(24, 2, -2);
    }

    @Test
    void roundTripsFirstLayers() {
        for (long index = 0; index < 500; index++) {
            CellCoordinate coordinate = SpiralCoordinateMapper.toCoordinate(index);
            assertEquals(index, SpiralCoordinateMapper.toIndex(coordinate.x(), coordinate.z()),
                    "index " + index);
        }
    }

    @Test
    void roundTripsNegativeQuadrants() {
        for (long x = -30; x <= 30; x++) {
            for (long z = -30; z <= 30; z++) {
                long index = SpiralCoordinateMapper.toIndex(x, z);
                CellCoordinate coordinate = SpiralCoordinateMapper.toCoordinate(index);
                assertEquals(x, coordinate.x(), "x at index " + index);
                assertEquals(z, coordinate.z(), "z at index " + index);
            }
        }
    }

    private void assertCell(long index, long expectedX, long expectedZ) {
        CellCoordinate coordinate = SpiralCoordinateMapper.toCoordinate(index);
        assertEquals(expectedX, coordinate.x());
        assertEquals(expectedZ, coordinate.z());
    }

    private void assertIndex(long expectedIndex, long x, long z) {
        assertEquals(expectedIndex, SpiralCoordinateMapper.toIndex(x, z));
    }
}
