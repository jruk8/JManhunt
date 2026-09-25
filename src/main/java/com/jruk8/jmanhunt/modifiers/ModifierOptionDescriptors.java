package com.jruk8.jmanhunt.modifiers;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

/**
 * Display data for every modifier option row, mirroring the settings
 * registry shape: label, icon, description, kind, bounds or choices,
 * default text, and the config path below the modifier id. Trigger
 * rows leave options null; the menu supplies the known trigger list.
 */
public final class ModifierOptionDescriptors {

    /** Row kinds: number and integer edits, choice cycles, trigger dialog. */
    public enum Kind {
        NUMBER,
        INTEGER,
        CHOICE,
        TRIGGERS
    }

    /**
     * @param label button name
     * @param icon button material
     * @param description one-line field help
     * @param kind row kind
     * @param allowed bounds text, null when the row has none
     * @param options choice options, null when the row is not a choice
     * @param defaultText default display for the Default line
     * @param pathSuffix dotted path below {@code modifiers.<id>}
     */
    public record Descriptor(String label, Material icon, String description, Kind kind,
            String allowed, List<String> options, String defaultText, String pathSuffix) {
    }

    private static final Map<String, Descriptor> BY_KEY = new LinkedHashMap<>();

    static {
        register("delay", "Delay", Material.WHITE_WOOL,
                "Ticks to wait after the trigger before commands run.",
                Kind.INTEGER, "0 or more", null, "Not set",
                "behavior.options.delay");
        register("interval", "Interval", Material.CLOCK,
                "Seconds between runs while INTERVAL is selected.",
                Kind.NUMBER, "0 or more", null, "Not set",
                "behavior.options.interval-settings.interval");
        register("deviation", "Deviation", Material.COMPASS,
                "Random jitter added to each interval, never above it.",
                Kind.NUMBER, "0 up to interval", null, "Not set",
                "behavior.options.interval-settings.deviation");
        register("interval-scope", "Interval scope", Material.REPEATER,
                "Whether the interval clock is shared or runs per player.",
                Kind.CHOICE, null, List.of("PER_INVOKE", "PER_EXECUTOR"), "PER_INVOKE",
                "behavior.options.interval-settings.behavior");
        register("selection", "Selection", Material.DISPENSER,
                "How command lines are picked on each run.",
                Kind.CHOICE, null, List.of("IN_ORDER", "PICK_RANDOM"), "IN_ORDER",
                "behavior.options.execution.selection");
        register("pick-count", "Pick count", Material.DROPPER,
                "How many lines each PICK_RANDOM draw takes.",
                Kind.INTEGER, "1 or more", null, "1",
                "behavior.options.execution.pick-random.count");
        register("pick-scope", "Pick scope", Material.OBSERVER,
                "Whether the random draw is shared or rolled per player.",
                Kind.CHOICE, null, List.of("PER_INVOKE", "PER_EXECUTOR"), "PER_INVOKE",
                "behavior.options.execution.pick-random.behavior");
        register("pre-start", "Pre-start order", Material.HOPPER,
                "Whether ON_START fires before or after the pre-start window.",
                Kind.CHOICE, null, List.of("IN_ORDER", "AFTER"), "IN_ORDER",
                "behavior.on-start.pre-start-order");
        register("chance", "Chance", Material.EXPERIENCE_BOTTLE,
                "Probability the modifier runs at all, from 0 to 1.",
                Kind.NUMBER, "0 to 1", null, "100%",
                "behavior.options.success-chance.chance");
        register("chance-scope", "Chance scope", Material.DAYLIGHT_DETECTOR,
                "Whether the chance roll is shared or rolled per player.",
                Kind.CHOICE, null, List.of("PER_INVOKE", "PER_EXECUTOR"), "PER_INVOKE",
                "behavior.options.success-chance.behavior");
        register("runs-on", "Runs On", Material.LEVER,
                "Events that trigger this modifier.",
                Kind.TRIGGERS, null, null, "ON_START",
                "behavior.runs-on");
    }

    private ModifierOptionDescriptors() {
    }

    private static void register(String key, String label, Material icon, String description,
            Kind kind, String allowed, List<String> options, String defaultText,
            String pathSuffix) {
        BY_KEY.put(key, new Descriptor(label, icon, description, kind, allowed,
                options, defaultText, pathSuffix));
    }

    /** Descriptor for one option key, or null when unknown. */
    public static Descriptor byKey(String key) {
        return BY_KEY.get(key);
    }

    /** All registered option keys in registration order. */
    public static Set<String> keys() {
        return new LinkedHashSet<>(BY_KEY.keySet());
    }

    /** Schema type name for one row kind. */
    public static String typeName(Kind kind) {
        return switch (kind) {
            case NUMBER -> "Number";
            case INTEGER -> "Integer";
            case CHOICE, TRIGGERS -> "Choice";
        };
    }
}
