package com.jruk8.jmanhunt.modifiers;

import com.jruk8.jmanhunt.modifiers.config.ModifierBehavior;
import com.jruk8.jmanhunt.modifiers.config.ModifierCommands;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierExecution;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import org.bukkit.Material;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Owns modifiers.yml behind the Okaeri model: modifier toggles,
 * typed behavior lookups, presets, and display metadata. Item names
 * resolve without a running server so unit tests can exercise every
 * fallback.
 */
public final class ModifierStore {
    public static final String DEFAULT_NAME = "My Modifier";
    public static final String DEFAULT_DESCRIPTION = "Enable for a twist!";

    private final ModifiersConfig config;
    private final Logger log;
    private final Set<String> warnedItems = new HashSet<>();

    public ModifierStore(ModifiersConfig config, Logger log) {
        this.config = config;
        this.log = log;
    }

    /** Clears one-per-load item warnings, e.g. after /mh reload. */
    public void clearItemWarnings() {
        warnedItems.clear();
    }

    public Set<String> modifierNames() {
        return new LinkedHashSet<>(config.getModifiers().keySet());
    }

    public boolean isEnabled(String name) {
        ModifierEntry entry = config.getModifiers().get(name);
        return entry != null && entry.isEnabled();
    }

    /**
     * Sets enabled and saves. Returns false when the modifier is unknown;
     * save failures only log since the in-memory value already applied.
     */
    public boolean setEnabled(String name, boolean value) {
        ModifierEntry entry = config.getModifiers().get(name);
        if (entry == null) {
            return false;
        }
        entry.setEnabled(value);
        save();
        return true;
    }

    /** Trigger names, or empty when the modifier omits runs-on. */
    public List<String> runsOn(String name) {
        ModifierBehavior behavior = behavior(name);
        return behavior == null || behavior.getRunsOn() == null ? List.of() : behavior.getRunsOn();
    }

    /** Raw pre-start-order key, or null when unset. */
    public String preStartOrder(String name) {
        ModifierBehavior behavior = behavior(name);
        return behavior == null || behavior.getOnStart() == null
                ? null : behavior.getOnStart().getPreStartOrder();
    }

    /** Interval seconds; 60 when unset. */
    public double intervalSeconds(String name) {
        ModifierBehavior behavior = behavior(name);
        Double interval = behavior == null || behavior.getIntervalSettings() == null
                ? null : behavior.getIntervalSettings().getInterval();
        return interval == null ? 60.0 : interval;
    }

    /** Interval deviation seconds; 0 when unset. */
    public double intervalDeviation(String name) {
        ModifierBehavior behavior = behavior(name);
        Double deviation = behavior == null || behavior.getIntervalSettings() == null
                ? null : behavior.getIntervalSettings().getDeviation();
        return deviation == null ? 0.0 : deviation;
    }

    /** Raw interval behavior key, or null when unset. */
    public String intervalBehavior(String name) {
        ModifierBehavior behavior = behavior(name);
        return behavior == null || behavior.getIntervalSettings() == null
                ? null : behavior.getIntervalSettings().getBehavior();
    }

    /** Success chance fraction; 1 when unset. */
    public double chance(String name) {
        ModifierBehavior behavior = behavior(name);
        Double chance = behavior == null || behavior.getSuccessChance() == null
                ? null : behavior.getSuccessChance().getChance();
        return chance == null ? 1.0 : chance;
    }

    /** Raw success-chance behavior key, or null when unset. */
    public String chanceBehavior(String name) {
        ModifierBehavior behavior = behavior(name);
        return behavior == null || behavior.getSuccessChance() == null
                ? null : behavior.getSuccessChance().getBehavior();
    }

    /** Raw pick-random behavior key, or null when unset. */
    public String pickBehavior(String name) {
        ModifierExecution execution = execution(name);
        return execution == null || execution.getPickRandom() == null
                ? null : execution.getPickRandom().getBehavior();
    }

    /** Ticks to wait after triggering; 0 when unset. */
    public long delayTicks(String name) {
        ModifierBehavior behavior = behavior(name);
        Long delay = behavior == null ? null : behavior.getDelay();
        return delay == null ? 0L : delay;
    }

