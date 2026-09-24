package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.match.prestart.OnExpire;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Setting reads, typed writes, and list operations. The saver is a
 * no-op because bare schema instances have no Okaeri binder; managed
 * saves are covered by booting the plugin.
 */
class ConfigServiceValuesTest {

    private ConfigService service;

    @BeforeEach
    void setup() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        service = new ConfigService(new JManhuntConfig(),
                new ModifierStore(new ModifiersConfig(), log), () -> {});
    }

    @Test
    void setValueBoolRoundtripsWithOldAndNew() {
        ConfigService.SetOutcome outcome =
                service.setValue("settings.match.autostart.enabled", "false");

        assertTrue(outcome.ok());
        assertEquals(true, outcome.oldValue());
        assertEquals(false, outcome.newValue());
        assertFalse(service.getBoolean("settings.match.autostart.enabled", true));
    }

    @Test
    void setValueRejectsBadBool() {
        ConfigService.SetOutcome outcome =
                service.setValue("settings.match.autostart.enabled", "maybe");

        assertFalse(outcome.ok());
        assertEquals("manhunt.setting-invalid-value", outcome.errorKey());
        assertEquals("settings.match.autostart.enabled", outcome.slots().get("setting"));
    }

    @Test
    void setValueEnforcesBounds() {
        ConfigService.SetOutcome outcome = service.setValue(
                "settings.compass.signal-interference.light-level.min-sky-light", "16");

        assertFalse(outcome.ok());
        assertEquals("manhunt.setting-out-of-range", outcome.errorKey());
        assertEquals("0 to 15", outcome.slots().get("bounds"));

        assertTrue(service.setValue(
                "settings.compass.signal-interference.light-level.min-sky-light", "15").ok());
    }

    @Test
    void setValueOptionStoresCanonical() {
        ConfigService.SetOutcome outcome = service.setValue(
                "settings.match.start-on-speedrunner-damage.on-expire", "cancel");

        assertTrue(outcome.ok());
        assertEquals("CANCEL", service.getString(
                "settings.match.start-on-speedrunner-damage.on-expire", "FORCE_START"));
    }

    @Test
    void setValueOptionRejectsUnknown() {
        ConfigService.SetOutcome outcome = service.setValue(
                "settings.match.start-on-speedrunner-damage.on-expire", "maybe");

        assertFalse(outcome.ok());
        assertEquals("manhunt.setting-invalid-option", outcome.errorKey());
        assertEquals("CANCEL, FORCE_START", outcome.slots().get("valid"));
    }

    @Test
    void setValueUnknownFails() {
        ConfigService.SetOutcome outcome = service.setValue("bogus.path", "1");

        assertFalse(outcome.ok());
        assertEquals("manhunt.setting-invalid", outcome.errorKey());
    }

    @Test
    void setValueIndexPathReplacesEntry() {
        ConfigService.SetOutcome outcome =
                service.setValue("match.end-statistics.0", "PROGRESSION");

        assertTrue(outcome.ok());
        assertEquals("DAMAGE_DEALT", outcome.oldValue());
        assertEquals(List.of("PROGRESSION", "HUNTER_FINAL_KILLS", "SPEEDRUNNER_KILLS", "PROGRESSION"),
                service.getStringList("match.end-statistics"));
    }

    @Test
    void listAddAndRemoveRoundtrip() {
        ConfigService.SetOutcome added = service.listAdd("match.end-statistics", "EXTRA");

        assertTrue(added.ok());
        assertEquals(5, service.getStringList("match.end-statistics").size());

        ConfigService.SetOutcome removed = service.listRemove("match.end-statistics", 4);
        assertTrue(removed.ok());
        assertEquals("EXTRA", removed.oldValue());
        assertEquals(4, service.getStringList("match.end-statistics").size());
    }

    @Test
    void listRemoveOutOfRangeReportsSize() {
        ConfigService.SetOutcome outcome = service.listRemove("match.end-statistics", 9);

        assertFalse(outcome.ok());
        assertEquals("manhunt.setting-index-invalid", outcome.errorKey());
        assertEquals("4", outcome.slots().get("size"));
    }

    @Test
    void isModifiedComparesAgainstDefaults() {
        assertFalse(service.isModified("settings.match.autostart.countdown-seconds"));

        assertTrue(service.setValue("settings.match.autostart.countdown-seconds", "60").ok());
        assertTrue(service.isModified("settings.match.autostart.countdown-seconds"));

        assertTrue(service.setValue("settings.match.autostart.countdown-seconds", "45").ok());
        assertFalse(service.isModified("settings.match.autostart.countdown-seconds"));
    }

    @Test
    void defaultValueReadsSchema() {
        assertEquals(45, service.defaultValue("settings.match.autostart.countdown-seconds"));
        assertEquals("FORCE_START",
                service.defaultValue("settings.match.start-on-speedrunner-damage.on-expire"));
        assertEquals("compass", service.defaultValue("settings.compass.item"));
        assertNull(service.defaultValue("bogus.path"));
    }

    @Test
    void displayValueTrimsFloatsToThreeDecimals() {
        assertEquals("10", ConfigService.displayValue(10.0));
        assertEquals("0.5", ConfigService.displayValue(0.5));
        assertEquals("1.235", ConfigService.displayValue(1.23456));
        assertEquals("FORCE_START", ConfigService.displayValue(OnExpire.FORCE_START));
        assertEquals("clock", ConfigService.displayValue("clock"));
    }

    @Test
    void boolWritesFireChangeListeners() {
        List<String> events = new ArrayList<>();
        service.onChange("settings.match.autostart.enabled",
                (oldValue, newValue) -> events.add(oldValue + "->" + newValue));

        assertTrue(service.setBoolean("settings.match.autostart.enabled", false));
        assertEquals(List.of("true->false"), events);
        assertFalse(service.setBoolean("bogus.path", true));
    }

    @Test
    void getEnumReadsTypedOptions() {
        assertEquals(OnExpire.FORCE_START, service.getEnum(
                "settings.match.start-on-speedrunner-damage.on-expire",
                OnExpire.class, OnExpire.CANCEL));
    }

    @Test
    void lobbyPresetKeysReadMap() {
        assertEquals(Set.of("EMPTY", "DEFAULT", "ADVANCED"), service.lobbyPresetKeys());
    }

    @Test
    void indexPathsDetected() {
        assertTrue(service.isIndexPath("match.end-statistics.0"));
        assertFalse(service.isIndexPath("match.end-statistics"));
        assertFalse(service.isIndexPath("match.end-delay"));
        assertTrue(service.isList("match.end-statistics"));
        assertFalse(service.isList("match.end-delay"));
    }
}
