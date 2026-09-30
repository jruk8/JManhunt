package com.jruk8.jmanhunt.compass;

import java.util.Locale;

/**
 * Pure accuracy readout math for tracking actionbars: stepped
 * percentages and lerped colors. Bukkit-free. Percents step in tens
 * and colors lerp on the stepped value (not the true one) so holders
 * cannot exploit sub-step precision.
 */
public final class CompassAccuracyRenderer {

    private CompassAccuracyRenderer() {
    }

    /** Tiny floor keeping a zero theoretical max from dividing by zero. */
    static final double MIN_THEORY = 1e-9;

    /** Fallback max-accuracy color used when the configured value is junk. */
    static final String DEFAULT_ACCURATE = "#63d42a";

    /** Fallback min-accuracy color used when the configured value is junk. */
    static final String DEFAULT_INACCURATE = "#cc472d";

    /**
     * Accuracy from one error: 1 minus the error share of the
     * theoretical max, clamped to [0, 1]. A zero error reads 1 even
     * against a zero max. Pure.
     */
    public static double accuracy(double error, double theoreticalMax) {
        double theory = Math.max(theoreticalMax, MIN_THEORY);
        if (!(error > 0.0)) {
            return 1.0;
        }
        return clamp01(1.0 - error / theory);
    }

    /** Accuracy snapped to tenths: 0.0, 0.1, up to 1.0. Pure. */
    public static double stepped(double accuracy01) {
        return Math.round(clamp01(accuracy01) * 10.0) / 10.0;
    }

    /** Whole percent for one stepped accuracy: 0 to 100. Pure. */
    public static int percent(double steppedAccuracy) {
        return (int) Math.round(clamp01(steppedAccuracy) * 100.0);
    }

    /**
     * Color between the inaccurate and accurate hex colors at the
     * stepped value, lerped per channel in linear RGB. Both inputs
     * parse as `#rrggbb` (leading `#` optional, case-insensitive);
     * junk falls back to the matching default. Returns lowercase
     * `#rrggbb`. Pure.
     */
    public static String lerpColor(String inaccurateHex, String accurateHex, double stepped) {
        int[] low = parseOr(inaccurateHex, DEFAULT_INACCURATE);
        int[] high = parseOr(accurateHex, DEFAULT_ACCURATE);
        double t = clamp01(stepped);
        int red = (int) Math.round(low[0] + (high[0] - low[0]) * t);
        int green = (int) Math.round(low[1] + (high[1] - low[1]) * t);
        int blue = (int) Math.round(low[2] + (high[2] - low[2]) * t);
        return String.format(Locale.ROOT, "#%02x%02x%02x", red, green, blue);
    }

    /**
     * Accuracy segment for tracking bars: gray parens around the
     * hex-colored percent, with the hex color closed before the closing
     * paren so it cannot bleed into the rest of the bar. Pure.
     */
    public static String segment(int percent, String hex) {
        return "<gray>(<" + hex + ">" + percent + "%<gray>)</gray>";
    }

    /** Value clamped to [0, 1]; NaN maps to 0. Pure. */
    static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, value));
    }

    /** Parses `#rrggbb` into channels, or the fallback when unparsable. */
    private static int[] parseOr(String raw, String fallback) {
        int[] parsed = parseHex(raw);
        return parsed != null ? parsed : parseHex(fallback);
    }

    /** Parses `#rrggbb` (leading `#` optional) into channels, else null. */
    private static int[] parseHex(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.length() != 6) {
            return null;
        }
        try {
            int red = Integer.parseInt(text.substring(0, 2), 16);
            int green = Integer.parseInt(text.substring(2, 4), 16);
            int blue = Integer.parseInt(text.substring(4, 6), 16);
            return new int[] {red, green, blue};
        } catch (NumberFormatException junk) {
            return null;
        }
    }
}
