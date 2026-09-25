package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.message.MessageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One lore schema for every config field, shared by the settings
 * browser and the modifier option rows. Order is fixed: description,
 * Value, Path, Type, Allowed bounds or option bullets, Default,
 * then the click hint and the reset hint.
 */
public final class FieldLore {

    /**
     * @param description field help, blank hides the block
     * @param value live display value for the Value line
     * @param path dotted config path for the Path line
     * @param type display type name for the Type line
     * @param allowed bounds text, null hides the Allowed line
     * @param options choice options, null hides the bullets
     * @param marked effective options, marked green in the bullets
     * @param defaultText default display for the Default line
     * @param hint click affordance line
     */
    public record Field(String description, String value, String path, String type,
            String allowed, List<String> options, Set<String> marked,
            String defaultText, String hint) {
    }

    private FieldLore() {
    }

    /** Raw lore lines in schema order; callers render them as components. */
    public static List<String> lines(MessageService messages, Field field) {
        List<String> lines = new ArrayList<>();
        if (field.description() != null && !field.description().isBlank()) {
            lines.add(field.description());
            lines.add("");
        }
        lines.add(template(messages, "manhunt-gui.setting-value",
                "Value: <white>{value}", field.value()));
        lines.add(template(messages, "manhunt-gui.setting-path",
                "Path: <white>{path}", field.path()));
        lines.add(template(messages, "manhunt-gui.setting-type",
                "Type: <white>{type}", field.type()));
        if (field.allowed() != null) {
            lines.add(template(messages, "manhunt-gui.dialog-bounds",
                    "Allowed: <white>{bounds}", field.allowed()));
        }
        if (field.options() != null) {
            for (String option : field.options()) {
                if (field.marked() != null && field.marked().contains(option)) {
                    lines.add("<green>» " + option + "</green>");
                } else {
                    lines.add("» " + option);
                }
            }
        }
        lines.add(template(messages, "manhunt-gui.setting-default",
                "Default: <white>{value}", field.defaultText()));
        lines.add("");
        lines.add(field.hint());
        lines.add(messages.string("manhunt-gui.setting-hint-reset", "Right-click to reset"));
        return lines;
    }

    private static String template(MessageService messages, String key,
            String fallback, String value) {
        String line = messages.string(key, fallback);
        return value == null ? line : line.replace("{value}", value)
                .replace("{path}", value).replace("{type}", value).replace("{bounds}", value);
    }
}
