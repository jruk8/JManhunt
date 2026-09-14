package com.jruk8.jmanhunt;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for config key relocation applied on reload. YamlConfiguration is a
 * pure YAML wrapper, so these tests run without a Bukkit server.
 */
class YamlFileUpdaterTest {

    @Test
    void scalarMoveCarriesValueAndClearsOld() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("game-end-delay", 20.0);

        YamlFileUpdater.relocateKeys(config, Map.of("game-end-delay", "match.end-delay"));

        assertEquals(20.0, config.getDouble("match.end-delay"));
        assertFalse(config.contains("game-end-delay"));
    }

    @Test
    void existingTargetWinsButOldIsStillCleared() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("game-end-delay", 20.0);
        config.set("match.end-delay", 5.0);

        YamlFileUpdater.relocateKeys(config, Map.of("game-end-delay", "match.end-delay"));

        assertEquals(5.0, config.getDouble("match.end-delay"));
        assertFalse(config.contains("game-end-delay"));
    }

    @Test
    void absentOldPathIsNoop() {
        YamlConfiguration config = new YamlConfiguration();

        YamlFileUpdater.relocateKeys(config, Map.of("game-end-delay", "match.end-delay"));

        assertFalse(config.contains("match.end-delay"));
    }

    @Test
    void sectionMoveCarriesWholeSubtree() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("disconnect-handling.speedrunner.max-strikes", 5);
        config.set("disconnect-handling.hunter.reconnect-grace-seconds", 30);

        YamlFileUpdater.relocateKeys(config,
                Map.of("disconnect-handling", "match.disconnect-handling"));

        assertEquals(5, config.getInt("match.disconnect-handling.speedrunner.max-strikes"));
        assertEquals(30, config.getInt("match.disconnect-handling.hunter.reconnect-grace-seconds"));
        assertFalse(config.contains("disconnect-handling"));
    }
}
