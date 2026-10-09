package com.jruk8.jmanhunt.match;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jruk8.jmanhunt.match.autostart.AutostartCountdownMessages;

class AutostartCountdownMessagesTest {
    @Test
    void announcesOnlyConfiguredCheckpoints() {
        assertTrue(AutostartCountdownMessages.shouldAnnounce(15, 15));
        assertTrue(AutostartCountdownMessages.shouldAnnounce(3, 15));
        assertTrue(AutostartCountdownMessages.shouldAnnounce(150, 200));
        assertFalse(AutostartCountdownMessages.shouldAnnounce(60, 15));
        assertFalse(AutostartCountdownMessages.shouldAnnounce(14, 15));
        assertFalse(AutostartCountdownMessages.shouldAnnounce(5, 60));
        assertFalse(AutostartCountdownMessages.shouldAnnounce(4, 60));
    }

    @Test
    void ignoresNonPositiveRemainingTime() {
        assertFalse(AutostartCountdownMessages.shouldAnnounce(0, 60));
        assertFalse(AutostartCountdownMessages.shouldAnnounce(-1, 60));
    }
}
