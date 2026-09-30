package com.jruk8.jmanhunt.compass;

import java.util.Locale;

/**
 * Pure signal inaccuracy math for compass tracking. Bukkit-free:
 * callers resolve the config values, check the threshold gate, and
 * sample a uniform point from the error donut around the true spot.
 * The inner deadzone is a share of the outer radius, so 0.5 keeps
 * every sample outside half the outer radius while 0 allows samples
 * arbitrarily close to the true spot.
 */
public final class SignalInaccuracy {

    private SignalInaccuracy() {
    }

    /** Largest deadzone below 1: 1 itself would leave no ring to sample. */
    static final double MAX_DEADZONE = 0.999999;

    /** Default deadzone used when the configured value is not a number. */
    static final double DEFAULT_DEADZONE = 0.4;

    /** Smallest drift: 0 would collapse the donut onto the true spot. */
    static final double MIN_DRIFT = 0.01;

    /** Default drift used when the configured value is not a number. */
    static final double DEFAULT_DRIFT = 0.6;

    /** Which readout the sampled error moves. */
    public enum InaccurateOn {
        NEEDLE,
        DISTANCE_FEEDBACK,
        BOTH
    }

    /**
     * Lenient target parse: trims and uppercases, anything unknown
     * (including null) falls back to BOTH. Pure.
     */
    public static InaccurateOn parseTarget(String raw) {
        if (raw == null) {
            return InaccurateOn.BOTH;
        }
        try {
            return InaccurateOn.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return InaccurateOn.BOTH;
        }
    }

    /** Raw resolved config; each consumer clamps what it reads. */
    public record Config(boolean enabled, double deadzone, double drift, double minDistance,
            double maxDistance, InaccurateOn target) {
    }

    /**
     * Deadzone clamped to [0, 1): negatives pin to 0, values at or
     * above 1 pin just below 1 so a ring always remains, and NaN
     * maps to the 0.4 default. Pure.
     */
    public static double clampDeadzone(double value) {
        if (!Double.isFinite(value)) {
            return Double.isNaN(value) ? DEFAULT_DEADZONE : MAX_DEADZONE;
        }
        return Math.min(MAX_DEADZONE, Math.max(0.0, value));
    }

    /**
     * Drift clamped to [0.01, 1]: 0 or negatives would pin the needle
     * onto the truth, so they pin to 0.01 instead; NaN maps to the 0.6
     * default. Pure.
     */
    public static double clampDrift(double value) {
        if (Double.isNaN(value)) {
            return DEFAULT_DRIFT;
        }
        if (!Double.isFinite(value)) {
            return value > 0.0 ? 1.0 : MIN_DRIFT;
        }
        return Math.min(1.0, Math.max(MIN_DRIFT, value));
    }

    /**
     * Effective min-distance gate: a positive min below a set max wins,
     * a min at or above a set max clamps down to max, and any
     * non-positive min (including -1 and junk like 0) disables the gate
     * by returning -1. Pure.
     */
    public static double effectiveMin(double minDistance, double maxDistance) {
        if (minDistance <= 0.0 || Double.isNaN(minDistance)) {
            return -1.0;
        }
        if (maxDistance > 0.0 && !Double.isNaN(maxDistance) && minDistance >= maxDistance) {
            return maxDistance;
        }
        return minDistance;
    }

    /**
     * True when the error applies at this distance: the feature is on
     * and the distance passes the effective min gate. Pure.
     */
    public static boolean applies(Config config, double distance) {
        if (!config.enabled() || !(distance > 0.0)) {
            return false;
        }
        double min = effectiveMin(config.minDistance(), config.maxDistance());
        return min < 0.0 || distance > min;
    }

    /**
     * Outer donut radius: clamped drift times the distance, capped at
     * max-distance when max is set to a positive value. Pure.
     */
    public static double outerRadius(double distance, double drift, double maxDistance) {
        double capped = distance;
        if (maxDistance > 0.0 && !Double.isNaN(maxDistance)) {
            capped = Math.min(distance, maxDistance);
        }
        return clampDrift(drift) * Math.max(0.0, capped);
    }

    /**
     * Uniform donut sample around the true spot: theta = 2*PI*u1 and
     * r = sqrt(Rin^2 + u2*(Rout^2 - Rin^2)) with Rin = deadzone*outer,
     * converted with x = x0 + r*cos(theta), z = z0 + r*sin(theta).
     * Returns {x, z}. Pure.
     */
    public static double[] sample(double targetX, double targetZ, double outer, double deadzone,
            double u1, double u2) {
        double inner = clampDeadzone(deadzone) * Math.max(0.0, outer);
        double clamped = Math.max(0.0, outer);
        double radius = Math.sqrt(inner * inner + clampUnit(u2)
                * (clamped * clamped - inner * inner));
        double theta = 2.0 * Math.PI * clampUnit(u1);
        return new double[] {targetX + radius * Math.cos(theta),
                targetZ + radius * Math.sin(theta)};
    }

    /** Unit roll clamped to [0, 1]; NaN maps to 0. Pure. */
    static double clampUnit(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, value));
    }
}
