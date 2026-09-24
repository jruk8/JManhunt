package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.match.prestart.OnExpire;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dotted-path reads and writes over the Okaeri schema objects. */
class ConfigPathMapperTest {

    @Test
    void getResolvesNestedScalars() {
        JManhuntConfig root = new JManhuntConfig();

        assertEquals(true,
                ConfigPathMapper.get(root, "settings.match.autostart.enabled"));
        assertEquals("compass", ConfigPathMapper.get(root, "settings.compass.item"));
        assertEquals(45,
                ConfigPathMapper.get(root, "settings.match.autostart.countdown-seconds"));
        assertEquals(10.0,
                ConfigPathMapper.get(root, "settings.compass.refresh-interval"));
        assertEquals(5, ConfigPathMapper.get(root, "config-version"));
    }

    @Test
    void getReturnsEnumsRaw() {
        JManhuntConfig root = new JManhuntConfig();

        assertEquals(OnExpire.FORCE_START, ConfigPathMapper.get(
                root, "settings.match.start-on-speedrunner-damage.on-expire"));
    }

    @Test
    void getResolvesMapLeaves() {
        JManhuntConfig root = new JManhuntConfig();

        assertEquals("default-lobby", ConfigPathMapper.get(
                root, "world-engine.lobby-presets.DEFAULT.schematic"));
    }

    @Test
    void getResolvesListIndices() {
        JManhuntConfig root = new JManhuntConfig();

        assertEquals("DAMAGE_DEALT", ConfigPathMapper.get(root, "match.end-statistics.0"));
        assertNull(ConfigPathMapper.get(root, "match.end-statistics.9"));
        assertNull(ConfigPathMapper.get(root, "match.end-statistics.bogus"));
    }

    @Test
    void getIsCaseInsensitive() {
        JManhuntConfig root = new JManhuntConfig();

        assertEquals(true,
                ConfigPathMapper.get(root, "Settings.Match.Autostart.Enabled"));
    }

    @Test
    void getMissingReturnsNull() {
        JManhuntConfig root = new JManhuntConfig();

        assertNull(ConfigPathMapper.get(root, "bogus.path"));
        assertNull(ConfigPathMapper.get(root, "settings.match.autostart.enabled.deeper"));
        assertNull(ConfigPathMapper.get(null, "settings.match.autostart.enabled"));
        assertNull(ConfigPathMapper.get(root, null));
        assertNull(ConfigPathMapper.get(root, ""));
    }

    @Test
    void setRoundtripsScalars() {
        JManhuntConfig root = new JManhuntConfig();

        assertTrue(ConfigPathMapper.set(root, "settings.match.autostart.countdown-seconds", 60));
        assertEquals(60,
                ConfigPathMapper.get(root, "settings.match.autostart.countdown-seconds"));

        assertTrue(ConfigPathMapper.set(root, "settings.compass.item", "clock"));
        assertEquals("clock", ConfigPathMapper.get(root, "settings.compass.item"));

        assertTrue(ConfigPathMapper.set(root, "settings.match.autostart.enabled", false));
        assertEquals(false, ConfigPathMapper.get(root, "settings.match.autostart.enabled"));
    }

    @Test
    void setCoercesCanonicalStringsIntoEnums() {
        JManhuntConfig root = new JManhuntConfig();

        assertTrue(ConfigPathMapper.set(
                root, "settings.match.start-on-speedrunner-damage.on-expire", "CANCEL"));
        assertEquals(OnExpire.CANCEL, ConfigPathMapper.get(
                root, "settings.match.start-on-speedrunner-damage.on-expire"));
    }

    @Test
    void setReplacesListEntries() {
        JManhuntConfig root = new JManhuntConfig();

        assertTrue(ConfigPathMapper.set(root, "match.end-statistics.0", "PROGRESSION"));
        assertEquals("PROGRESSION", ConfigPathMapper.get(root, "match.end-statistics.0"));
        assertFalse(ConfigPathMapper.set(root, "match.end-statistics.9", "X"));
    }

    @Test
    void setMissingOrMismatchedReturnsFalse() {
        JManhuntConfig root = new JManhuntConfig();

        assertFalse(ConfigPathMapper.set(root, "bogus.path", true));
        assertFalse(ConfigPathMapper.set(root, "settings.match.autostart.enabled", 5));
        assertFalse(ConfigPathMapper.set(root, "settings.match.autostart.countdown-seconds", "abc"));
        assertFalse(ConfigPathMapper.set(null, "settings.match.autostart.enabled", true));
    }
}
