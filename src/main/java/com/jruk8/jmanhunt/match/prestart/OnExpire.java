package com.jruk8.jmanhunt.match.prestart;

/**
 * What happens when the pre-start wait expires before a speedrunner
 * damages a hunter. Backs {@code settings.match.start-on-speedrunner-damage.on-expire}.
 */
public enum OnExpire {
    CANCEL,
    FORCE_START;

    /** Parses leniently; unknown values fall back to CANCEL. */
    public static OnExpire parse(String raw) {
        if (raw != null && raw.trim().equalsIgnoreCase("FORCE_START")) {
            return FORCE_START;
        }
        return CANCEL;
    }
}
