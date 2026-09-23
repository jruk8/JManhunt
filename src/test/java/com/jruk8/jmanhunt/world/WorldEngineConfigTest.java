package com.jruk8.jmanhunt.world;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEngineConfigTest {

    @Test
    void spawnpointAlgorithmDefaultsToEnabledWithFiveRetries() {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(new YamlConfiguration());

        assertTrue(config.spawnpointAlgorithmEnabled());
        assertEquals(5, config.spawnpointMaxRetries());
    }

    @Test
    void spawnpointAlgorithmReadsConfig() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world-engine.spawnpoint-algorithm.enabled", false);
        yaml.set("world-engine.spawnpoint-algorithm.max-retries", 2);
        WorldEngineConfig config = WorldEngineConfig.fromConfig(yaml);

        assertFalse(config.spawnpointAlgorithmEnabled());
        assertEquals(2, config.spawnpointMaxRetries());
    }

    @Test
    void spawnpointRetriesClampAtZero() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world-engine.spawnpoint-algorithm.max-retries", -3);
        WorldEngineConfig config = WorldEngineConfig.fromConfig(yaml);

        assertEquals(0, config.spawnpointMaxRetries());
    }


    @Test
    void calculatesDiameterUsingLargerOfConfiguredRadiusAndSpreadPlusOne() {
        // startBorderRadius (10) > tpSpreadRadius + 1 (6) → use 10
        assertEquals(20, WorldEngineConfig.calculateStartBorderDiameter(5, 10));
    }

    @Test
    void calculatesDiameterUsingSpreadPlusOneWhenRadiusIsSmaller() {
        // startBorderRadius (3) < tpSpreadRadius + 1 (21) → use 21
        assertEquals(42, WorldEngineConfig.calculateStartBorderDiameter(20, 3));
    }

    @Test
    void calculatesDiameterUsingSpreadPlusOneOnlyWhenRadiusIsMinusOne() {
        // -1 means use tpSpreadRadius + 1 only
        assertEquals(12, WorldEngineConfig.calculateStartBorderDiameter(5, -1));
    }

    @Test
    void calculatesDiameterWithZeroSpread() {
        // tpSpreadRadius 0 → spreadBasedRadius = 1, radius 10 wins
        assertEquals(20, WorldEngineConfig.calculateStartBorderDiameter(0, 10));
    }

    @Test
    void calculatesDiameterWithZeroSpreadAndMinusOneRadius() {
        // tpSpreadRadius 0, radius -1 → use 0 + 1 = 1
        assertEquals(2, WorldEngineConfig.calculateStartBorderDiameter(0, -1));
    }

    @Test
    void calculatesDiameterWhenRadiusEqualsSpreadPlusOne() {
        // Both are equal (10 == 9 + 1) → use 10
        assertEquals(20, WorldEngineConfig.calculateStartBorderDiameter(9, 10));
    }
}