package com.jruk8.jmanhunt.world.border;

/**
 * The four pseudoborder walls. There is no floor or ceiling: match cells
 * are unbounded vertically, so only the wall edges render. Index order
 * decides seam ownership: the lowest-index wall owns each shared corner
 * edge, which means the X walls render their full span while the Z walls
 * skip both end columns.
 */
public enum BorderPlane {
    NEG_X(0, true),
    POS_X(1, true),
    NEG_Z(2, false),
    POS_Z(3, false);

    private final int index;
    private final boolean xFixed;

    BorderPlane(int index, boolean xFixed) {
        this.index = index;
        this.xFixed = xFixed;
    }

    public int index() {
        return index;
    }

    /** True for the x = const walls, false for the z = const walls. */
    public boolean xFixed() {
        return xFixed;
    }

    /** Wall position: min/max X for X walls, min/max Z for Z walls. */
    public double fixed(BorderBox box) {
        return switch (this) {
            case NEG_X -> box.minX();
            case POS_X -> box.maxX();
            case NEG_Z -> box.minZ();
            case POS_Z -> box.maxZ();
        };
    }

    /** Wall span start along the wall axis: min Z for X walls, min X for Z walls. */
    public double spanMin(BorderBox box) {
        return xFixed ? box.minZ() : box.minX();
    }

    /** Wall span end along the wall axis: max Z for X walls, max X for Z walls. */
    public double spanMax(BorderBox box) {
        return xFixed ? box.maxZ() : box.maxX();
    }

    /**
     * True when a vertex at wall coordinate {@code u} sits on a corner
     * edge owned by a lower-index wall and must be skipped. Only the Z
     * walls skip: both their corners are owned by the X walls.
     */
    public boolean skipsEdge(double u, BorderBox box) {
        return !xFixed && (u == box.minX() || u == box.maxX());
    }
}
