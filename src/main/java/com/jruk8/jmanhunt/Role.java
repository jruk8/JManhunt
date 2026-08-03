package com.jruk8.jmanhunt;

public enum Role {
    NONE,
    HUNTER,
    SPEEDRUNNER,
    AFK;

    /** Returns true for HUNTER or SPEEDRUNNER (active match participants). */
    public boolean isParticipant() {
        return this == HUNTER || this == SPEEDRUNNER;
    }
}
