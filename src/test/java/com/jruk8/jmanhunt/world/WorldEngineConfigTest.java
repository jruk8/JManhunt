package com.jruk8.jmanhunt.world;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEngineConfigTest {

    @Test
    void spawnpointAlgorithmDefaultsToEnabledWithEightRetries() {
        WorldEngineConfig config = WorldEngineConfig.fromSettings(new JManhuntConfig().getWorldEngine());

        assertTrue(config.spawnpointAlgorithmEnabled());
        assertEquals(8, config.spawnpointMaxRetries());
        assertEquals(7, config.spawnpointYTolerance());
    }

    @Test
    void spawnpointAlgorithmReadsConfig() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "world-engine.spawnpoint-algorithm.enabled", false);
        ConfigPathMapper.set(root, "world-engine.spawnpoint-algorithm.max-retries", 2);
        WorldEngineConfig config = WorldEngineConfig.fromSettings(root.getWorldEngine());

        assertFalse(config.spawnpointAlgorithmEnabled());
        assertEquals(2, config.spawnpointMaxRetries());
    }

    @Test
    void spawnpointRetriesClampAtZero() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "world-engine.spawnpoint-algorithm.max-retries", -3);
        WorldEngineConfig config = WorldEngineConfig.fromSettings(root.getWorldEngine());

        assertEquals(0, config.spawnpointMaxRetries());
    }

    @Test
    void spawnpointYToleranceReadsAndClamps() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "world-engine.spawnpoint-algorithm.y-tolerance", 12);
        assertEquals(12, WorldEngineConfig.fromSettings(root.getWorldEngine()).spawnpointYTolerance());

        ConfigPathMapper.set(root, "world-engine.spawnpoint-algorithm.y-tolerance", -4);
        assertEquals(0, WorldEngineConfig.fromSettings(root.getWorldEngine()).spawnpointYTolerance());
    }


    @Test
    void endPoolDefaultsToJmhEndWithBufferThree() {
        WorldEngineConfig config = WorldEngineConfig.fromSettings(new JManhuntConfig().getWorldEngine());

        assertEquals("jmh_end", config.endBaseName());
        assertEquals(3, config.endBuffer());
    }

    @Test
    void endPoolReadsConfig() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "world-engine.end.base-name", "custom_end");
        ConfigPathMapper.set(root, "world-engine.end.buffer", 5);
        WorldEngineConfig config = WorldEngineConfig.fromSettings(root.getWorldEngine());

        assertEquals("custom_end", config.endBaseName());
        assertEquals(5, config.endBuffer());
    }

    @Test
    void endBufferClampsAtOneAndBlankBaseNameFallsBack() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "world-engine.end.buffer", 0);
        ConfigPathMapper.set(root, "world-engine.end.base-name", "  ");
        WorldEngineConfig config = WorldEngineConfig.fromSettings(root.getWorldEngine());

        assertEquals(1, config.endBuffer());
        assertEquals("jmh_end", config.endBaseName());
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

    @Test
    void useStartBorderNeedsUnbegunAndEnabled() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "world-engine.world-border.enabled", true);
        ConfigPathMapper.set(root, "world-engine.world-border.start-border.enabled", true);
        WorldEngineConfig config = WorldEngineConfig.fromSettings(root.getWorldEngine());

        assertTrue(config.useStartBorder(false));
        assertFalse(config.useStartBorder(true));
    }

    @Test
    void useStartBorderOffWhenStartBorderDisabled() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "world-engine.world-border.enabled", true);
        ConfigPathMapper.set(root, "world-engine.world-border.start-border.enabled", false);
        WorldEngineConfig config = WorldEngineConfig.fromSettings(root.getWorldEngine());

        assertFalse(config.useStartBorder(false));
    }

    @Test
    void useStartBorderOffWhenBorderDisabled() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "world-engine.world-border.enabled", false);
        ConfigPathMapper.set(root, "world-engine.world-border.start-border.enabled", true);
        WorldEngineConfig config = WorldEngineConfig.fromSettings(root.getWorldEngine());

        assertFalse(config.useStartBorder(false));
    }
}