    /** One command list; empty when the modifier or list is unknown. */
    public List<String> commandList(String name, String listKey) {
        ModifierCommands commands = commands(name);
        if (commands == null) {
            return List.of();
        }
        List<String> lines = commands.getLists().get(listKey);
        return lines == null ? List.of() : lines;
    }

    /** Raw execution selection key, or null when unset. */
    public String selection(String name) {
        ModifierExecution execution = execution(name);
        return execution == null ? null : execution.getSelection();
    }

    /** Pick-random line count; 1 when unset. */
    public int pickCount(String name) {
        ModifierExecution execution = execution(name);
        Integer count = execution == null || execution.getPickRandom() == null
                ? null : execution.getPickRandom().getCount();
        return count == null ? 1 : count;
    }

    public String metaName(String name) {
        ModifierMeta meta = meta(name);
        return orDefault(meta == null ? null : meta.getName(), DEFAULT_NAME);
    }

    public String metaDescription(String name) {
        ModifierMeta meta = meta(name);
        return orDefault(meta == null ? null : meta.getDescription(), DEFAULT_DESCRIPTION);
    }

    /** Author line, or null when the modifier defines none. */
    public String metaAuthor(String name) {
        ModifierMeta meta = meta(name);
        String author = meta == null ? null : meta.getAuthor();
        return author == null || author.isBlank() ? null : author;
    }

    /**
     * Menu icon. Missing entries silently become stone; unparseable or
     * air entries warn once per load and become stone too.
     */
    public Material metaItem(String name) {
        ModifierMeta meta = meta(name);
        return resolveItem(meta == null ? null : meta.getItem(), "Modifier '" + name + "'");
    }

    public Set<String> presetNames() {
        return new LinkedHashSet<>(config.getPresets().keySet());
    }

    /** Preset member ids; empty when the preset is unknown. */
    public List<String> presetMembers(String id) {
        ModifierPreset preset = config.getPresets().get(id);
        return preset == null || preset.getModifiers() == null ? List.of() : preset.getModifiers();
    }

    /**
     * True when every member of the preset is enabled. Unknown or
     * memberless presets read as off.
     */
    public boolean presetEnabled(String id) {
        List<String> members = presetMembers(id);
        if (members.isEmpty() || !presetNames().contains(id)) {
            return false;
        }
        for (String member : members) {
            if (!isEnabled(member)) {
                return false;
            }
        }
        return true;
    }

    public String presetName(String id) {
        ModifierPreset preset = config.getPresets().get(id);
        return orDefault(preset == null ? null : preset.getName(), DEFAULT_NAME);
    }

    public String presetDescription(String id) {
        ModifierPreset preset = config.getPresets().get(id);
        return orDefault(preset == null ? null : preset.getDescription(), DEFAULT_DESCRIPTION);
    }

    /** Preset menu icon, with the same fallbacks as modifier icons. */
    public Material presetItem(String id) {
        ModifierPreset preset = config.getPresets().get(id);
        return resolveItem(preset == null ? null : preset.getItem(), "Preset '" + id + "'");
    }

    private ModifierBehavior behavior(String name) {
        ModifierEntry entry = config.getModifiers().get(name);
        return entry == null ? null : entry.getBehavior();
    }

    private ModifierMeta meta(String name) {
        ModifierEntry entry = config.getModifiers().get(name);
        return entry == null ? null : entry.getMeta();
    }

    private ModifierCommands commands(String name) {
        ModifierBehavior behavior = behavior(name);
        return behavior == null ? null : behavior.getCommands();
    }

    private ModifierExecution execution(String name) {
        ModifierCommands commands = commands(name);
        return commands == null ? null : commands.getExecution();
    }

    private Material resolveItem(String raw, String label) {
        if (raw == null) {
            return Material.STONE;
        }
        Material material = parseMaterial(raw);
        if (material == null || material == Material.AIR) {
            if (warnedItems.add(label)) {
                log.warning(label + " has invalid item '" + raw + "'; using stone.");
            }
            return Material.STONE;
        }
        return material;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Lenient material parse: trims, strips minecraft: prefix, ignores case. */
    static Material parseMaterial(String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        try {
            return Material.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void save() {
        try {
            config.save();
        } catch (RuntimeException exception) {
            log.warning("Could not save modifiers.yml: " + exception.getMessage());
        }
    }
}
