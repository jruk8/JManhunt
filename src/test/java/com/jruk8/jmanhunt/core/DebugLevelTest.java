package com.jruk8.jmanhunt.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugLevelTest {

    @Test
    void parseMatchesCaseInsensitively() {
        assertEquals(DebugLevel.INFO, DebugLevel.parse("INFO"));
        assertEquals(DebugLevel.WARN, DebugLevel.parse("warn"));
        assertEquals(DebugLevel.parse("Severe"), DebugLevel.SEVERE);
    }

    @Test
    void parseRejectsAnythingElse() {
        assertNull(DebugLevel.parse("bogus"));
        assertNull(DebugLevel.parse("on"));
        assertNull(DebugLevel.parse(""));
        assertNull(DebugLevel.parse(null));
    }

    @Test
    void severeShowsOnlySevere() {
        assertFalse(DebugLevel.SEVERE.shows(DebugLevel.INFO));
        assertFalse(DebugLevel.SEVERE.shows(DebugLevel.WARN));
        assertTrue(DebugLevel.SEVERE.shows(DebugLevel.SEVERE));
    }

    @Test
    void warnShowsWarnAndSevere() {
        assertFalse(DebugLevel.WARN.shows(DebugLevel.INFO));
        assertTrue(DebugLevel.WARN.shows(DebugLevel.WARN));
        assertTrue(DebugLevel.WARN.shows(DebugLevel.SEVERE));
    }

    @Test
    void infoShowsAll() {
        assertTrue(DebugLevel.INFO.shows(DebugLevel.INFO));
        assertTrue(DebugLevel.INFO.shows(DebugLevel.WARN));
        assertTrue(DebugLevel.INFO.shows(DebugLevel.SEVERE));
    }
}
