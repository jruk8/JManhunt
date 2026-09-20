package com.jruk8.jmanhunt.settings.world_engine;

/**
 * Rectangular bounds of one world-engine cell, mirroring the real world
 * border geometry (center on the cell origin, size as diameter) so the
 * pseudo-border guard confines exactly what the border would. The Nether
 * uses the same 1/8 scaling as the border sync.
 */
public final class CellBounds {
    private final double centerX;
    private final double centerZ;
    private final double halfSize;

    private CellBounds(double centerX, double centerZ, double halfSize) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.halfSize = halfSize;
    }

    /**
     * Bounds for an overworld cell: the full cell size, or the start
     * diameter while the match is still in its start-border phase.
     */
    public static CellBounds forCell(long cellIndex, int cellSize, int startDiameter, boolean startPhase) {
        SpiralCoordinateMapper.CellCoordinate grid = SpiralCoordinateMapper.toCoordinate(cellIndex);
        double centerX = grid.x() * (double) cellSize;
        double centerZ = grid.z() * (double) cellSize;
        double diameter = startPhase ? startDiameter : cellSize;
        return new CellBounds(centerX, centerZ, diameter / 2.0);
    }

    public boolean contains(double x, double z) {
        return contains(x, z, false);
    }

    public boolean contains(double x, double z, boolean nether) {
        return outsideBy(x, z, nether) <= 0.0;
    }

    /**
     * Blocks beyond the edge along the worst axis; 0 when inside. Compare
     * against the damage buffer to decide border-style damage.
     */
    public double outsideBy(double x, double z, boolean nether) {
        double scale = nether ? 8.0 : 1.0;
        double beyond = Math.max(Math.abs(x - centerX / scale), Math.abs(z - centerZ / scale))
                - halfSize / scale;
        return Math.max(0.0, beyond);
    }

    /**
     * Nearest point inside the bounds, pulled {@code margin} blocks off the
     * edge; null when already inside. Used to rubber-band players back in.
     */
    public double[] clampInside(double x, double z, boolean nether, double margin) {
        double scale = nether ? 8.0 : 1.0;
        double scaledCenterX = centerX / scale;
        double scaledCenterZ = centerZ / scale;
        double reach = halfSize / scale - margin;
        if (reach <= 0.0) {
            return new double[]{scaledCenterX, scaledCenterZ};
        }
        double clampedX = Math.clamp(x, scaledCenterX - reach, scaledCenterX + reach);
        double clampedZ = Math.clamp(z, scaledCenterZ - reach, scaledCenterZ + reach);
        if (clampedX == x && clampedZ == z) {
            return null;
        }
        return new double[]{clampedX, clampedZ};
    }
}
