package com.jruk8.jmanhunt.lobby;

/**
 * What NONE, SPECTATOR, and AFK players wait out when they enter a
 * live match through /game join, pswitch, or a joining setplayer.
 * Players already in the game are never affected.
 */
public enum JoinTiming {
    WAIT,
    INSTANT;

    /** Parses leniently; unknown values fall back to WAIT. */
    public static JoinTiming parse(String raw) {
        if (raw != null) {
            for (JoinTiming timing : values()) {
                if (timing.name().equalsIgnoreCase(raw.trim())) {
                    return timing;
                }
            }
        }
        return WAIT;
    }
}
