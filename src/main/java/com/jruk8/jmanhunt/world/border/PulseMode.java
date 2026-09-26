package com.jruk8.jmanhunt.world.border;

/**
 * Pseudoborder particle pulse: a synchronized blink on a tick interval,
 * or a traveling sine wave sweeping across each wall. Exactly one is
 * active at a time.
 */
public enum PulseMode {
    INTERVAL,
    SINE_WAVE;

    /** Parses the pulse-mode key, defaulting to INTERVAL. */
    public static PulseMode parse(String raw) {
        if (raw != null && raw.trim().equalsIgnoreCase("SINE_WAVE")) {
            return SINE_WAVE;
        }
        return INTERVAL;
    }
}
