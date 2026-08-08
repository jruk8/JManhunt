package com.jruk8.jmanhunt.settings.world_engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private void assertCell(long index, long expectedX, long expectedZ) {
        SpiralCoordinateMapper.CellCoordinate coordinate = SpiralCoordinateMapper.toCoordinate(index);
        assertEquals(expectedX, coordinate.x());
        assertEquals(expectedZ, coordinate.z());
    }
}
