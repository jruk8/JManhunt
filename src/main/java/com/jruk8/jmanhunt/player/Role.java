package com.jruk8.jmanhunt.player;

public enum Role {
    NONE,
    HUNTER,
    SPEEDRUNNER,
    AFK,
    SPECTATOR;

    /** Returns true for HUNTER or SPEEDRUNNER (active match participants). */
    public boolean isParticipant() {
        return this == HUNTER || this == SPEEDRUNNER;
    }

    /** User-facing name, e.g. Hunter instead of HUNTER. */
    public String displayName() {
        return switch (this) {
            case HUNTER -> "Hunter";
            case SPEEDRUNNER -> "Speedrunner";
            case AFK -> "Afk";
            case NONE -> "None";
            case SPECTATOR -> "Spectator";
        };
    }

    /** Parses a role name case-insensitively, or empty when it matches nothing. */
    public static java.util.Optional<Role> parse(String raw) {
        if (raw == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Role.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }
}
