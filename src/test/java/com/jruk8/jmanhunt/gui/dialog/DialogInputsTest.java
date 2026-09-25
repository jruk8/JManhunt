package com.jruk8.jmanhunt.gui.dialog;

import com.jruk8.jmanhunt.config.SettingRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dialog input choice: numbers with both static bounds get the slider,
 * everything else gets the text field, and slider responses convert back
 * to setter text.
 */
class DialogInputsTest {

    @Test
    void fullyBoundedNumbersUseTheSlider() {
        assertTrue(DialogInputs.useNumberRange(
                SettingRegistry.byPath("settings.compass.signal-interference.light-level.min-sky-light")));
        assertTrue(DialogInputs.useNumberRange(
                SettingRegistry.byPath("settings.compass.signal-interference.chance-to-bypass")));
    }

    @Test
    void openEndedSentinelAndDynamicBoundsUseText() {
        assertFalse(DialogInputs.useNumberRange(
                SettingRegistry.byPath("settings.match.autostart.countdown-seconds")));
        assertFalse(DialogInputs.useNumberRange(
                SettingRegistry.byPath("settings.compass.refresh-interval")));
        assertFalse(DialogInputs.useNumberRange(
                SettingRegistry.byPath("settings.compass.signal-interference.required-to-fail")));
        assertFalse(DialogInputs.useNumberRange(
                SettingRegistry.byPath("settings.compass.item")));
    }

    @Test
    void sliderResponsesConvertToSetterText() {
        assertEquals("3", DialogInputs.submitText(
                SettingRegistry.byPath("settings.compass.signal-interference.underground.max-blocks-above"),
                2.6f));
        assertEquals("0.5", DialogInputs.submitText(
                SettingRegistry.byPath("settings.compass.signal-interference.chance-to-bypass"),
                0.5f));
        assertNull(DialogInputs.submitText(
                SettingRegistry.byPath("settings.compass.signal-interference.chance-to-bypass"),
                null));
    }

    @Test
    void sliderResponsesClampIntoBounds() {
        assertEquals("1.0", DialogInputs.submitText(
                SettingRegistry.byPath("settings.compass.signal-interference.chance-to-bypass"),
                9.5f));
        assertEquals("0.0", DialogInputs.submitText(
                SettingRegistry.byPath("settings.compass.signal-interference.chance-to-bypass"),
                -2.0f));
    }

    @Test
    void clampPinsAndTamesNonFiniteValues() {
        assertEquals(0.5f, DialogInputs.clamp(0.5f, 0.0f, 1.0f));
        assertEquals(0.0f, DialogInputs.clamp(-3.0f, 0.0f, 1.0f));
        assertEquals(1.0f, DialogInputs.clamp(42.0f, 0.0f, 1.0f));
        assertEquals(0.0f, DialogInputs.clamp(Float.NaN, 0.0f, 1.0f));
        assertEquals(1.0f, DialogInputs.clamp(Float.POSITIVE_INFINITY, 0.0f, 1.0f));
        assertEquals(0.0f, DialogInputs.clamp(Float.NEGATIVE_INFINITY, 0.0f, 1.0f));
    }

    @Test
    void floatsFormatToThreeDecimals() {
        assertEquals("0.500", DialogInputs.formatFloat(0.5f));
        assertEquals("1.000", DialogInputs.formatFloat(1.0f));
        assertEquals("0.333", DialogInputs.formatFloat(1.0f / 3.0f));
    }

    @Test
    void safeInitialPrefillsAndFallsBackWhenOverlong() {
        assertEquals("Speedy", DialogInputs.safeInitial("Speedy"));
        assertEquals("", DialogInputs.safeInitial(null));
        assertEquals("", DialogInputs.safeInitial("x".repeat(DialogInputs.TEXT_MAX_LENGTH + 1)));
        assertEquals("ok", DialogInputs.safeInitial("ok"));
    }

    @Test
    void checkedBoxesMapToTriggersInKnownOrder() {
        Map<String, Boolean> answers = Map.of(
                "trigger_1", true,
                "trigger_0", true,
                "trigger_3", false);

        Set<String> checked = DialogInputs.checkedTriggers(answers::get,
                com.jruk8.jmanhunt.match.ModifierTriggers.KNOWN);

        assertEquals(Set.of("ON_START", "INTERVAL"), checked);
        assertEquals(List.of("ON_START", "INTERVAL"), List.copyOf(checked));
    }

    @Test
    void missingAnswersCountAsUnchecked() {
        assertEquals(Set.of(), DialogInputs.checkedTriggers(key -> null,
                com.jruk8.jmanhunt.match.ModifierTriggers.KNOWN));
    }

    @Test
    void triggerKeysAreIndexed() {
        assertEquals("trigger_0", DialogInputs.triggerKey(0));
        assertEquals("trigger_13", DialogInputs.triggerKey(13));
        assertEquals(14, com.jruk8.jmanhunt.match.ModifierTriggers.KNOWN.size());
    }

    @Test
    void triggerKeysFitPaperInputNameGrammar() {
        // Paper rejects dialog input keys outside
        // StringTemplate.isValidVariableName (letters, digits, underscore).
        for (int index = 0; index < com.jruk8.jmanhunt.match.ModifierTriggers.KNOWN.size(); index++) {
            assertTrue(DialogInputs.triggerKey(index).matches("[A-Za-z0-9_]+"),
                    DialogInputs.triggerKey(index));
        }
    }
}
