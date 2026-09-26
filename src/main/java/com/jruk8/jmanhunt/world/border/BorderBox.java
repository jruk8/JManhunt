package com.jruk8.jmanhunt.world.border;

/** XZ rectangle a pseudoborder is rendered from. */
public record BorderBox(double minX, double maxX, double minZ, double maxZ) {

    /**
     * Box for a match cell: center plus or minus half size. The nether
     * uses the same 1/8 scaling as the enforcement guard.
     */
    public static BorderBox forCell(double centerX, double centerZ, double halfSize, double scale) {
        return new BorderBox(
                (centerX - halfSize) / scale,
                (centerX + halfSize) / scale,
                (centerZ - halfSize) / scale,
                (centerZ + halfSize) / scale);
    }
}
