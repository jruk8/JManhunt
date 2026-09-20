package com.jruk8.jmanhunt.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CellIndexCapTest {

    @Test
    void capIsSquaredSpan() {
        assertEquals(35_880_100L, WorldEngineService.maxCellIndex(10_000));
        assertEquals(1_435_204L, WorldEngineService.maxCellIndex(50_000));
        assertEquals(3_588_010_000_000_000L, WorldEngineService.maxCellIndex(1));
    }

    @Test
    void clampBounds() {
        assertEquals(0L, WorldEngineService.clampCellIndex(-5L, 100L));
        assertEquals(100L, WorldEngineService.clampCellIndex(500L, 100L));
        assertEquals(42L, WorldEngineService.clampCellIndex(42L, 100L));
    }
}
