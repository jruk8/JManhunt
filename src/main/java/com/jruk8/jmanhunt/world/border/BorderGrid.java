package com.jruk8.jmanhunt.world.border;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Per-wall particle grids for pseudoborder rendering. */
public final class BorderGrid {
    private BorderGrid() {
    }

    /** One grid vertex: world position, reused plane distance, and wall coordinates. */
    public record BorderVertex(double x, double y, double z, double planeDistance,
            double u, double v, BorderPlane plane) {
    }

    /**
     * Grid patch where one wall meets a player. Vertices sit on
     * world-aligned multiples of the spacing within the render radius of
     * the player's projection onto the wall (horizontal) and of the
     * player's height (vertical), clipped to the wall span and world
     * height, minus corner edges owned by lower-index walls. Empty when
     * the wall is out of render radius.
     */
    public static List<BorderVertex> verticesForPlane(BorderPlane plane, BorderBox box,
            double playerX, double playerY, double playerZ,
            int spacing, double radius, int minY, int maxY) {
        double fixed = plane.fixed(box);
        double planeDistance = Math.abs((plane.xFixed() ? playerX : playerZ) - fixed);
        if (planeDistance > radius) {
            return List.of();
        }
        double along = plane.xFixed() ? playerZ : playerX;
        double spanMin = plane.spanMin(box);
        double spanMax = plane.spanMax(box);
        List<BorderVertex> vertices = new ArrayList<>();
        for (long u = ceilMultiple(along - radius, spacing); u <= along + radius; u += spacing) {
            if (u < spanMin || u > spanMax || plane.skipsEdge(u, box)) {
                continue;
            }
            for (long v = ceilMultiple(playerY - radius, spacing); v <= playerY + radius; v += spacing) {
                if (v < minY || v >= maxY) {
                    continue;
                }
                double x = plane.xFixed() ? fixed : u;
                double z = plane.xFixed() ? u : fixed;
                vertices.add(new BorderVertex(x, v, z, planeDistance, u, v, plane));
            }
        }
        return vertices;
    }

    /** Smallest multiple of spacing at or above value. Pure for tests. */
    static long ceilMultiple(double value, int spacing) {
        return (long) Math.ceil(value / spacing) * spacing;
    }

    /**
     * Budget truncation: nearest plane distance first with a coordinate
     * tie-break, keeping at most {@code budget} vertices. Pure for tests.
     */
    public static List<BorderVertex> truncateNearest(List<BorderVertex> candidates, int budget) {
        List<BorderVertex> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(BorderVertex::planeDistance)
                .thenComparingDouble(BorderVertex::x)
                .thenComparingDouble(BorderVertex::y)
                .thenComparingDouble(BorderVertex::z));
        return sorted.subList(0, Math.min(sorted.size(), Math.max(0, budget)));
    }
}
