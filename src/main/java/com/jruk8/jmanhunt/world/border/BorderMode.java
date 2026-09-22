package com.jruk8.jmanhunt.world.border;

/**
 * Which border system confines live matches: the real Bukkit border for a
 * single match, per-instance pseudo-borders once matches run concurrently,
 * or nothing when borders are off.
 */
public enum BorderMode {
    NONE,
    SINGLE,
    MULTI;

    public static BorderMode resolve(int liveInstances, boolean engineOn, boolean borderEnabled) {
        if (!engineOn || !borderEnabled || liveInstances <= 0) {
            return NONE;
        }
        return liveInstances == 1 ? SINGLE : MULTI;
    }
}
