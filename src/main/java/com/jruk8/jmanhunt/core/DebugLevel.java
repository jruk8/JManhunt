package com.jruk8.jmanhunt.core;

/** Debug output verbosity: INFO shows all lines, WARN hides INFO, SEVERE shows only SEVERE. */
public enum DebugLevel {
    INFO,
    WARN,
    SEVERE;

    /** True when a recipient at this level sees a message at the given level. */
    public boolean shows(DebugLevel message) {
        return message.ordinal() >= this.ordinal();
    }

    /** Case-insensitive level match, or null when nothing matches. */
    public static DebugLevel parse(String raw) {
        if (raw == null) {
            return null;
        }
        for (DebugLevel level : values()) {
            if (level.name().equalsIgnoreCase(raw)) {
                return level;
            }
        }
        return null;
    }
}
