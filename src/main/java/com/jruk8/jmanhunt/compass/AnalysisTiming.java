package com.jruk8.jmanhunt.compass;

/**
 * Pure analysis timing math: jittered delays, tick conversions, and
 * doom shortening. Bukkit-free.
 */
final class AnalysisTiming {

    private AnalysisTiming() {
    }

    /**
     * Remaining doom-shortened ticks: the multiplier keeps a fraction
     * of the time left, clamped to 0-1, so 0 resolves at once and 1
     * leaves the duration untouched. Pure.
     */
    static long shortenedTicks(long remainingTicks, double multiplier) {
        double clamped = Math.min(1.0, Math.max(0.0, multiplier));
        return (long) Math.ceil(Math.max(0L, remainingTicks) * clamped);
    }

    /**
     * Jittered analysis delay: the deviation is clamped to the delay,
     * then a uniform sample in delay +- deviation, never negative.
     * Pure for tests; roll is a [0, 1) sample.
     */
    static double jitteredDelay(double delaySeconds, double deviationSeconds, double roll) {
        double delay = Math.max(0.0, delaySeconds);
        double deviation = Math.min(Math.max(0.0, deviationSeconds), delay);
        return Math.max(0.0, delay + (roll * 2.0 - 1.0) * deviation);
    }

    /** Analysis delay in ticks, at least one. Pure for tests. */
    static long analyzeDelayTicks(double delaySeconds) {
        return Math.max(1L, Math.round(delaySeconds * 20.0));
    }

    /**
     * Analysis tick interval in ticks: seconds rounded to whole ticks
     * (the game runs 20 ticks per second), at least one. Pure for tests.
     */
    static long analysisTickInterval(double intervalSeconds) {
        return Math.max(1L, Math.round(intervalSeconds * 20.0));
    }
}
