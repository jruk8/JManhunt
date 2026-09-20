package com.jruk8.jmanhunt.player;

/** Pure queue-cap math shared by setplayer, quickstart, and lobby join. */
public final class CapLimits {
    /** Normalized marker for "no limit". */
    public static final int UNCAPPED = -1;

    private CapLimits() {
    }

    /**
     * Normalizes a raw cap config value: negatives mean uncapped, zero
     * clamps to the minimum of one.
     */
    public static int effectiveCap(int raw) {
        if (raw < 0) {
            return UNCAPPED;
        }
        return Math.max(1, raw);
    }

    /** True when one more entry of a role is allowed at the given count. */
    public static boolean allows(int count, int rawCap) {
        int cap = effectiveCap(rawCap);
        return cap == UNCAPPED || count < cap;
    }
}
