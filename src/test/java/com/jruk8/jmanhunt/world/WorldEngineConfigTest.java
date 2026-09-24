package com.jruk8.jmanhunt.world;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEngineConfigTest {

    private static ConfigService service(JManhuntConfig root) {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        return new ConfigService(root, new ModifierStore(new ModifiersConfig(), log));
    }

    @Test
    void spawnpointAlgorithmDefaultsToEnabledWithEightRetries() {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(service(new JManhuntConfig()));

        assertTrue(config.spawnpointAlgorithmEnabled());
        assertEquals(8, config.spawnpointMaxRetries());
        assertEquals(7, config.spawnpointYTolerance());
    }

    @Test
    void spawnpointAlgorithmReadsConfig() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "world-engine.spawnpoint-algorithm.enabled", false);
        ConfigPathMapper.set(root, "world-engine.spawnpoint-algorithm.max-retries", 2);
        WorldEngineConfig config = WorldEngineConfig.fromConfig(service(root));

        assertFalse(config.spawnpointAlgorithmEnabled());
        assertEquals(2, config.spawnpointMaxRetries());
    }

    @Test
    void spawnpointRetriesClampAtZero() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "world-engine.spawnpoint-algorithm.max-retries", -3);
        WorldEngineConfig config = WorldEngineConfig.fromConfig(service(root));

        assertEquals(0, config.spawnpointMaxRetries());
    }

    @Test
    void spawnpointYToleranceReadsAndClamps() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "world-engine.spawnpoint-algorithm.y-tolerance", 12);
        assertEquals(12, WorldEngineConfig.fromConfig(service(root)).spawnpointYTolerance());

        ConfigPathMapper.set(root, "world-engine.spawnpoint-algorithm.y-tolerance", -4);
        assertEquals(0, WorldEngineConfig.fromConfig(service(root)).spawnpointYTolerance());
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