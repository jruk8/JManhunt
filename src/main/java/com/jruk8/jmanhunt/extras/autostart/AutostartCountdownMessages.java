package com.jruk8.jmanhunt.extras.autostart;

import java.util.Set;

public final class AutostartCountdownMessages {
    private static final Set<Integer> CHECKPOINTS = Set.of(60, 15, 3, 2, 1);

    private AutostartCountdownMessages() {
    }

    public static boolean shouldAnnounce(int remainingSeconds, int configuredCountdownSeconds) {
        return remainingSeconds > 0
                && remainingSeconds <= configuredCountdownSeconds
                && CHECKPOINTS.contains(remainingSeconds);
    }
}
