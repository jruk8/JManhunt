package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WinConditionEngineTest {

    private WinConditionEngine engine(ConfigService config) {
        return new WinConditionEngine(
                new OverrideService(config, new LobbyConfig(), () -> { }));
    }

    private static ConfigService service(JManhuntConfig root) {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        return new ConfigService(root, new ModifierStore(new ModifiersConfig(), log));
    }

    @Test
    void exitEndEnabledByDefaultForSpeedrunnersOnly() {
        WinConditionEngine engine = engine(service(new JManhuntConfig()));
        assertTrue(engine.enabled(Role.SPEEDRUNNER, WinCondition.EXIT_END));
        assertFalse(engine.enabled(Role.HUNTER, WinCondition.EXIT_END));
    }

    @Test
    void exitEndCanBeDisabled() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.match.win-conditions.speedrunner.exit-end.enabled", false);
        WinConditionEngine engine = engine(service(root));
        assertFalse(engine.enabled(Role.SPEEDRUNNER, WinCondition.EXIT_END));
    }

    @Test
    void surviveTimeDisabledByDefault() {
        WinConditionEngine engine = engine(service(new JManhuntConfig()));
        assertFalse(engine.enabled(Role.SPEEDRUNNER, WinCondition.SURVIVE_TIME));
        assertFalse(engine.enabled(Role.HUNTER, WinCondition.SURVIVE_TIME));
    }

    @Test
    void timeIsSharedAcrossSides() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.match.win-conditions.speedrunner.survive-time.enabled", true);
        ConfigPathMapper.set(root, "settings.match.win-conditions.speedrunner.survive-time.time", 1200.0);
        ConfigPathMapper.set(root, "settings.match.win-conditions.hunter.survive-time.enabled", true);
        ConfigPathMapper.set(root, "settings.match.win-conditions.hunter.survive-time.time", 600.0);
        WinConditionEngine engine = engine(service(root));
        assertTrue(engine.enabled(Role.SPEEDRUNNER, WinCondition.SURVIVE_TIME));
        assertEquals(1200.0, engine.time(Role.SPEEDRUNNER));
        assertTrue(engine.enabled(Role.HUNTER, WinCondition.TIME_LIMIT));
        assertEquals(600.0, engine.time(Role.HUNTER));
        assertEquals(3600.0, engine(service(new JManhuntConfig())).time(Role.SPECTATOR));
    }

    @Test
    void acquireItemIsPerSide() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.match.win-conditions.speedrunner.acquire-item.enabled", true);
        ConfigPathMapper.set(root, "settings.match.win-conditions.speedrunner.acquire-item.item", "minecraft:diamond");
        WinConditionEngine engine = engine(service(root));
        assertTrue(engine.enabled(Role.SPEEDRUNNER, WinCondition.ACQUIRE_ITEM));
        assertEquals("minecraft:diamond", engine.item(Role.SPEEDRUNNER));
        assertFalse(engine.enabled(Role.HUNTER, WinCondition.ACQUIRE_ITEM));
        assertEquals("minecraft:netherite_ingot", engine.item(Role.HUNTER));
    }

    @Test
    void reachAdvancementIsPerSide() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.match.win-conditions.speedrunner.reach-advancement.enabled", true);
        ConfigPathMapper.set(root, "settings.match.win-conditions.speedrunner.reach-advancement.advancement",
                "minecraft:story/enter_the_nether");
        WinConditionEngine engine = engine(service(root));
        assertTrue(engine.enabled(Role.SPEEDRUNNER, WinCondition.REACH_ADVANCEMENT));
        assertFalse(engine.enabled(Role.HUNTER, WinCondition.REACH_ADVANCEMENT));
        assertEquals("minecraft:story/enter_the_nether", engine.advancement(Role.SPEEDRUNNER));
        assertEquals("minecraft:story/enter_the_nether",
                engine(service(new JManhuntConfig())).advancement(Role.HUNTER));
    }

    @Test
    void killMobIsPerSide() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.match.win-conditions.hunter.kill-mob.enabled", true);
        ConfigPathMapper.set(root, "settings.match.win-conditions.hunter.kill-mob.mob", "minecraft:warden");
        WinConditionEngine engine = engine(service(root));
        assertTrue(engine.enabled(Role.HUNTER, WinCondition.KILL_MOB));
        assertEquals("minecraft:warden", engine.mob(Role.HUNTER));
        assertFalse(engine.enabled(Role.SPEEDRUNNER, WinCondition.KILL_MOB));
        assertEquals("minecraft:wither", engine.mob(Role.SPEEDRUNNER));
    }

    @Test
    void nonParticipantsNeverEnabled() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.match.win-conditions.speedrunner.acquire-item.enabled", true);
        ConfigPathMapper.set(root, "settings.match.win-conditions.hunter.acquire-item.enabled", true);
        WinConditionEngine engine = engine(service(root));
        for (Role role : new Role[] {Role.SPECTATOR, Role.AFK, Role.NONE}) {
            for (WinCondition condition : WinCondition.values()) {
                assertFalse(engine.enabled(role, condition), role + " " + condition);
            }
        }
    }

    @Test
    void reloadUpdatesConfig() {
        JManhuntConfig root = new JManhuntConfig();
        WinConditionEngine engine = engine(service(root));
        assertFalse(engine.enabled(Role.SPEEDRUNNER, WinCondition.SURVIVE_TIME));

        JManhuntConfig root2 = new JManhuntConfig();
        ConfigPathMapper.set(root2, "settings.match.win-conditions.speedrunner.survive-time.enabled", true);
        ConfigPathMapper.set(root2, "settings.match.win-conditions.speedrunner.survive-time.time", 500.0);
        engine.reload(service(root2));

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
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.match.win-conditions.hunter.reach-advancement.enabled", true);
        ConfigPathMapper.set(root, "settings.match.win-conditions.hunter.reach-advancement.advancement",
                "minecraft:nether/get_wither_skull");
        WinConditionEngine engine = engine(service(root));

        assertTrue(engine.enabled(Role.HUNTER, WinCondition.REACH_ADVANCEMENT));
        assertFalse(engine.enabled(Role.SPEEDRUNNER, WinCondition.REACH_ADVANCEMENT));
        assertEquals("minecraft:nether/get_wither_skull", engine.advancement(Role.HUNTER));
        assertEquals("minecraft:story/enter_the_nether", engine.advancement(Role.SPEEDRUNNER));
    }

    @Test
    void materialWinsStaysFalseWhenDisabled() {
        WinConditionEngine engine = engine(service(new JManhuntConfig()));

        assertFalse(engine.materialWins(org.bukkit.Material.DIAMOND, Role.SPEEDRUNNER));
        assertFalse(engine.materialWins(org.bukkit.Material.DIAMOND, Role.HUNTER));
    }

    @Test
    void materialWinsRejectsNullWithoutTouchingRegistry() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.match.win-conditions.speedrunner.acquire-item.enabled", true);
        WinConditionEngine engine = engine(service(root));

        assertFalse(engine.materialWins(null, Role.SPEEDRUNNER));
    }

    @Test
    void cancelSurviveEnabledByDefaultAtEightHours() {
        WinConditionEngine engine = engine(service(new JManhuntConfig()));

        assertTrue(engine.cancelSurviveEnabled());
        assertEquals(28800.0, engine.cancelSurviveTime());
    }

    @Test
    void cancelSurviveCanBeDisabled() {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.match.win-conditions.cancel.survived-time.enabled", false);
        ConfigPathMapper.set(root, "settings.match.win-conditions.cancel.survived-time.time", 60.0);
        WinConditionEngine engine = engine(service(root));

        assertFalse(engine.cancelSurviveEnabled());
        assertEquals(60.0, engine.cancelSurviveTime());
    }
}
