package com.jruk8.jmanhunt;

/** Pure helpers for the start-on-speedrunner-damage waiting reminders. */
public final class WaitingReminder {
    private static final int MIN_DELAY_SECONDS = 5;
    private static final int REMINDER_COUNT = 3;

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

    /**
     * Returns the number of seconds between the configured countdown
     * reminders: the delay divided by the reminder count.
     *
     * @param effectiveDelay the clamped delay in seconds (must be > 0)
     * @return the slice in seconds
     */
    public static int sliceSeconds(int effectiveDelay) {
        if (effectiveDelay <= 0) {
            return Math.max(1, effectiveDelay);
        }
        return Math.max(1, (int) Math.round(effectiveDelay / (double) REMINDER_COUNT));
    }
}