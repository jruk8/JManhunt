package com.jruk8.jmanhunt.config;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Typed validation for {@code /mh config} values: parsing, bounds,
 * options, and drill structure. Replaces the retired SettingValueParser
 * cases and extends them with the new type safety.
 */
class SettingRegistryTest {

    private static Function<String, Object> lookup(Map<String, Object> values) {
        return values::get;
    }

    private static SettingRegistry.ValidationOutcome validate(String path, String raw) {
        return SettingRegistry.validate(SettingRegistry.byPath(path), raw, key -> null);
    }

    @Test
    void boolAcceptsTrueAndFalseCaseInsensitively() {
        assertEquals(true, validate("settings.match.autostart.enabled", "TRUE").value());
        assertEquals(false, validate("settings.match.autostart.enabled", "False").value());
    }

    @Test
    void boolRejectsOtherValues() {
        var outcome = validate("settings.match.autostart.enabled", "not-a-bool");

        assertFalse(outcome.ok());
        assertEquals("manhunt.setting-invalid-value", outcome.errorKey());
    }

    @Test
    void intParsesAndRejectsNonNumeric() {
        assertEquals(137, validate("settings.match.autostart.countdown-seconds", "137").value());

        var outcome = validate("settings.match.autostart.countdown-seconds", "abc");
        assertFalse(outcome.ok());
        assertEquals("manhunt.setting-invalid-number", outcome.errorKey());
    }

    @Test
    void floatParsesAndRejectsNonNumericAndNonFinite() {
        assertEquals(25.5, validate("settings.compass.analyze.delay-seconds", "25.5").value());

        assertFalse(validate("settings.compass.analyze.delay-seconds", "oops").ok());
        assertFalse(validate("settings.compass.analyze.delay-seconds", "NaN").ok());
        assertFalse(validate("settings.compass.analyze.delay-seconds", "Infinity").ok());
    }

    @Test
    void stringAcceptedTrimmed() {
        var outcome = validate("settings.compass.item", "  clock  ");

        assertTrue(outcome.ok());
        assertEquals("clock", outcome.value());
    }

    @Test
    void nullRawFails() {
        assertFalse(SettingRegistry.validate(
                SettingRegistry.byPath("settings.compass.item"), null, path -> null).ok());
    }

    @Test
    void optionMatchesCaseInsensitivelyToCanonical() {
        var outcome = validate("settings.match.start-on-speedrunner-damage.on-expire", "cancel");

        assertTrue(outcome.ok());
        assertEquals("CANCEL", outcome.value());
    }

    @Test
    void optionRejectsUnknownListingValid() {
        var outcome = validate("settings.match.start-on-speedrunner-damage.on-expire", "maybe");

        assertFalse(outcome.ok());
        assertEquals("manhunt.setting-invalid-option", outcome.errorKey());
        assertEquals("CANCEL, FORCE_START", outcome.slots().get("valid"));
    }

    @Test
    void optionAliasesCanonicalize() {
        assertEquals("GEAR-WIPE",
                validate("settings.server.anti-spawn-camp.punishment", "gear_wipe").value());
        assertEquals("postgres", validate("statistics.type", "Postgres").value());
    }

    @Test
    void intBoundsRejectOutsideWithBoundsText() {
        var outcome = validate(
                "settings.compass.signal-interference.light-level.min-sky-light", "16");

        assertFalse(outcome.ok());
        assertEquals("manhunt.setting-out-of-range", outcome.errorKey());
        assertEquals("0 to 15", outcome.slots().get("bounds"));
        assertEquals(15, validate(
                "settings.compass.signal-interference.light-level.min-sky-light", "15").value());
    }

    @Test
    void minusOneOrMinAcceptsSentinelAndMinimum() {
        assertEquals(-1, validate("lobbies.queue-caps.hunter", "-1").value());
        assertEquals(2, validate("lobbies.queue-caps.hunter", "2").value());

        var outcome = validate("lobbies.queue-caps.hunter", "0");
        assertFalse(outcome.ok());
        assertEquals("-1 or at least 1", outcome.slots().get("bounds"));

        assertEquals(-1,
                validate("settings.match.start-on-speedrunner-damage.delay-seconds", "-1").value());
        assertFalse(validate(
                "settings.match.start-on-speedrunner-damage.delay-seconds", "4").ok());
        assertEquals(5,
                validate("settings.match.start-on-speedrunner-damage.delay-seconds", "5").value());
    }

    @Test
    void dynamicCellHalfCapsSpreadRadius() {
        Map<String, Object> values = new HashMap<>();
        values.put("world-engine.cell-size", 100);
        SettingDescriptor descriptor =
                SettingRegistry.byPath("world-engine.tp-spread-radius");

        assertEquals(50, SettingRegistry.validate(descriptor, "50", lookup(values)).value());
        var outcome = SettingRegistry.validate(descriptor, "51", lookup(values));
        assertFalse(outcome.ok());
        assertEquals("0 to 50", outcome.slots().get("bounds"));
    }

