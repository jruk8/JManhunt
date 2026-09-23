package com.jruk8.jmanhunt.updatechecker;

import com.jruk8.jmanhunt.updatechecker.ports.UpdateCheckLogger;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateVersionTest {

    private static final class RecordingLogger implements UpdateCheckLogger {
        final List<String> errors = new ArrayList<>();

        @Override
        public void info(String message) {
        }

        @Override
        public void warning(String message) {
        }

        @Override
        public void severe(String message) {
            errors.add(message);
        }
    }

    @Test
    void parseFindsTripleAnywhere() {
        RecordingLogger logger = new RecordingLogger();

        assertEquals(new UpdateVersion(1, 2, 3), UpdateVersion.parse("v1.2.3", logger));
        assertEquals(new UpdateVersion(10, 20, 30), UpdateVersion.parse("release-10.20.30-snapshot", logger));
        assertTrue(logger.errors.isEmpty());
    }

    @Test
    void parseFallsBackToPairThenSingle() {
        RecordingLogger logger = new RecordingLogger();

        assertEquals(new UpdateVersion(2, 0, 0), UpdateVersion.parse("v2.0", logger));
        assertEquals(new UpdateVersion(3, 0, 0), UpdateVersion.parse("build 3", logger));
        assertTrue(logger.errors.isEmpty());
    }

    @Test
    void parseGarbageFallsBackToOneZeroZeroWithError() {
        RecordingLogger logger = new RecordingLogger();

        assertEquals(new UpdateVersion(1, 0, 0), UpdateVersion.parse("snapshot", logger));
        assertEquals(new UpdateVersion(1, 0, 0), UpdateVersion.parse(null, logger));
        assertEquals(2, logger.errors.size());
    }

    @Test
    void newerComparesComponentWise() {
        assertTrue(new UpdateVersion(2, 0, 0).isNewerThan(new UpdateVersion(1, 9, 9)));
        assertTrue(new UpdateVersion(1, 3, 0).isNewerThan(new UpdateVersion(1, 2, 9)));
        assertTrue(new UpdateVersion(1, 2, 4).isNewerThan(new UpdateVersion(1, 2, 3)));
        assertFalse(new UpdateVersion(1, 2, 3).isNewerThan(new UpdateVersion(1, 2, 3)));
        assertFalse(new UpdateVersion(1, 2, 3).isNewerThan(new UpdateVersion(2, 0, 0)));
    }

    @Test
    void severityNamesHighestDifferingComponent() {
        assertEquals("major", new UpdateVersion(2, 0, 0).severityOver(new UpdateVersion(1, 2, 3)));
        assertEquals("minor", new UpdateVersion(1, 3, 0).severityOver(new UpdateVersion(1, 2, 3)));
        assertEquals("hotfix", new UpdateVersion(1, 2, 4).severityOver(new UpdateVersion(1, 2, 3)));
        assertEquals(null, new UpdateVersion(1, 2, 3).severityOver(new UpdateVersion(1, 2, 3)));
    }

    @Test
    void toStringPrintsDottedTriple() {
        assertEquals("1.2.3", new UpdateVersion(1, 2, 3).toString());
    }
}
