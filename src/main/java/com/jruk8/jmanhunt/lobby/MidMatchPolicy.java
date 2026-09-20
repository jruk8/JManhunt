package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.player.Role;

/**
 * What setplayer does when the target's lobby has a live match and the
 * world engine is on. With the engine off the legacy in-match block
 * stays.
 */
public enum MidMatchPolicy {
    HOLD,
    JOIN_ANY,
    JOIN_SPECTATORS,
    SUBLOBBY;

    /** Parses leniently; unknown values fall back to SUBLOBBY. */
    public static MidMatchPolicy parse(String raw) {
        if (raw != null) {
            for (MidMatchPolicy policy : values()) {
                if (policy.name().equalsIgnoreCase(raw.trim())) {
                    return policy;
                }
            }
        }
        return SUBLOBBY;
    }

    /**
     * True when this policy joins the live match instead of holding the
     * player for the next game. SUBLOBBY holds for the next sublobby by
     * design. Pure for tests.
     */
    public boolean joinsMidMatch(Role role) {
        return switch (this) {
            case JOIN_ANY -> true;
            case JOIN_SPECTATORS -> role == Role.SPECTATOR;
            case HOLD, SUBLOBBY -> false;
        };
    }
}