    @Test
    void dynamicEnabledCountCapsRequiredToFail() {
        Map<String, Object> values = new HashMap<>();
        String base = "settings.compass.signal-interference.";
        values.put(base + "light-level.enabled", true);
        values.put(base + "underground.enabled", true);
        values.put(base + "underwater.enabled", true);
        SettingDescriptor descriptor =
                SettingRegistry.byPath("settings.compass.signal-interference.required-to-fail");

        assertEquals(3, SettingRegistry.validate(descriptor, "3", lookup(values)).value());
        var outcome = SettingRegistry.validate(descriptor, "4", lookup(values));
        assertFalse(outcome.ok());
        assertEquals("1 to 3", outcome.slots().get("bounds"));
    }

    @Test
    void boundsTextFormatsOpenEnds() {
        assertEquals("at least 1", SettingRegistry.boundsText(
                SettingRegistry.byPath("statistics.pool-size"), (Double) null));
        assertEquals("at least 0", SettingRegistry.boundsText(
                SettingRegistry.byPath("settings.match.autostart.countdown-seconds"), (Double) null));
        assertEquals("at least 1", SettingRegistry.boundsText(
                SettingRegistry.byPath("settings.match.autostart.minimums.hunter"), (Double) null));
        assertEquals("-1 or at least 0", SettingRegistry.boundsText(
                SettingRegistry.byPath("settings.compass.refresh-interval"), (Double) null));
        assertEquals("-1 or at least 5", SettingRegistry.boundsText(
                SettingRegistry.byPath("settings.match.start-on-speedrunner-damage.delay-seconds"),
                (Double) null));
    }

    @Test
    void minusOneSentinelsAcceptOnlyExactNegativeOne() {
        assertTrue(validate("settings.compass.refresh-interval", "-1").ok());
        assertTrue(validate("settings.compass.refresh-interval", "5").ok());
        var refreshBad = validate("settings.compass.refresh-interval", "-0.5");
        assertFalse(refreshBad.ok());
        assertEquals("manhunt.setting-out-of-range", refreshBad.errorKey());
        assertEquals("-1 or at least 0", refreshBad.slots().get("bounds"));

        assertTrue(validate("settings.players.respawn.hunter.lives", "-1").ok());
        assertFalse(validate("settings.players.respawn.hunter.lives", "-2").ok());
        assertTrue(validate("match.end-delay", "-5").ok());
    }

    @Test
    void topCategoriesAreAlphabetical() {
        assertEquals(List.of("debug", "lobbies", "match", "settings", "statistics",
                "update-checker", "world-engine"), SettingRegistry.topCategories());
    }

    @Test
    void childrenSplitSectionsLeavesAndLists() {
        var settings = SettingRegistry.children("settings");
        assertEquals(List.of("compass", "match", "players", "server"), settings.sections());
        assertEquals(List.of(), settings.leaves());

        var givenTo = SettingRegistry.children("settings.compass.given-to");
        assertEquals(List.of(), givenTo.sections());
        assertEquals(List.of("hunters", "speedrunners"), givenTo.leaves());

        var commands = SettingRegistry.children("settings.compass.analyze.debuffs.commands");
        assertEquals(List.of("hunter", "player", "speedrunner"), commands.sections());
    }

    @Test
    void listPathsResolve() {
        assertTrue(SettingRegistry.isListPath("match.end-statistics"));
        assertTrue(SettingRegistry.isListPath("MATCH.END-STATISTICS"));
        assertFalse(SettingRegistry.isListPath("match.end-delay"));
        assertEquals("world-engine.lobby-presets.DEFAULT.commands",
                SettingRegistry.canonicalListPath("world-engine.lobby-presets.default.commands"));
        assertNull(SettingRegistry.canonicalListPath("bogus"));
    }

    @Test
    void byPathIsCaseInsensitive() {
        assertEquals("settings.compass.item",
                SettingRegistry.byPath("Settings.Compass.Item").path());
        assertNull(SettingRegistry.byPath("bogus.path"));
        assertNull(SettingRegistry.byPath(null));
        assertEquals("settings.compass.item",
                SettingRegistry.canonicalPath("Settings.Compass.Item"));
        assertNull(SettingRegistry.canonicalPath("bogus"));
    }

    @Test
    void sectionsDetected() {
        assertTrue(SettingRegistry.isSection("settings"));
        assertTrue(SettingRegistry.isSection("settings.match"));
        assertFalse(SettingRegistry.isSection("settings.compass.item"));
        assertFalse(SettingRegistry.isSection("bogus"));
    }
}
