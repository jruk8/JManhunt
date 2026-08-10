package com.jruk8.jmanhunt;

import org.bukkit.block.structure.StructureRotation;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SchematicPivotTest {

    @Test
    void savePivotOffsetComputesOffsetFromCorner() {
        // Door is 3 blocks along X, same Y, same Z as the corner
        int[] offset = SchematicPivot.savePivotOffset(
                loc(0, 0, 0),
                loc(3, 0, 0)
        );
        assertArrayEquals(new int[]{3, 0, 0}, offset);
    }

    @Test
    void savePivotOffsetHandlesNegativeOffsets() {
        // Door is behind and below the corner
        int[] offset = SchematicPivot.savePivotOffset(
                loc(5, 10, 5),
                loc(2, 7, 3)
        );
        assertArrayEquals(new int[]{-3, -3, -2}, offset);
    }

    @Test
    void rotateOffsetNoneIsIdentity() {
        int[] offset = {3, 0, 0};
        BlockVector size = new BlockVector(10, 5, 8);
        assertArrayEquals(new int[]{3, 0, 0},
                SchematicPivot.rotateOffset(offset, size, StructureRotation.NONE));
    }

    @Test
    void rotateOffsetClockwise90() {
        // (x, y, z) -> (length - 1 - z, y, x)
        int[] offset = {3, 0, 0};
        BlockVector size = new BlockVector(10, 5, 8);
        assertArrayEquals(new int[]{7, 0, 3},
                SchematicPivot.rotateOffset(offset, size, StructureRotation.CLOCKWISE_90));
    }

    @Test
    void rotateOffsetClockwise180() {
        // (x, y, z) -> (width - 1 - x, y, length - 1 - z)
        int[] offset = {3, 0, 0};
        BlockVector size = new BlockVector(10, 5, 8);
        assertArrayEquals(new int[]{6, 0, 7},
                SchematicPivot.rotateOffset(offset, size, StructureRotation.CLOCKWISE_180));
    }

    @Test
    void rotateOffsetCounterclockwise90() {
        // (x, y, z) -> (z, y, width - 1 - x)
        int[] offset = {3, 0, 0};
        BlockVector size = new BlockVector(10, 5, 8);
        assertArrayEquals(new int[]{0, 0, 6},
                SchematicPivot.rotateOffset(offset, size, StructureRotation.COUNTERCLOCKWISE_90));
    }

    @Test
    void rotateOffsetPreservesY() {
        int[] offset = {2, 4, 1};
        BlockVector size = new BlockVector(10, 5, 8);
        for (StructureRotation rotation : StructureRotation.values()) {
            int[] result = SchematicPivot.rotateOffset(offset, size, rotation);
            assertEquals(4, result[1], "Y should be preserved for " + rotation);
        }
    }

    @Test
    void yawToRotationNorth() {
        assertEquals(StructureRotation.NONE, SchematicPivot.yawToRotation(0f));
        assertEquals(StructureRotation.NONE, SchematicPivot.yawToRotation(-10f));
        assertEquals(StructureRotation.NONE, SchematicPivot.yawToRotation(350f));
    }

    @Test
    void yawToRotationEast() {
        assertEquals(StructureRotation.CLOCKWISE_90, SchematicPivot.yawToRotation(90f));
        assertEquals(StructureRotation.CLOCKWISE_90, SchematicPivot.yawToRotation(45f));
    }

    @Test
    void yawToRotationSouth() {
        assertEquals(StructureRotation.CLOCKWISE_180, SchematicPivot.yawToRotation(180f));
        assertEquals(StructureRotation.CLOCKWISE_180, SchematicPivot.yawToRotation(135f));
    }

    @Test
    void yawToRotationWest() {
        assertEquals(StructureRotation.COUNTERCLOCKWISE_90, SchematicPivot.yawToRotation(270f));
        assertEquals(StructureRotation.COUNTERCLOCKWISE_90, SchematicPivot.yawToRotation(225f));
    }

    @Test
    void yawToRotationBoundary315() {
        assertEquals(StructureRotation.NONE, SchematicPivot.yawToRotation(315f));
    }

    private static org.bukkit.Location loc(int x, int y, int z) {
        return new org.bukkit.Location(null, x, y, z);
    }
}
