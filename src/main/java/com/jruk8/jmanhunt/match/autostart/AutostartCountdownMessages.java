package com.jruk8.jmanhunt.match.autostart;

import com.jruk8.jmanhunt.match.CountdownService;

public final class AutostartCountdownMessages {
    private AutostartCountdownMessages() {
    }

    /**
     * True when the tick earns a checkpoint: inside the configured
     * window and on a countdown mark. The eligible opener owns the
     * full total, so the checkpoint skips it (after recording it as
     * the initial mark for the two-second shadow).
     */
    public static boolean shouldAnnounce(CountdownService countdowns, Object key,
            int remainingSeconds, int configuredCountdownSeconds) {
        if (remainingSeconds <= 0 || remainingSeconds > configuredCountdownSeconds) {
            return false;
        }
        return countdowns.pollMark(key, remainingSeconds)
                && remainingSeconds != configuredCountdownSeconds;
    }
}
