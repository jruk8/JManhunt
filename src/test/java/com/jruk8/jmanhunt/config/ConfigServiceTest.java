package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toggle and preset semantics. The plugin is null because the
 * store-backed paths under test never touch it; the config object
 * is assembled in memory so no files are involved.
 */
class ConfigServiceTest {

    private ConfigService service;

    @BeforeEach
    void setup() {
        ModifiersConfig config = new ModifiersConfig();
        entry(config, "beef", true);
        entry(config, "bare", false);
        preset(config, "mixed", List.of("beef", "bare"));
        preset(config, "lonely", List.of("missing"));
        preset(config, "empty", List.of());
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        service = new ConfigService(null, new ModifierStore(config, log));
    }

    @Test
    void setModifierEnabledRoundtrips() {
        assertTrue(service.setModifierEnabled("bare", true));
        assertTrue(service.modifierEnabled("bare"));
        assertTrue(service.setModifierEnabled("beef", false));
        assertFalse(service.modifierEnabled("beef"));
    }

    @Test
    void setModifierEnabledUnknownReturnsFalse() {
        assertFalse(service.setModifierEnabled("missing", true));
    }

    @Test
    void presetNamesAndMembersReadThrough() {
        assertEquals(3, service.presetNames().size());
        assertEquals(List.of("beef", "bare"), service.presetMembers("mixed"));
        assertTrue(service.presetMembers("missing").isEmpty());
    }

    @Test
    void presetReadsOnOnlyWhenEveryMemberIsOn() {
        assertFalse(service.presetEnabled("mixed"));
        assertTrue(service.setModifierEnabled("bare", true));
        assertTrue(service.presetEnabled("mixed"));
    }

    @Test
    void presetReadsOffWhenUnknownOrMemberless() {
        assertFalse(service.presetEnabled("missing"));
        assertFalse(service.presetEnabled("empty"));
        assertFalse(service.presetEnabled("lonely"));
    }

    @Test
    void setPresetFlipsEveryMemberAtOnce() {
        assertTrue(service.setPreset("mixed", true));
        assertTrue(service.modifierEnabled("beef"));
        assertTrue(service.modifierEnabled("bare"));
        assertTrue(service.presetEnabled("mixed"));

        assertTrue(service.setPreset("mixed", false));
        assertFalse(service.modifierEnabled("beef"));
        assertFalse(service.modifierEnabled("bare"));
    }

    @Test
    void setPresetUnknownReturnsFalse() {
        assertFalse(service.setPreset("missing", true));
    }

    @Test
    void setPresetSkipsUnknownMembers() {
        assertTrue(service.setPreset("lonely", true));
        assertFalse(service.presetEnabled("lonely"));
    }

    private static void entry(ModifiersConfig config, String name, boolean enabled) {
        ModifierEntry entry = new ModifierEntry();
        entry.setEnabled(enabled);
        config.getModifiers().put(name, entry);
    }

    private static void preset(ModifiersConfig config, String id, List<String> members) {
        ModifierPreset preset = new ModifierPreset();
        preset.setModifiers(new ArrayList<>(members));
        config.getPresets().put(id, preset);
    }
}
