package com.jruk8.jmanhunt.match.autostart;

import com.jruk8.jmanhunt.match.CountdownService;

public final class AutostartCountdownMessages {
    private AutostartCountdownMessages() {
    }

    public static boolean shouldAnnounce(int remainingSeconds, int configuredCountdownSeconds) {
        return remainingSeconds > 0
                && remainingSeconds <= configuredCountdownSeconds
                && CountdownService.onLadder(remainingSeconds);
    }
}
