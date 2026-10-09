package com.jruk8.jmanhunt.match.prestart;

/** Pure helpers for the start-on-speedrunner-damage wait. */
public final class WaitingReminder {
    private static final int MIN_DELAY_SECONDS = 5;

    private WaitingReminder() {
    }

    /**
     * Clamps a configured delay to at least 5 seconds. A value of -1 (wait
     * indefinitely) is preserved.
     *
     * @param configured the configured delay in seconds
     * @return the effective delay, or -1 for indefinite
     */
    public static int clampDelay(int configured) {
        if (configured < 0) {
            return -1;
        }
        return Math.max(MIN_DELAY_SECONDS, configured);
    }
}