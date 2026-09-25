package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierBehavior;
import com.jruk8.jmanhunt.modifiers.config.ModifierChance;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierExecution;
import com.jruk8.jmanhunt.modifiers.config.ModifierInterval;
import com.jruk8.jmanhunt.modifiers.config.ModifierOptions;
import com.jruk8.jmanhunt.modifiers.config.ModifierPickRandom;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recursive ANY glow: leaves glow on value drift, categories glow
 * when any immediate child glows, for settings and behavior alike.
 */
class ModifiedGlowTest {

    private JManhuntConfig root;
    private ConfigService config;
    private ModifierStore store;

    @BeforeEach
    void setup() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        root = new JManhuntConfig();
        config = new ConfigService(root,
                new ModifierStore(new ModifiersConfig(), log));
        ModifiersConfig modifiers = new ModifiersConfig();
        modifiers.getModifiers().put("zebra", behaviorEntry());
        modifiers.getModifiers().put("plain", plainEntry());
        store = new ModifierStore(modifiers, log);
    }

    private static ModifierEntry behaviorEntry() {
        ModifierEntry entry = new ModifierEntry();
        entry.setEnabled(true);
        ModifierBehavior behavior = new ModifierBehavior();
        behavior.setRunsOn(new ArrayList<>(List.of("ON_START", "INTERVAL")));
        ModifierOptions options = new ModifierOptions();
        ModifierInterval interval = new ModifierInterval();
        interval.setInterval(30.0);
        interval.setDeviation(5.0);
        interval.setBehavior("PER_EXECUTOR");
        options.setIntervalSettings(interval);
        ModifierChance chance = new ModifierChance();
        chance.setChance(0.5);
        chance.setBehavior("PER_EXECUTOR");
        options.setSuccessChance(chance);
        ModifierExecution execution = new ModifierExecution();
        execution.setSelection("PICK_RANDOM");
        ModifierPickRandom pick = new ModifierPickRandom();
        pick.setCount(3);
        pick.setBehavior("PER_EXECUTOR");
        execution.setPickRandom(pick);
        options.setExecution(execution);
        options.setDelay(100L);
        behavior.setOptions(options);
        entry.setBehavior(behavior);
        return entry;
    }

    private static ModifierEntry plainEntry() {
        ModifierEntry entry = new ModifierEntry();
        ModifierBehavior behavior = new ModifierBehavior();
        ModifierOptions options = new ModifierOptions();
        ModifierInterval interval = new ModifierInterval();
        interval.setInterval(60.0);
        options.setIntervalSettings(interval);
        behavior.setOptions(options);
        entry.setBehavior(behavior);
        return entry;
    }

    @Test
    void settingLeafGlowsOnlyOnDrift() {
        String path = "settings.match.autostart.enabled";

        assertFalse(ModifiedGlow.leafSetting(config, path));
        ConfigPathMapper.set(root, path, false);
        assertTrue(ModifiedGlow.leafSetting(config, path));
    }

    @Test
    void settingCategoryGlowsWhenAnyChildGlows() {
        assertFalse(ModifiedGlow.section(config, "settings.match"));

        ConfigPathMapper.set(root, "settings.match.autostart.countdown-seconds", 30);

        assertTrue(ModifiedGlow.section(config, "settings.match"));
        assertTrue(ModifiedGlow.section(config, "settings.match.autostart"));
        assertFalse(ModifiedGlow.section(config, "settings.server"));
    }

    @Test
    void behaviorLeavesGlowOnDrift() {
        assertTrue(ModifiedGlow.behaviorEnabled(store, "zebra"));
        assertTrue(ModifiedGlow.behaviorRunsOn(store, "zebra"));
        assertTrue(ModifiedGlow.behaviorInterval(store, "zebra"));
        assertTrue(ModifiedGlow.behaviorDeviation(store, "zebra"));
        assertTrue(ModifiedGlow.behaviorIntervalScope(store, "zebra"));
        assertTrue(ModifiedGlow.behaviorSelection(store, "zebra"));
        assertTrue(ModifiedGlow.behaviorPickCount(store, "zebra"));
        assertTrue(ModifiedGlow.behaviorPickScope(store, "zebra"));
        assertTrue(ModifiedGlow.behaviorDelay(store, "zebra"));
        assertTrue(ModifiedGlow.behaviorChance(store, "zebra"));
        assertTrue(ModifiedGlow.behaviorChanceScope(store, "zebra"));
        assertFalse(ModifiedGlow.behaviorPreStart(store, "zebra"));
    }

    @Test
    void behaviorGroupsGlowWhenAnyLeafGlows() {
        assertTrue(ModifiedGlow.behaviorIntervalGroup(store, "zebra"));
        assertTrue(ModifiedGlow.behaviorExecutionGroup(store, "zebra"));
        assertTrue(ModifiedGlow.behaviorChanceGroup(store, "zebra"));
        assertTrue(ModifiedGlow.behaviorAny(store, "zebra"));

        assertFalse(ModifiedGlow.behaviorIntervalGroup(store, "ghost"));
        assertFalse(ModifiedGlow.behaviorExecutionGroup(store, "ghost"));
        assertFalse(ModifiedGlow.behaviorChanceGroup(store, "ghost"));
        assertFalse(ModifiedGlow.behaviorAny(store, "ghost"));
    }

    @Test
    void explicitDefaultsDoNotGlow() {
        assertFalse(ModifiedGlow.behaviorInterval(store, "plain"));
        assertFalse(ModifiedGlow.behaviorIntervalGroup(store, "plain"));
        assertFalse(ModifiedGlow.behaviorAny(store, "plain"));
    }

}
