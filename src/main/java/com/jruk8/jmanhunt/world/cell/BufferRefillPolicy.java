package com.jruk8.jmanhunt.world.cell;

/**
 * When the ready-cell buffer refills: always whenever a slot is free, or
 * only while no match is running.
 */
public enum BufferRefillPolicy {
    ALWAYS,
    NO_MATCH_RUNNING;

    /** Parses the increment-when key, defaulting to ALWAYS. */
    public static BufferRefillPolicy parse(String raw) {
        if (raw != null && raw.trim().equalsIgnoreCase("NO_MATCH_RUNNING")) {
            return NO_MATCH_RUNNING;
        }
        return ALWAYS;
    }
}
