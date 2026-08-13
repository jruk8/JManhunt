package com.jruk8.jmanhunt;

import org.bukkit.Location;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.util.BlockVector;

/**
 * Pure helpers for the schematic pivot point system. A pivot is the offset
 * of a "door" block from the structure's origin corner. When pasting, the
 * pivot is rotated with the structure so the door lands on the player.
 */
public final class SchematicPivot {
    private SchematicPivot() {
    }

    /**
     * Computes the pivot offset (in blocks) from the structure origin corner
     * to the door block.
     *
     * @param corner1 the origin corner used when filling the structure
     * @param doorLocation the door block's location
     * @return the pivot offset as {dx, dy, dz}
     */
    public static int[] savePivotOffset(Location corner1, Location doorLocation) {
        return new int[]{
                doorLocation.getBlockX() - corner1.getBlockX(),
                doorLocation.getBlockY() - corner1.getBlockY(),
                doorLocation.getBlockZ() - corner1.getBlockZ()
        };
    }

    /**
     * Rotates a pivot offset according to the given structure rotation.
     * The structure size is needed to correctly map coordinates for
     * 90-degree and 180-degree rotations.
     *
     * @param offset the original pivot offset {dx, dy, dz}
     * @param size the structure's size (width, height, length)
     * @param rotation the rotation to apply
     * @return the rotated pivot offset {dx, dy, dz}
     */
    public static int[] rotateOffset(int[] offset, BlockVector size, StructureRotation rotation) {
        int x = offset[0];
        int y = offset[1];
        int z = offset[2];
        int width = size.getBlockX();
        int length = size.getBlockZ();
        return switch (rotation) {
            case NONE -> new int[]{x, y, z};
            case CLOCKWISE_90 -> new int[]{length - 1 - z, y, x};
            case CLOCKWISE_180 -> new int[]{width - 1 - x, y, length - 1 - z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, y, width - 1 - x};
        };
    }

    /**
     * Maps a player's yaw to a StructureRotation so the structure's "front"
     * (the door's facing direction as saved) points back at the player.
     *
     * @param yaw the player's yaw in degrees
     * @return the matching StructureRotation
     */
    public static StructureRotation yawToRotation(float yaw) {
        float y = (yaw % 360 + 360) % 360;
        if (y >= 315 || y < 45) {
            return StructureRotation.NONE;
        }
        if (y >= 45 && y < 135) {
            return StructureRotation.CLOCKWISE_90;
        }
        if (y >= 135 && y < 225) {
            return StructureRotation.CLOCKWISE_180;
        }
        return StructureRotation.COUNTERCLOCKWISE_90;
    }
}
