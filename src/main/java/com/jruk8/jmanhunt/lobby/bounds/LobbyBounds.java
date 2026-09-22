package com.jruk8.jmanhunt.lobby.bounds;

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
