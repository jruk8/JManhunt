package com.jruk8.jmanhunt.match;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WinConditionEngineTest {

    private WinConditionEngine engine(YamlConfiguration config) {
        return new WinConditionEngine(config);
    }

    @Test
    void exitEndEnabledByDefault() {
        WinConditionEngine engine = engine(new YamlConfiguration());
        assertTrue(engine.isExitEndEnabled());
    }

    @Test
    void exitEndCanBeDisabled() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.exitEnd.enabled", false);
        WinConditionEngine engine = engine(config);
        assertFalse(engine.isExitEndEnabled());
    }

    @Test
    void surviveTimeDisabledByDefault() {
        WinConditionEngine engine = engine(new YamlConfiguration());
        assertFalse(engine.isSurviveTimeEnabled());
    }

    @Test
    void surviveTimeSecondsDefault() {
        WinConditionEngine engine = engine(new YamlConfiguration());
        assertEquals(3600.0, engine.surviveTimeSeconds());
    }

    @Test
    void surviveTimeSecondsConfigurable() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.surviveTime.enabled", true);
        config.set("settings.win-conditions.surviveTime.time", 1200.0);
        WinConditionEngine engine = engine(config);
        assertTrue(engine.isSurviveTimeEnabled());
        assertEquals(1200.0, engine.surviveTimeSeconds());
    }

    @Test
    void acquireItemDisabledByDefault() {
        WinConditionEngine engine = engine(new YamlConfiguration());
        assertFalse(engine.isAcquireItemEnabled());
    }

    @Test
    void acquireItemConfigurable() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.acquireItem.enabled", true);
        config.set("settings.win-conditions.acquireItem.item", "minecraft:diamond");
        WinConditionEngine engine = engine(config);
        assertTrue(engine.isAcquireItemEnabled());
        assertEquals("minecraft:diamond", engine.acquireItem());
    }

    @Test
    void acquireItemDefaultItem() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.acquireItem.enabled", true);
        WinConditionEngine engine = engine(config);
        assertEquals("minecraft:netherite_ingot", engine.acquireItem());
    }

    @Test
    void reachAdvancementDisabledByDefault() {
        WinConditionEngine engine = engine(new YamlConfiguration());
        assertFalse(engine.isReachAdvancementEnabled());
    }

    @Test
    void reachAdvancementConfigurable() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.reachAdvancement.enabled", true);
        config.set("settings.win-conditions.reachAdvancement.advancement", "minecraft:story/enter_the_nether");
        WinConditionEngine engine = engine(config);
        assertTrue(engine.isReachAdvancementEnabled());
        assertEquals("minecraft:story/enter_the_nether", engine.reachAdvancement());
    }

    @Test
    void reachAdvancementDefaultAdvancement() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.reachAdvancement.enabled", true);
        WinConditionEngine engine = engine(config);
        assertEquals("minecraft:story/enter_the_nether", engine.reachAdvancement());
    }

    @Test
    void reloadUpdatesConfig() {
        YamlConfiguration config = new YamlConfiguration();
        WinConditionEngine engine = engine(config);
        assertFalse(engine.isSurviveTimeEnabled());

        YamlConfiguration newConfig = new YamlConfiguration();
        newConfig.set("settings.win-conditions.surviveTime.enabled", true);
        newConfig.set("settings.win-conditions.surviveTime.time", 500.0);
        engine.reload(newConfig);

        assertTrue(engine.isSurviveTimeEnabled());
        assertEquals(500.0, engine.surviveTimeSeconds());
    }

    @Test
    void killMobDisabledByDefault() {
        WinConditionEngine engine = engine(new YamlConfiguration());
        assertFalse(engine.isKillMobEnabled());
        assertEquals("minecraft:ender_dragon", engine.killMob());
    }

    @Test
    void killMobConfigurable() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.killMob.enabled", true);
        config.set("settings.win-conditions.killMob.mob", "minecraft:wither");
        WinConditionEngine engine = engine(config);
        assertTrue(engine.isKillMobEnabled());
        assertEquals("minecraft:wither", engine.killMob());
    }

    @Test
    void hunterConditionsDisabledByDefault() {
        WinConditionEngine engine = engine(new YamlConfiguration());
        assertFalse(engine.isHunterTimeLimitEnabled());
        assertEquals(3600.0, engine.hunterTimeLimitSeconds());
        assertFalse(engine.isHunterAcquireItemEnabled());
        assertEquals("minecraft:netherite_ingot", engine.hunterAcquireItem());
        assertFalse(engine.isHunterKillMobEnabled());
        assertEquals("minecraft:ender_dragon", engine.hunterKillMob());
    }

    @Test
    void hunterConditionsConfigurable() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.hunterTimeLimit.enabled", true);
        config.set("settings.win-conditions.hunterTimeLimit.time", 600.0);
        config.set("settings.win-conditions.hunterAcquireItem.enabled", true);
        config.set("settings.win-conditions.hunterKillMob.enabled", true);
        config.set("settings.win-conditions.hunterKillMob.mob", "minecraft:warden");
        WinConditionEngine engine = engine(config);
        assertTrue(engine.isHunterTimeLimitEnabled());
        assertEquals(600.0, engine.hunterTimeLimitSeconds());
        assertTrue(engine.isHunterAcquireItemEnabled());
        assertTrue(engine.isHunterKillMobEnabled());
        assertEquals("minecraft:warden", engine.hunterKillMob());
    }

    @Test
    void prettyKeyStripsNamespaceAndFormats() {
        assertEquals("ender dragon", WinConditionEngine.prettyKey("minecraft:ender_dragon"));
        assertEquals("netherite ingot", WinConditionEngine.prettyKey("minecraft:netherite_ingot"));
        assertEquals("story enter the nether",
                WinConditionEngine.prettyKey("minecraft:story/enter_the_nether"));
        assertEquals("netherite ingot", WinConditionEngine.prettyKey("netherite_ingot"));
    }

    @Test
    void prettyKeyFallsBackOnBlank() {
        assertEquals("?", WinConditionEngine.prettyKey(null));
        assertEquals("?", WinConditionEngine.prettyKey("   "));
    }
}
