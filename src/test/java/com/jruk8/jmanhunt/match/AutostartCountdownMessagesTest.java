package com.jruk8.jmanhunt.match;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.match.autostart.AutostartCountdownMessages;
import org.junit.jupiter.api.Test;

class AutostartCountdownMessagesTest {
    @Test
    void eligibleOwnsTotalThenMarksRun() {
        CountdownService countdowns = new CountdownService(mock(TaskScheduler.class));
        Object key = new Object();

        assertFalse(AutostartCountdownMessages.shouldAnnounce(countdowns, key, 17, 17));
        assertFalse(AutostartCountdownMessages.shouldAnnounce(countdowns, key, 16, 17));
        assertFalse(AutostartCountdownMessages.shouldAnnounce(countdowns, key, 15, 17));
        assertTrue(AutostartCountdownMessages.shouldAnnounce(countdowns, key, 10, 17));
        assertFalse(AutostartCountdownMessages.shouldAnnounce(countdowns, key, 9, 17));
        assertTrue(AutostartCountdownMessages.shouldAnnounce(countdowns, key, 3, 17));
        assertTrue(AutostartCountdownMessages.shouldAnnounce(countdowns, key, 2, 17));
        assertTrue(AutostartCountdownMessages.shouldAnnounce(countdowns, key, 1, 17));
    }

    @Test
    void ignoresOutOfWindowAndNonPositive() {
        CountdownService countdowns = new CountdownService(mock(TaskScheduler.class));

        assertFalse(AutostartCountdownMessages.shouldAnnounce(countdowns, new Object(), 60, 15));
        assertFalse(AutostartCountdownMessages.shouldAnnounce(countdowns, new Object(), 0, 60));
        assertFalse(AutostartCountdownMessages.shouldAnnounce(countdowns, new Object(), -1, 60));
    }
}
