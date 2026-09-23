package com.jruk8.jmanhunt.lobby.bounds;

import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.OptionalInt;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Pure lobby boundary-box matching: containment plus overlap resolution.
 * Works on plain coordinates only, so unit tests never touch Bukkit.
 */
public final class LobbyBounds {

    /** One lobby's bounds as two opposite corners. */
    public record Bound(double x1, double y1, double z1, double x2, double y2, double z2) {
    }

    private LobbyBounds() {
    }

    /**
     * Lobby whose bounds contain the point, if any. Block coordinates
     * are compared, so fractional positions land in their block; when
     * several boxes contain the point, the one whose midpoint is
     * nearest wins. Pure for tests.
     */
    public static OptionalInt match(Map<Integer, Bound> bounds, double x, double y, double z) {
        long blockX = (long) Math.floor(x);
        long blockY = (long) Math.floor(y);
        long blockZ = (long) Math.floor(z);
        Integer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<Integer, Bound> entry : bounds.entrySet()) {
            Bound bound = entry.getValue();
            if (bound == null || !contains(bound, blockX, blockY, blockZ)) {
                continue;
            }
            double distance = midpointDistanceSquared(bound, x, y, z);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }
        return best == null ? OptionalInt.empty() : OptionalInt.of(best);
    }

    /** True when the block sits inside the box corners, inclusive. Pure for tests. */
    public static boolean contains(Bound bound, long x, long y, long z) {
        return between(bound.x1(), bound.x2(), x)
                && between(bound.y1(), bound.y2(), y)
                && between(bound.z1(), bound.z2(), z);
    }

    /**
     * Lowest id of another lobby whose box exactly equals the given
     * corners (normalized, so corner order does not matter), if any.
     * Partial entries and negative ids never count. Pure for tests.
     */
    public static OptionalInt duplicateOf(Map<String, LobbyConfig.LobbyEntry> entries, int selfId,
            int x1, int y1, int z1, int x2, int y2, int z2) {
        long[] want = normalizedBox(x1, y1, z1, x2, y2, z2);
        Integer best = null;
        for (Map.Entry<String, LobbyConfig.LobbyEntry> entry : entries.entrySet()) {
            int id;
            try {
                id = Integer.parseInt(entry.getKey().trim());
            } catch (NumberFormatException expected) {
                continue;
            }
            if (id < 0 || id == selfId || entry.getValue() == null
                    || entry.getValue().getBounds() == null) {
                continue;
            }
            LobbyConfig.Position pos1 = entry.getValue().getBounds().getPos1();
            LobbyConfig.Position pos2 = entry.getValue().getBounds().getPos2();
            if (pos1 == null || pos2 == null) {
                continue;
            }
            long[] have = normalizedBox((long) pos1.getX(), (long) pos1.getY(), (long) pos1.getZ(),
                    (long) pos2.getX(), (long) pos2.getY(), (long) pos2.getZ());
            if (Arrays.equals(want, have) && (best == null || id < best)) {
                best = id;
            }
        }
        return best == null ? OptionalInt.empty() : OptionalInt.of(best);
    }

    private static long[] normalizedBox(long x1, long y1, long z1, long x2, long y2, long z2) {
        return new long[]{Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)};
    }

    /**
     * Points along the box's 12 edges as {x, y, z} triples, spaced
     * {@code step} blocks apart. Corners repeat across edges. Max faces
     * sit at max plus one so the drawn box encloses the contained
     * blocks. Pure for tests.
     */
    public static List<double[]> edgePoints(Bound bound, double step) {
        double x1 = Math.min(bound.x1(), bound.x2());
        double x2 = Math.max(bound.x1(), bound.x2()) + 1.0;
        double y1 = Math.min(bound.y1(), bound.y2());
        double y2 = Math.max(bound.y1(), bound.y2()) + 1.0;
        double z1 = Math.min(bound.z1(), bound.z2());
        double z2 = Math.max(bound.z1(), bound.z2()) + 1.0;
        List<double[]> points = new ArrayList<>();
        for (double y : new double[]{y1, y2}) {
            for (double z : new double[]{z1, z2}) {
                walkEdge(points, x1, y, z, x2, y, z, step);
            }
        }
        for (double x : new double[]{x1, x2}) {
            for (double z : new double[]{z1, z2}) {
                walkEdge(points, x, y1, z, x, y2, z, step);
            }
        }
        for (double x : new double[]{x1, x2}) {
            for (double y : new double[]{y1, y2}) {
                walkEdge(points, x, y, z1, x, y, z2, step);
            }
        }
        return points;
    }

    private static void walkEdge(List<double[]> points, double ax, double ay, double az,
            double bx, double by, double bz, double step) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int segments = Math.max(1, (int) Math.ceil(length / step));
        for (int index = 0; index <= segments; index++) {
            double t = (double) index / segments;
            points.add(new double[]{ax + dx * t, ay + dy * t, az + dz * t});
        }
    }

    /** Squared distance from the point to the nearest point in/on the box. Pure for tests. */
    public static double distanceSquaredToBox(Bound bound, double x, double y, double z) {
        double dx = axisDistance(bound.x1(), bound.x2(), x);
        double dy = axisDistance(bound.y1(), bound.y2(), y);
        double dz = axisDistance(bound.z1(), bound.z2(), z);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double a, double b, double value) {
        double lo = Math.min(a, b);
        double hi = Math.max(a, b);
        if (value < lo) {
            return lo - value;
        }
        if (value > hi) {
            return value - hi;
        }
        return 0.0;
    }

    private static boolean between(double a, double b, long value) {
        return value >= Math.min(a, b) && value <= Math.max(a, b);
    }

    /** Squared distance from the point to the box midpoint. Pure for tests. */
    public static double midpointDistanceSquared(Bound bound, double x, double y, double z) {
        double dx = (bound.x1() + bound.x2()) / 2.0 - x;
        double dy = (bound.y1() + bound.y2()) / 2.0 - y;
        double dz = (bound.z1() + bound.z2()) / 2.0 - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
