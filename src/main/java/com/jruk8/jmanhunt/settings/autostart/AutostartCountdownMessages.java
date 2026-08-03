package com.jruk8.jmanhunt.settings.autostart;

import java.util.Set;

public final class AutostartCountdownMessages {
    private static final Set<Integer> CHECKPOINTS = Set.of(600, 300, 180, 120, 60, 30, 15, 10, 5, 4, 3, 2, 1);

    private AutostartCountdownMessages() {
    }

    public static boolean shouldAnnounce(int remainingSeconds, int configuredCountdownSeconds) {
        return remainingSeconds > 0
                && remainingSeconds <= configuredCountdownSeconds
                && CHECKPOINTS.contains(remainingSeconds);
    }
}
