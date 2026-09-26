package com.jruk8.jmanhunt.world.border;

import org.bukkit.Color;

/** Pure pseudoborder particle math: thinning, hashes, and pulse gates. */
public final class BorderParticles {
    /** Shown fraction at the render-radius edge; ramps to 1.0 up close. */
    public static final double EDGE_FRACTION = 0.3;
    /** Viewing angle past which the interval pulse stays at its slowest. */
    public static final double FALLOFF_ANGLE_DEGREES = 30.0;
    /** Slowest interval as a multiple of the configured one. */
    public static final double FALLOFF_FACTOR = 2.0;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double SECONDS_PER_TICK = 0.05;

    private BorderParticles() {
    }

    /**
     * Shown fraction for a wall at a perpendicular distance: 1.0 against
     * the wall, lerping down to 0.3 at the render radius edge.
     */
    public static double thinningFraction(double distance, double radius) {
        if (radius <= 0.0 || distance <= 0.0) {
            return 1.0;
        }
        if (distance >= radius) {
            return EDGE_FRACTION;
        }
        return EDGE_FRACTION + (1.0 - EDGE_FRACTION) * (1.0 - distance / radius);
    }

    /**
     * Stable value in [0,1) for a vertex from its exact world
     * coordinates. A vertex renders while the hash sits below the current
     * thinning fraction, so approaching players only ever gain vertices.
     */
    public static double hash01(double x, double y, double z) {
        long mixed = Double.doubleToLongBits(x) * 0x9E3779B97F4A7C15L
                ^ Double.doubleToLongBits(y) * 0xBF58476D1CE4E5B9L
                ^ Double.doubleToLongBits(z) * 0x94D049BB133111EBL;
        return (splitmix64(mixed) >>> 11) * 0x1p-53;
    }

    /**
     * SplitMix64 (Steele, Lea, and Flood, public domain): one cheap
     * avalanche mix so adjacent vertices scatter across [0,1).
     */
    private static long splitmix64(long value) {
        long state = value + 0x9E3779B97F4A7C15L;
        state = (state ^ (state >>> 30)) * 0xBF58476D1CE4E5B9L;
        state = (state ^ (state >>> 27)) * 0x94D049BB133111EBL;
        return state ^ (state >>> 31);
    }

    /**
     * Angle in degrees between the look direction and the wall normal, 0
     * looking straight at the wall to 90 looking along it.
     */
    public static double viewingAngleDegrees(double lookX, double lookZ, BorderPlane plane) {
        double dot = Math.abs(plane.xFixed() ? lookX : lookZ);
        return Math.toDegrees(Math.acos(Math.clamp(dot, 0.0, 1.0)));
    }

    /**
     * INTERVAL gate: visible on pulse ticks of the angle-adjusted
     * interval, which lerps from the configured interval looking straight
     * at the wall to twice that at 30 degrees off and beyond. An
     * interval of 0 shows every tick.
     */
    public static boolean intervalVisible(long tick, double intervalSeconds, double angleDegrees) {
        if (intervalSeconds <= 0.0) {
            return true;
        }
        double factor = 1.0 + (FALLOFF_FACTOR - 1.0)
                * Math.min(Math.max(angleDegrees, 0.0) / FALLOFF_ANGLE_DEGREES, 1.0);
        long period = Math.max(1L, Math.round(intervalSeconds * factor * TICKS_PER_SECOND));
        return tick % period == 0;
    }

    /**
     * SINE_WAVE phase offset for a vertex: 2 pi times its projection
     * onto the travel direction, divided by the wave length. The
     * direction runs counter-clockwise from the wall horizontal when
     * facing the wall, so 0 travels along the wall and 90 travels up.
     */
    public static double wavePhase(double u, double v, double angleDegrees, double waveLength) {
        double radians = Math.toRadians(angleDegrees);
        double x = u * Math.cos(radians) + v * Math.sin(radians);
        return 2.0 * Math.PI * x / waveLength;
    }

    /**
     * SINE_WAVE gate on the shared tick clock. Subtracting the phase
     * makes the on/off edge travel along the configured direction as
     * time advances.
     */
    public static boolean waveVisible(double phase, double speedHz, long tick) {
        double time = tick * SECONDS_PER_TICK;
        return Math.sin(2.0 * Math.PI * speedHz * time - phase) > 0.0;
    }

    /** Parses #rrggbb or rrggbb; anything else falls back. */
    public static Color parseHexColor(String raw, Color fallback) {
        if (raw == null) {
            return fallback;
        }
        String hex = raw.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return fallback;
        }
        try {
            int rgb = Integer.parseInt(hex, 16);
            return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }
}
