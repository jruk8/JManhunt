package com.jruk8.jmanhunt.lobby.bounds;

/**
 * What walking out of every lobby bounds box does to a lobby member.
 * Only members whose lobby has complete bounds are considered, and
 * never players in a live match.
 */
public enum LobbyBoundsExitBehavior {
    /** Walking out keeps the lobby membership; the exit is ignored. */
    KEEP_IN_LOBBY,
    /** Walking out leaves the lobby for the exit destination. */
    EXIT_LOBBY;

    /**
     * Parses configured text leniently: blank, null, and unknown values
     * fall back to the default. Pure for tests.
     */
    public static LobbyBoundsExitBehavior parse(String raw) {
        if (raw != null) {
            for (LobbyBoundsExitBehavior behavior : values()) {
                if (behavior.name().equalsIgnoreCase(raw.trim())) {
                    return behavior;
                }
            }
        }
        return KEEP_IN_LOBBY;
    }
}
