package com.jruk8.jmanhunt.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Startup banner: versioned, compact, console colors only. */
class StartupBannerTest {

    @Test
    void linesCarryTheVersion() {
        List<String> lines = StartupBanner.lines("5.0.0");

        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("JManhunt"));
        assertTrue(lines.get(0).contains("5.0.0"));
    }

    @Test
    void linesUseNoMiniMessageOrEmDashes() {
        for (String line : StartupBanner.lines("5.0.0")) {
            assertFalse(line.contains("<"), line);
            assertFalse(line.contains(">"), line);
            assertFalse(line.contains("\u2014"), line);
        }
    }

    @Test
    void printLogsOneLinePerCall() {
        JManhuntLogger log = mock(JManhuntLogger.class);

        StartupBanner.print(log, "5.0.0");

        verify(log, times(3)).info(anyString());
    }
}
