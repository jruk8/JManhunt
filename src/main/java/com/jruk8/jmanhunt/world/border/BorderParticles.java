package com.jruk8.jmanhunt.world.border;

import org.bukkit.Color;

/** Pure pseudoborder particle math: pulse gates and color helpers. */
public final class BorderParticles {
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double SECONDS_PER_TICK = 0.05;

    private BorderParticles() {
    }

    /**
     * Wall color as unit RGB offsets for the count-0 color protocol,
     * one double per channel in [0,1].
     */
    public static double[] colorOffsets(Color color) {
        return new double[] {color.getRed() / 255.0, color.getGreen() / 255.0,
                color.getBlue() / 255.0};
    }

    /**
     * INTERVAL gate: visible on pulse ticks of the configured interval,
     * identical whatever direction the player looks. An interval of 0
     * shows every tick.
     */
    public static boolean intervalVisible(long tick, double intervalSeconds) {
        if (intervalSeconds <= 0.0) {
            return true;
        }
        long period = Math.max(1L, Math.round(intervalSeconds * TICKS_PER_SECOND));
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
