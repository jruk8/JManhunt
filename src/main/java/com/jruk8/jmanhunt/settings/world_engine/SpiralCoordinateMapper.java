package com.jruk8.jmanhunt.settings.world_engine;

public final class SpiralCoordinateMapper {
    private SpiralCoordinateMapper() {}

    public static CellCoordinate toCoordinate(long index) {
        if (index <= 0L) {
            return new CellCoordinate(0L, 0L);
        }
        long x = 0L;
        long z = 0L;
        long stepLength = 1L;
        long moved = 0L;

        while (moved < index) {
            long right = Math.min(stepLength, index - moved);
            x += right;
            moved += right;
            if (moved >= index) {
                break;
            }

            long up = Math.min(stepLength, index - moved);
            z += up;
            moved += up;
            stepLength++;
            if (moved >= index) {
                break;
            }

            long left = Math.min(stepLength, index - moved);
            x -= left;
            moved += left;
            if (moved >= index) {
                break;
            }

            long down = Math.min(stepLength, index - moved);
            z -= down;
            moved += down;
            stepLength++;
        }
        return new CellCoordinate(x, z);
    }

    public record CellCoordinate(long x, long z) {}

    /**
     * Inverse of {@link #toCoordinate}: the spiral index for grid coordinates.
     * Layer {@code n} (cells with max(|x|, |z|) = n) starts at index
     * (2n-1)^2 and unwinds right edge up, top edge left, left edge down,
     * bottom edge right.
     */
    public static long toIndex(long x, long z) {
        long n = Math.max(Math.abs(x), Math.abs(z));
        if (n == 0L) {
            return 0L;
        }
        long side = 2L * n - 1L;
        long start = side * side;
        long t;
        if (x == n && z >= -(n - 1L) && z < n) {
            t = z + n - 1L;
        } else if (z == n) {
            t = side + n - x;
        } else if (x == -n) {
            t = 2L * side + n + 1L - z;
        } else {
            t = 3L * side + x + n + 2L;
        }
        return start + t;
    }
}
