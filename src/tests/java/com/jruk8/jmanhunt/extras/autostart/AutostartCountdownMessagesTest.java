package com.jruk8.jmanhunt.extras.autostart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutostartCountdownMessagesTest {
    @Test
    void announcesOnlyConfiguredCheckpoints() {
        assertTrue(AutostartCountdownMessages.shouldAnnounce(15, 15));
        assertTrue(AutostartCountdownMessages.shouldAnnounce(3, 15));
        assertFalse(AutostartCountdownMessages.shouldAnnounce(60, 15));
        assertFalse(AutostartCountdownMessages.shouldAnnounce(14, 15));
    }

    @Test
    void ignoresNonPositiveRemainingTime() {
        assertFalse(AutostartCountdownMessages.shouldAnnounce(0, 60));
        assertFalse(AutostartCountdownMessages.shouldAnnounce(-1, 60));
    }
}
