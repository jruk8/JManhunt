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
    SUBLOBBY,
    SUBLOBBY_WITH_SPECTATORS;

    /** Parses leniently; unknown values fall back to SUBLOBBY_WITH_SPECTATORS. */
    public static MidMatchPolicy parse(String raw) {
        if (raw != null) {
            for (MidMatchPolicy policy : values()) {
                if (policy.name().equalsIgnoreCase(raw.trim())) {
                    return policy;
                }
            }
        }
        return SUBLOBBY_WITH_SPECTATORS;
    }

    /**
     * True when this policy joins the live match instead of holding the
     * player for the next game. SUBLOBBY holds for the next sublobby by
     * design; SUBLOBBY_WITH_SPECTATORS holds everyone except spectators.
     * Pure for tests.
     */
    public boolean joinsMidMatch(Role role) {
        return switch (this) {
            case JOIN_ANY -> true;
            case JOIN_SPECTATORS, SUBLOBBY_WITH_SPECTATORS -> role == Role.SPECTATOR;
            case HOLD, SUBLOBBY -> false;
        };
    }

    /** True when new matches run as sub-lobbies of the parent lobby. Pure for tests. */
    public boolean usesSubLobbies() {
        return this == SUBLOBBY || this == SUBLOBBY_WITH_SPECTATORS;
    }
}
