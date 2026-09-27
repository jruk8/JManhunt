package com.jruk8.jmanhunt.world.border;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
     * Budget spread: every wall with candidates gets a fair quota of the
     * budget (small walls keep everything, the rest splits evenly), and
     * each wall's quota is stride-sampled across its full patch. Low
     * budgets cover all walls instead of freezing on one corner, output
     * is stable across ticks for stable input, and no vertex repeats.
     * Pure for tests.
     */
    public static List<BorderVertex> spreadBudget(List<BorderVertex> candidates, int budget) {
        int cap = Math.max(0, budget);
        List<List<BorderVertex>> groups = groupByPlane(candidates);
        if (groups.isEmpty() || cap == 0) {
            return List.of();
        }
        int total = 0;
        for (List<BorderVertex> group : groups) {
            total += group.size();
        }
        if (total <= cap) {
            List<BorderVertex> all = new ArrayList<>(total);
            for (List<BorderVertex> group : groups) {
                all.addAll(group);
            }
            return all;
        }
        Map<List<BorderVertex>, Integer> quota = fillQuotas(groups, cap);
        List<BorderVertex> shown = new ArrayList<>(cap);
        for (List<BorderVertex> group : groups) {
            shown.addAll(stride(group, quota.get(group)));
        }
        return shown;
    }

    /** Non-empty per-wall groups in plane order, generation order kept. */
    private static List<List<BorderVertex>> groupByPlane(List<BorderVertex> candidates) {
        List<List<BorderVertex>> groups = new ArrayList<>();
        for (BorderPlane plane : BorderPlane.values()) {
            List<BorderVertex> group = new ArrayList<>();
            for (BorderVertex candidate : candidates) {
                if (candidate.plane() == plane) {
                    group.add(candidate);
                }
            }
            if (!group.isEmpty()) {
                groups.add(group);
            }
        }
        return groups;
    }

    /**
     * Water-filling quotas: smallest walls keep everything, the rest of
     * the budget splits evenly. Stable: ties keep plane order.
     */
    private static Map<List<BorderVertex>, Integer> fillQuotas(
            List<List<BorderVertex>> groups, int cap) {
        List<List<BorderVertex>> bySize = new ArrayList<>(groups);
        bySize.sort(Comparator.comparingInt(List::size));
        Map<List<BorderVertex>, Integer> quota = new IdentityHashMap<>();
        int remaining = cap;
        int left = bySize.size();
        for (List<BorderVertex> group : bySize) {
            int take = Math.min(group.size(), remaining / left);
            quota.put(group, take);
            remaining -= take;
            left--;
        }
        return quota;
    }

    /** Even stride across a group: quota distinct vertices, first kept. */
    private static List<BorderVertex> stride(List<BorderVertex> group, int quota) {
        if (quota >= group.size()) {
            return new ArrayList<>(group);
        }
        List<BorderVertex> picked = new ArrayList<>(quota);
        for (int index = 0; index < quota; index++) {
            picked.add(group.get((int) ((long) index * group.size() / quota)));
        }
        return picked;
    }
}
