package com.jruk8.jmanhunt.match;

/**
 * Where a match leaver goes, from
 * {@code settings.match.game-leave.destination}.
 */
public enum LeaveDestination {
    SPECTATOR,
    LOBBY;

    /** Parses case-insensitively; unknown values fall back to SPECTATOR. */
    public static LeaveDestination parse(String raw) {
        if (raw != null && raw.trim().equalsIgnoreCase("LOBBY")) {
            return LOBBY;
        }
        return SPECTATOR;
    }
}
