package com.jruk8.jmanhunt.gui.dialog;

import com.jruk8.jmanhunt.config.SettingRegistry;
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
}
