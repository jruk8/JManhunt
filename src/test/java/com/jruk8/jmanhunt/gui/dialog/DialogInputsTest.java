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
}
