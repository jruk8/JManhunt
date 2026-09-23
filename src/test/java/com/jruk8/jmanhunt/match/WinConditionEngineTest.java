package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.player.Role;
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
    void exitEndEnabledByDefaultForSpeedrunnersOnly() {
        WinConditionEngine engine = engine(new YamlConfiguration());
        assertTrue(engine.enabled(Role.SPEEDRUNNER, WinCondition.EXIT_END));
        assertFalse(engine.enabled(Role.HUNTER, WinCondition.EXIT_END));
    }

    @Test
    void exitEndCanBeDisabled() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.speedrunner.exit-end.enabled", false);
        WinConditionEngine engine = engine(config);
        assertFalse(engine.enabled(Role.SPEEDRUNNER, WinCondition.EXIT_END));
    }

    @Test
    void surviveTimeDisabledByDefault() {
        WinConditionEngine engine = engine(new YamlConfiguration());
        assertFalse(engine.enabled(Role.SPEEDRUNNER, WinCondition.SURVIVE_TIME));
        assertFalse(engine.enabled(Role.HUNTER, WinCondition.SURVIVE_TIME));
    }

    @Test
    void timeIsSharedAcrossSides() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.speedrunner.survive-time.enabled", true);
        config.set("settings.win-conditions.speedrunner.survive-time.time", 1200.0);
        config.set("settings.win-conditions.hunter.survive-time.enabled", true);
        config.set("settings.win-conditions.hunter.survive-time.time", 600.0);
        WinConditionEngine engine = engine(config);
        assertTrue(engine.enabled(Role.SPEEDRUNNER, WinCondition.SURVIVE_TIME));
        assertEquals(1200.0, engine.time(Role.SPEEDRUNNER));
        assertTrue(engine.enabled(Role.HUNTER, WinCondition.TIME_LIMIT));
        assertEquals(600.0, engine.time(Role.HUNTER));
        assertEquals(3600.0, engine(new YamlConfiguration()).time(Role.SPECTATOR));
    }

    @Test
    void acquireItemIsPerSide() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.speedrunner.acquire-item.enabled", true);
        config.set("settings.win-conditions.speedrunner.acquire-item.item", "minecraft:diamond");
        WinConditionEngine engine = engine(config);
        assertTrue(engine.enabled(Role.SPEEDRUNNER, WinCondition.ACQUIRE_ITEM));
        assertEquals("minecraft:diamond", engine.item(Role.SPEEDRUNNER));
        assertFalse(engine.enabled(Role.HUNTER, WinCondition.ACQUIRE_ITEM));
        assertEquals("minecraft:netherite_ingot", engine.item(Role.HUNTER));
    }

    @Test
    void reachAdvancementIsPerSide() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.speedrunner.reach-advancement.enabled", true);
        config.set("settings.win-conditions.speedrunner.reach-advancement.advancement",
                "minecraft:story/enter_the_nether");
        WinConditionEngine engine = engine(config);
        assertTrue(engine.enabled(Role.SPEEDRUNNER, WinCondition.REACH_ADVANCEMENT));
        assertFalse(engine.enabled(Role.HUNTER, WinCondition.REACH_ADVANCEMENT));
        assertEquals("minecraft:story/enter_the_nether", engine.advancement(Role.SPEEDRUNNER));
        assertEquals("minecraft:story/enter_the_nether",
                engine(new YamlConfiguration()).advancement(Role.HUNTER));
    }

    @Test
    void killMobIsPerSide() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.hunter.kill-mob.enabled", true);
        config.set("settings.win-conditions.hunter.kill-mob.mob", "minecraft:warden");
        WinConditionEngine engine = engine(config);
        assertTrue(engine.enabled(Role.HUNTER, WinCondition.KILL_MOB));
        assertEquals("minecraft:warden", engine.mob(Role.HUNTER));
        assertFalse(engine.enabled(Role.SPEEDRUNNER, WinCondition.KILL_MOB));
        assertEquals("minecraft:ender_dragon", engine.mob(Role.SPEEDRUNNER));
    }

    @Test
    void nonParticipantsNeverEnabled() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.speedrunner.acquire-item.enabled", true);
        config.set("settings.win-conditions.hunter.acquire-item.enabled", true);
        WinConditionEngine engine = engine(config);
        for (Role role : new Role[] {Role.SPECTATOR, Role.AFK, Role.NONE}) {
            for (WinCondition condition : WinCondition.values()) {
                assertFalse(engine.enabled(role, condition), role + " " + condition);
            }
        }
    }

    @Test
    void reloadUpdatesConfig() {
        YamlConfiguration config = new YamlConfiguration();
        WinConditionEngine engine = engine(config);
        assertFalse(engine.enabled(Role.SPEEDRUNNER, WinCondition.SURVIVE_TIME));

        YamlConfiguration newConfig = new YamlConfiguration();
        newConfig.set("settings.win-conditions.speedrunner.survive-time.enabled", true);
        newConfig.set("settings.win-conditions.speedrunner.survive-time.time", 500.0);
        engine.reload(newConfig);

        assertTrue(engine.enabled(Role.SPEEDRUNNER, WinCondition.SURVIVE_TIME));
        assertEquals(500.0, engine.time(Role.SPEEDRUNNER));
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

    @Test
    void reachAdvancementEnabledPerSide() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.hunter.reach-advancement.enabled", true);
        config.set("settings.win-conditions.hunter.reach-advancement.advancement",
                "minecraft:nether/get_wither_skull");
        WinConditionEngine engine = engine(config);

        assertTrue(engine.enabled(Role.HUNTER, WinCondition.REACH_ADVANCEMENT));
        assertFalse(engine.enabled(Role.SPEEDRUNNER, WinCondition.REACH_ADVANCEMENT));
        assertEquals("minecraft:nether/get_wither_skull", engine.advancement(Role.HUNTER));
        assertEquals("minecraft:story/enter_the_nether", engine.advancement(Role.SPEEDRUNNER));
    }

    @Test
    void materialWinsStaysFalseWhenDisabled() {
        WinConditionEngine engine = engine(new YamlConfiguration());

        assertFalse(engine.materialWins(org.bukkit.Material.DIAMOND, Role.SPEEDRUNNER));
        assertFalse(engine.materialWins(org.bukkit.Material.DIAMOND, Role.HUNTER));
    }

    @Test
    void materialWinsRejectsNullWithoutTouchingRegistry() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.speedrunner.acquire-item.enabled", true);
        WinConditionEngine engine = engine(config);

        assertFalse(engine.materialWins(null, Role.SPEEDRUNNER));
    }

    @Test
    void cancelSurviveEnabledByDefaultAtEightHours() {
        WinConditionEngine engine = engine(new YamlConfiguration());

        assertTrue(engine.cancelSurviveEnabled());
        assertEquals(28800.0, engine.cancelSurviveTime());
    }

    @Test
    void cancelSurviveCanBeDisabled() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.win-conditions.cancel.survived-time.enabled", false);
        config.set("settings.win-conditions.cancel.survived-time.time", 60.0);
        WinConditionEngine engine = engine(config);

        assertFalse(engine.cancelSurviveEnabled());
        assertEquals(60.0, engine.cancelSurviveTime());
    }
}
