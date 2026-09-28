package com.jruk8.jmanhunt.compass;

/**
 * Actionbar distance delta: compares the rounded current distance to
 * the rounded previous one and picks further, closer, or same.
 * Everything is pure for tests; the manager owns the history store.
 */
public final class DistanceDelta {

    /** Delta direction for one refresh. */
    public enum Kind {
        FURTHER,
        CLOSER,
        SAME
    }

    /**
     * Delta display mode: HOLD keeps the triangle until the next
     * refresh, BLINK shows it briefly, then reverts to plain white.
     */
    public enum Mode {
        HOLD,
        BLINK;

        /** Parses case-insensitively; unknown values fall back to BLINK. */
        public static Mode parse(String raw) {
            if (raw != null && raw.trim().equalsIgnoreCase("HOLD")) {
                return HOLD;
            }
            return BLINK;
        }
    }

    private DistanceDelta() {
    }

    /**
     * Picks the kind for rounded distances. Null previous (first
     * sighting), equal values, past-max current, and below-min deltas
     * all render SAME. Boundaries are inclusive: exactly max-distance
     * or exactly min-delta still shows.
     */
    public static Kind of(Long previous, long current, double maxDistance, double minDelta) {
        if (previous == null || previous == current) {
            return Kind.SAME;
        }
        if (current > maxDistance) {
            return Kind.SAME;
        }
        if (Math.abs(current - previous) < minDelta) {
            return Kind.SAME;
        }
        return current > previous ? Kind.FURTHER : Kind.CLOSER;
    }
}
