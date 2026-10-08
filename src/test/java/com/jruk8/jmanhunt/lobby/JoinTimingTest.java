package com.jruk8.jmanhunt.lobby;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JoinTimingTest {

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(JoinTiming.WAIT, JoinTiming.parse("wait"));
        assertEquals(JoinTiming.WAIT, JoinTiming.parse("WAIT"));
        assertEquals(JoinTiming.INSTANT, JoinTiming.parse("instant"));
        assertEquals(JoinTiming.INSTANT, JoinTiming.parse("Instant"));
    }

    @Test
    void parseFallsBackToWait() {
        assertEquals(JoinTiming.WAIT, JoinTiming.parse("bogus"));
        assertEquals(JoinTiming.WAIT, JoinTiming.parse(null));
        assertEquals(JoinTiming.WAIT, JoinTiming.parse(""));
    }
}
