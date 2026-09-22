package com.jruk8.jmanhunt.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jruk8.jmanhunt.world.cell.WorldCellService;

class CellIndexCapTest {

    @Test
    void capIsSquaredSpan() {
        assertEquals(35_880_100L, WorldCellService.maxCellIndex(10_000));
        assertEquals(1_435_204L, WorldCellService.maxCellIndex(50_000));
        assertEquals(3_588_010_000_000_000L, WorldCellService.maxCellIndex(1));
    }

    @Test
    void clampBounds() {
        assertEquals(0L, WorldCellService.clampCellIndex(-5L, 100L));
        assertEquals(100L, WorldCellService.clampCellIndex(500L, 100L));
        assertEquals(42L, WorldCellService.clampCellIndex(42L, 100L));
    }
}
