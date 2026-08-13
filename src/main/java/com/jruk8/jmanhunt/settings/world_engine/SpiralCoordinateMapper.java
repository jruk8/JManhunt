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
}
