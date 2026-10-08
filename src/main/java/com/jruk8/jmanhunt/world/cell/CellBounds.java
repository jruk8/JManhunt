package com.jruk8.jmanhunt.world.cell;

/**
 * Rectangular bounds of one world-engine cell (center on the cell
 * origin, size as diameter): the single box the pseudo-border guard
 * confines and the wall particles render. The Nether uses 1/8 scaling.
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
        CellCoordinate grid = SpiralCoordinateMapper.toCoordinate(cellIndex);
        double centerX = grid.x() * (double) cellSize;
        double centerZ = grid.z() * (double) cellSize;
        double diameter = startPhase ? startDiameter : cellSize;
        return new CellBounds(centerX, centerZ, diameter / 2.0);
    }

    /** Cell center X in overworld blocks. */
    public double centerX() {
        return centerX;
    }

    /** Cell center Z in overworld blocks. */
    public double centerZ() {
        return centerZ;
    }

    /** Half the confining diameter in overworld blocks. */
    public double halfSize() {
        return halfSize;
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
     * Nearest point inside the bounds: each escaped axis is pulled
     * {@code margin} blocks off its own edge, while an axis already
     * inside keeps its exact value. Null when already inside. Used
     * to rubber-band players back in.
     */
    public double[] clampInside(double x, double z, boolean nether, double margin) {
        double scale = nether ? 8.0 : 1.0;
        double scaledCenterX = centerX / scale;
        double scaledCenterZ = centerZ / scale;
        double half = halfSize / scale;
        boolean outX = Math.abs(x - scaledCenterX) > half;
        boolean outZ = Math.abs(z - scaledCenterZ) > half;
        if (!outX && !outZ) {
            return null;
        }
        double edge = Math.max(0.0, half - margin);
        double clampedX = outX ? scaledCenterX + Math.signum(x - scaledCenterX) * edge : x;
        double clampedZ = outZ ? scaledCenterZ + Math.signum(z - scaledCenterZ) * edge : z;
        return new double[]{clampedX, clampedZ};
    }
}
