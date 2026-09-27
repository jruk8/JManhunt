package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntervalDispatcherTest {

    @Test
    void intervalSkipsWatchersInactiveDeadAndSpectating() {
        assertTrue(IntervalDispatcher.intervalSkipWhy(Role.HUNTER, true, false, false).isEmpty());
        assertTrue(IntervalDispatcher.intervalSkipWhy(Role.SPEEDRUNNER, true, false, false)
                .isEmpty());
        assertEquals("not a participant",
                IntervalDispatcher.intervalSkipWhy(Role.SPECTATOR, true, false, false)
                        .orElseThrow());
        assertEquals("not a participant",
                IntervalDispatcher.intervalSkipWhy(Role.NONE, true, false, false).orElseThrow());
        assertEquals("not a participant",
                IntervalDispatcher.intervalSkipWhy(Role.AFK, true, false, false).orElseThrow());
        assertEquals("not active",
                IntervalDispatcher.intervalSkipWhy(Role.HUNTER, false, false, false)
                        .orElseThrow());
        assertEquals("dead",
                IntervalDispatcher.intervalSkipWhy(Role.HUNTER, true, true, false).orElseThrow());
        assertEquals("spectating",
                IntervalDispatcher.intervalSkipWhy(Role.HUNTER, true, false, true).orElseThrow());
    }
}
