package com.jruk8.jmanhunt.gui;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Scaling math: rows grow eight slots at a time within the chest range,
 * and the back arrow sits at row (rows - 1) / 2 of column 8.
 */
class ScalingLayoutTest {

    @Test
    void rowsGrowEightSlotsAtATime() {
        assertEquals(1, ScalingLayout.rowsFor(0));
        assertEquals(1, ScalingLayout.rowsFor(1));
        assertEquals(1, ScalingLayout.rowsFor(8));
        assertEquals(2, ScalingLayout.rowsFor(9));
        assertEquals(6, ScalingLayout.rowsFor(48));
        assertEquals(6, ScalingLayout.rowsFor(49));
        assertEquals(6, ScalingLayout.rowsFor(500));
    }

    @Test
    void backArrowSitsMidColumn() {
        assertEquals(8, ScalingLayout.backSlot(1));
        assertEquals(8, ScalingLayout.backSlot(2));
        assertEquals(17, ScalingLayout.backSlot(3));
        assertEquals(17, ScalingLayout.backSlot(4));
        assertEquals(26, ScalingLayout.backSlot(5));
        assertEquals(26, ScalingLayout.backSlot(6));
    }

    @Test
    void layoutMarksContentAndBack() {
        MenuLayout three = ScalingLayout.layout(3);

        assertEquals(27, three.size());
        assertEquals(24, three.contentSlots().size());
        assertEquals(List.of(17), three.slotsFor(ScalingLayout.BACK));
        for (int slot : three.contentSlots()) {
            assertEquals(false, slot % 9 == 8, "content avoids column 8: " + slot);
        }
        assertEquals(24, ScalingLayout.capacity(3));
        assertEquals(8, ScalingLayout.capacity(1));
        assertEquals(48, ScalingLayout.capacity(6));
    }

    @Test
    void rowsClampToChestRange() {
        assertEquals(1, ScalingLayout.layout(0).rowCount());
        assertEquals(6, ScalingLayout.layout(99).rowCount());
        assertEquals(8, ScalingLayout.backSlot(0));
        assertEquals(26, ScalingLayout.backSlot(99));
    }
}
