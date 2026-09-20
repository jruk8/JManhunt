package com.jruk8.jmanhunt;

/** Compact durations for instance listings: 48s, 19m 25s, 1h 2m 3s, 1d 1h. */
public final class DurationFormat {
    private DurationFormat() {
    }

    /** Formats whole seconds, omitting zero units (0 itself renders as 0s). */
    public static String format(long totalSeconds) {
        long remaining = Math.max(0L, totalSeconds);
        long days = remaining / 86_400L;
        long hours = (remaining % 86_400L) / 3_600L;
        long minutes = (remaining % 3_600L) / 60L;
        long seconds = remaining % 60L;
        StringBuilder out = new StringBuilder();
        append(out, days, "d");
        append(out, hours, "h");
        append(out, minutes, "m");
        append(out, seconds, "s");
        if (out.isEmpty()) {
            return "0s";
        }
        return out.toString();
    }

    private static void append(StringBuilder out, long value, String unit) {
        if (value <= 0L) {
            return;
        }
        if (!out.isEmpty()) {
            out.append(' ');
        }
        out.append(value).append(unit);
    }
}
