package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEditAvailabilityTest {

    @Test
    void acceptsMinimumVersion() {
        assertTrue(WorldEditAvailability.isSupportedVersion("7.3.0"));
        assertTrue(WorldEditAvailability.isSupportedVersion("7.3.0-SNAPSHOT"));
    }

    @Test
    void acceptsNewerVersions() {
        assertTrue(WorldEditAvailability.isSupportedVersion("7.4.5"));
        assertTrue(WorldEditAvailability.isSupportedVersion("8.0.0"));
        assertTrue(WorldEditAvailability.isSupportedVersion("10.1.2-SNAPSHOT"));
    }

    @Test
    void rejectsOlderVersions() {
        assertFalse(WorldEditAvailability.isSupportedVersion("7.2.18"));
        assertFalse(WorldEditAvailability.isSupportedVersion("6.1.5"));
    }

    @Test
    void rejectsMalformedOrUnknownVersions() {
        assertFalse(WorldEditAvailability.isSupportedVersion(null));
        assertFalse(WorldEditAvailability.isSupportedVersion(""));
        assertFalse(WorldEditAvailability.isSupportedVersion("abc"));
        assertFalse(WorldEditAvailability.isSupportedVersion("unknown"));
    }

    @Test
    void isAvailableReturnsFalseWithoutWorldEdit() {
        // There is no running server in unit tests, so WorldEdit can never be
        // available; the soft-dependency check must not throw.
        assertFalse(WorldEditAvailability.isAvailable());
    }
}
