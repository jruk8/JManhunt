package com.jruk8.jmanhunt.gui.dialog;

import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.config.SettingType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Headless dialog input choice, shared by the Paper implementation and
 * unit tests. Nothing here touches client classes, so this loads without
 * a server while {@link SettingDialogs} cannot.
 */
final class DialogInputs {

    /** Text input ceiling: the classic full-string cap, far above any sane value. */
    static final int TEXT_MAX_LENGTH = 32767;

    private DialogInputs() {
    }

    /**
     * Dialog initial text from a live value: null becomes blank and
     * overlong values fall back to blank so a huge current value never
     * refuses the open; callers echo the full value in the body instead.
     */
    static String safeInitial(String current) {
        if (current == null || current.length() > TEXT_MAX_LENGTH) {
            return "";
        }
        return current;
    }

    /**
     * True when the setting gets the number-range slider: INT or FLOAT
     * with both bounds static. Dynamic maxima and -1 sentinels use the
     * text field with server-side validation instead.
     */
    static boolean useNumberRange(SettingDescriptor descriptor) {
        return (descriptor.type() == SettingType.INT
                || descriptor.type() == SettingType.FLOAT)
                && descriptor.min() != null
                && descriptor.max() != null;
    }

    /**
     * Raw setter text from a slider response, or null when the response
     * carries no value. Integers round to whole numbers. The response is
     * clamped into the static bounds first, so a drifting slider can never
     * submit an out-of-range value.
     */
    static String submitText(SettingDescriptor descriptor, Float response) {
        if (response == null) {
            return null;
        }
        float value = response;
        if (descriptor.min() != null && descriptor.max() != null) {
            value = clamp(value, descriptor.min().floatValue(),
                    descriptor.max().floatValue());
        }
        if (descriptor.type() == SettingType.INT) {
            return String.valueOf(Math.round(value));
        }
        return String.valueOf(value);
    }

    /**
     * Value pinned into [min, max]. NaN falls back to min and infinities
     * to the nearest bound, so slider math never escapes the range.
     */
    static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) {
            return min;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    /** Float display with three decimals for dialog body lines. */
    static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /**
     * Runs On input key for one trigger row. Letters, digits, and
     * underscores only: Paper rejects anything else because input keys
     * must satisfy StringTemplate.isValidVariableName.
     */
    static String triggerKey(int index) {
        return "trigger_" + index;
    }

    /**
     * Checked triggers in known order. Missing or unreadable answers
     * count as unchecked, so a partial response never enables extras.
     * Takes the answer lookup instead of the response view so headless
     * tests never link the client dialog classes.
     */
    static Set<String> checkedTriggers(Function<String, Boolean> answer, List<String> known) {
        Set<String> checked = new LinkedHashSet<>();
        for (int index = 0; index < known.size(); index++) {
            if (Boolean.TRUE.equals(answer.apply(triggerKey(index)))) {
                checked.add(known.get(index));
            }
        }
        return checked;
    }
}
