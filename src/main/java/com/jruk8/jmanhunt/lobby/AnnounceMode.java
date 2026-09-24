package com.jruk8.jmanhunt.lobby;

/**
 * Who hears lobby leave and join lines. Backs
 * {@code lobbies.announce-lobby-changes}: ALL tells everyone involved,
 * SELF only the moved player, MEMBERS only the other members, NONE
 * stays silent.
 */
public enum AnnounceMode {
    ALL,
    SELF,
    MEMBERS,
    NONE;

    /** Parses leniently; unknown values fall back to ALL. */
    public static AnnounceMode parse(String raw) {
        if (raw != null) {
            for (AnnounceMode mode : values()) {
                if (mode.name().equalsIgnoreCase(raw.trim())) {
                    return mode;
                }
            }
        }
        return ALL;
    }

    /** True when the moved player hears the line. */
    public boolean tellsSelf() {
        return this == ALL || this == SELF;
    }

    /** True when the other lobby members hear the line. */
    public boolean tellsMembers() {
        return this == ALL || this == MEMBERS;
    }
}
