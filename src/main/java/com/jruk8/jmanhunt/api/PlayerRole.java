package com.jruk8.jmanhunt.api;

/**
 * The role of a player within a Manhunt match, as exposed through
 * {@link JManhuntApi}. Mirrors the plugin's internal role model without
 * leaking implementation classes to consumers.
 */
public enum PlayerRole {
    NONE,
    HUNTER,
    SPEEDRUNNER,
    AFK;

    /** Returns true for HUNTER or SPEEDRUNNER (active match participants). */
    public boolean isParticipant() {
        return this == HUNTER || this == SPEEDRUNNER;
    }
}