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
        assertEquals(25.5, validate("settings.compass.actions.manual.analysis.delay-seconds", "25.5").value());

        assertFalse(validate("settings.compass.actions.manual.analysis.delay-seconds", "oops").ok());
        assertFalse(validate("settings.compass.actions.manual.analysis.delay-seconds", "NaN").ok());
        assertFalse(validate("settings.compass.actions.manual.analysis.delay-seconds", "Infinity").ok());
    }

    @Test
    void stringAcceptedTrimmed() {
        var outcome = validate("settings.compass.obtaining.item", "  clock  ");

        assertTrue(outcome.ok());
        assertEquals("clock", outcome.value());
    }

    @Test
    void nullRawFails() {
        assertFalse(SettingRegistry.validate(
                SettingRegistry.byPath("settings.compass.obtaining.item"), null, path -> null).ok());
    }

    @Test
    void toolbarLayoutRequiresNineCharacters() {
        var ok = validate("settings.players.spectator.toolbar.layout", "cp######b");

        assertTrue(ok.ok());
        assertEquals("cp######b", ok.value());
        var shortOutcome = validate("settings.players.spectator.toolbar.layout", "cp###b");
        assertFalse(shortOutcome.ok());
        assertEquals("manhunt.setting-out-of-range", shortOutcome.errorKey());
        assertEquals("exactly 9 characters", shortOutcome.slots().get("bounds"));
        assertFalse(validate(
                "settings.players.spectator.toolbar.layout", "cp########b").ok());
    }

    @Test
    void boundsExitBehaviorAcceptsBothModes() {
        assertEquals("EXIT_LOBBY",
                validate("advanced.lobbies.bounds.exit-behavior", "exit_lobby").value());
        assertEquals("KEEP_IN_LOBBY",
                validate("advanced.lobbies.bounds.exit-behavior", "keep_in_lobby").value());
        assertFalse(validate("advanced.lobbies.bounds.exit-behavior", "block").ok());
    }

    @Test
    void toolbarTpDistanceMinOne() {
        assertEquals(1,
                validate("settings.players.spectator.toolbar.tp-distance", "1").value());
        assertFalse(validate("settings.players.spectator.toolbar.tp-distance", "0").ok());
    }

    @Test
    void snowballCooldownFloorZero() {
        assertEquals(8, validate(
                "settings.players.spectator.toolbar.snowball.cooldown-seconds", "8").value());
        assertFalse(validate(
                "settings.players.spectator.toolbar.snowball.cooldown-seconds", "-1").ok());
        assertEquals(Boolean.TRUE, validate(
                "settings.players.spectator.toolbar.snowball.enabled", "true").value());
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
                validate("settings.server.anti-spawn-camp.first-punishment", "gear_wipe").value());
        assertEquals("postgres", validate("statistics.type", "Postgres").value());
    }

    @Test
    void headstartDelaySecondsRejectBelowZero() {
        for (String side : List.of("speedrunner", "hunter")) {
            String path = "settings.match.headstarts." + side + ".delay-seconds";
            var outcome = validate(path, "-1");

            assertFalse(outcome.ok());
            assertEquals("manhunt.setting-out-of-range", outcome.errorKey());
            assertEquals("at least 0", outcome.slots().get("bounds"));
            assertEquals(0, validate(path, "0").value());
            assertEquals(30, validate(path, "30").value());
        }
    }

    @Test
    void lobbyPresetOptionRemoved() {
        assertNull(SettingRegistry.byPath("world-engine.lobby-preset"));
    }

    @Test
    void compassActionbarRefreshTicksDefaultsToOne() {
        assertEquals("1", SettingRegistry
                .byPath("settings.compass.feedback.actionbar.refresh-ticks").defaultValue());
    }

    @Test
    void worldEngineDefaultsMatchBundledConfig() {
        assertEquals("jmh_lobby",
                SettingRegistry.byPath("advanced.lobbies.lobby-world-name").defaultValue());
        assertEquals("3", SettingRegistry
                .byPath("world-engine.preloading.cell-buffer.stored-cells-buffer").defaultValue());
        assertEquals("jmh_end",
                SettingRegistry.byPath("world-engine.end.base-name").defaultValue());
        assertEquals("3", SettingRegistry.byPath("world-engine.end.buffer").defaultValue());
    }

    @Test
    void pseudoborderParticleDefaultsMatchBundledConfig() {
        assertEquals("1", SettingRegistry
                .byPath("world-engine.world-border.particles.particle-spacing").defaultValue());
        assertEquals("#de7766", SettingRegistry
                .byPath("world-engine.world-border.particles.color").defaultValue());
        assertEquals("10.0", SettingRegistry
                .byPath("world-engine.world-border.particles.render-radius").defaultValue());
        assertEquals("SINE_WAVE", SettingRegistry
                .byPath("world-engine.world-border.particles.pulse-mode").defaultValue());
        assertEquals("0.5", SettingRegistry
                .byPath("world-engine.world-border.particles.interval").defaultValue());
        assertEquals("0.0", SettingRegistry
                .byPath("world-engine.world-border.particles.wave-direction-angle").defaultValue());
        assertEquals("8.0", SettingRegistry
                .byPath("world-engine.world-border.particles.wave-length").defaultValue());
        assertEquals("0.5", SettingRegistry
                .byPath("world-engine.world-border.particles.wave-speed").defaultValue());
        assertEquals("1000", SettingRegistry
                .byPath("world-engine.world-border.particles.max-particles-per-player").defaultValue());
    }

    @Test
    void intBoundsRejectOutsideWithBoundsText() {
        var outcome = validate(
                "settings.compass.signal.interference.light-level.min-sky-light", "16");

        assertFalse(outcome.ok());
        assertEquals("manhunt.setting-out-of-range", outcome.errorKey());
        assertEquals("0 to 15", outcome.slots().get("bounds"));
        assertEquals(15, validate(
                "settings.compass.signal.interference.light-level.min-sky-light", "15").value());
    }

    @Test
    void minusOneOrMinAcceptsSentinelAndMinimum() {
        assertEquals(-1, validate("advanced.lobbies.queue-caps.hunter", "-1").value());
        assertEquals(2, validate("advanced.lobbies.queue-caps.hunter", "2").value());

        var outcome = validate("advanced.lobbies.queue-caps.hunter", "0");
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
        String base = "settings.compass.signal.interference.";
        values.put(base + "light-level.enabled", true);
        values.put(base + "underground.enabled", true);
        values.put(base + "underwater.enabled", true);
        SettingDescriptor descriptor =
                SettingRegistry.byPath("settings.compass.signal.interference.required-to-fail");

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
        assertEquals("at least 0", SettingRegistry.boundsText(
                SettingRegistry.byPath("settings.compass.actions.auto.interval"), (Double) null));
        assertEquals("-1 or at least 5", SettingRegistry.boundsText(
                SettingRegistry.byPath("settings.match.start-on-speedrunner-damage.delay-seconds"),
                (Double) null));
    }

    @Test
    void minusOneSentinelsAcceptOnlyExactNegativeOne() {
        assertTrue(validate("settings.players.respawn.hunter.lives", "-1").ok());
        assertFalse(validate("settings.players.respawn.hunter.lives", "-2").ok());
        assertTrue(validate("advanced.advanced-match-controls.end-delay", "-5").ok());
    }

    @Test
    void topCategoriesAreAlphabetical() {
        assertEquals(List.of("advanced", "settings", "statistics",
                "update-checker", "world-engine"), SettingRegistry.topCategories());
    }

    @Test
    void childrenSplitSectionsLeavesAndLists() {
        var settings = SettingRegistry.children("settings");
        assertEquals(List.of("compass", "match", "players", "server"), settings.sections());
        assertEquals(List.of(), settings.leaves());

        var givenTo = SettingRegistry.children("settings.compass.obtaining.given-to");
        assertEquals(List.of(), givenTo.sections());
        assertEquals(List.of("hunters", "speedrunners"), givenTo.leaves());

        var commands = SettingRegistry.children("settings.compass.actions.manual.analysis.debuffs.commands");
        assertEquals(List.of("hunter", "player", "speedrunner"), commands.sections());
    }

    @Test
    void listPathsResolve() {
        assertTrue(SettingRegistry.isListPath("advanced.advanced-match-controls.end-statistics"));
        assertTrue(SettingRegistry.isListPath("ADVANCED.ADVANCED-MATCH-CONTROLS.END-STATISTICS"));
        assertFalse(SettingRegistry.isListPath("advanced.advanced-match-controls.end-delay"));
        assertEquals("world-engine.preloading.commands",
                SettingRegistry.canonicalListPath("World-Engine.Preloading.Commands"));
        assertNull(SettingRegistry.canonicalListPath("bogus"));
    }

    @Test
    void byPathIsCaseInsensitive() {
        assertEquals("settings.compass.obtaining.item",
                SettingRegistry.byPath("Settings.Compass.Obtaining.Item").path());
        assertNull(SettingRegistry.byPath("bogus.path"));
        assertNull(SettingRegistry.byPath(null));
        assertEquals("settings.compass.obtaining.item",
                SettingRegistry.canonicalPath("Settings.Compass.Obtaining.Item"));
        assertNull(SettingRegistry.canonicalPath("bogus"));
    }

    @Test
    void sectionsDetected() {
        assertTrue(SettingRegistry.isSection("settings"));
        assertTrue(SettingRegistry.isSection("settings.match"));
        assertFalse(SettingRegistry.isSection("settings.compass.obtaining.item"));
        assertFalse(SettingRegistry.isSection("bogus"));
    }

    @Test
    void advancedRootDrillsIntoThreeSections() {
        assertEquals(List.of("advanced-match-controls", "lobbies", "misc"),
                SettingRegistry.children("advanced").sections());
        assertEquals(List.of("interop", "modifier-editor"),
                SettingRegistry.children("advanced.misc").sections());
    }

    @Test
    void movedDefaultsMirrorTheirSpecs() {
        assertEquals("30.0", SettingRegistry.byPath(
                "advanced.advanced-match-controls.start-reminder-interval").defaultValue());
        assertTrue(SettingRegistry.isListPath(
                "advanced.advanced-match-controls.game-rules.rules"));
        assertEquals(6, MatchConfig.GameRules.DEFAULT_RULES.size());
        assertTrue(MatchConfig.GameRules.DEFAULT_RULES.contains("DISABLE_WANDERING_TRADER"));
        assertEquals("jmh_lobby", SettingRegistry.byPath(
                "advanced.lobbies.lobby-world-name").defaultValue());
    }
}
