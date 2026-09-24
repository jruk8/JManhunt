package com.jruk8.jmanhunt.gui.dialog;

import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.config.SettingType;

/**
 * Headless dialog input choice, shared by the Paper implementation and
 * unit tests. Nothing here touches client classes, so this loads without
 * a server while {@link SettingDialogs} cannot.
 */
final class DialogInputs {

    private DialogInputs() {
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
     * carries no value. Integers round to whole numbers.
     */
    static String submitText(SettingDescriptor descriptor, Float response) {
        if (response == null) {
            return null;
        }
        if (descriptor.type() == SettingType.INT) {
            return String.valueOf(Math.round(response));
        }
        return String.valueOf(response);
    }
}
