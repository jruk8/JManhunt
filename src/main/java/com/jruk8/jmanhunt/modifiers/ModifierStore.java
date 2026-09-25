package com.jruk8.jmanhunt.modifiers;

import com.jruk8.jmanhunt.modifiers.config.ModifierBehavior;
import com.jruk8.jmanhunt.modifiers.config.ModifierCommands;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierExecution;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.config.ModifierOptions;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import java.util.ArrayList;
import java.util.function.Consumer;
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
    public static final String DEFAULT_PRESET_NAME = "My Preset";

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

    /** Raw modifier entry, or null when unknown. */
    public ModifierEntry modifierEntry(String name) {
        return config.getModifiers().get(name);
    }

    /** Raw preset, or null when unknown. */
    public ModifierPreset presetEntry(String id) {
        return config.getPresets().get(id);
    }

    /**
     * Creates a disabled modifier with placeholder display data, saving
     * immediately. The name gets " {n}" numbering when taken. Returns
     * the final id.
     */
    public String createModifier(String displayName, String author) {
        ModifierEntry entry = new ModifierEntry();
        ModifierMeta meta = new ModifierMeta();
        meta.setName(displayName);
        meta.setDescription(DEFAULT_DESCRIPTION);
        meta.setItem(Material.STONE.name());
        meta.setAuthor(author);
        entry.setMeta(meta);
        String slug = ModifierNames.kebab(displayName);
        return addModifier(slug.isEmpty() ? "modifier" : slug, entry);
    }

    /**
     * Creates a preset with placeholder display data, saving
     * immediately. The name gets " {n}" numbering when taken. Returns
     * the final id.
     */
    public String createPreset(String displayName) {
        ModifierPreset preset = new ModifierPreset();
        ModifierMeta meta = new ModifierMeta();
        meta.setName(displayName);
        meta.setDescription(DEFAULT_DESCRIPTION);
        meta.setItem(Material.STONE.name());
        preset.setMeta(meta);
        preset.setModifiers(new ArrayList<>());
        String slug = ModifierNames.kebab(displayName);
        return addPreset(slug.isEmpty() ? "preset" : slug, preset);
    }

    /** Removes a modifier, saving immediately. False when unknown. */
    public boolean removeModifier(String id) {
        if (config.getModifiers().remove(id) == null) {
            return false;
        }
        save();
        return true;
    }

    /** Removes a preset, saving immediately. False when unknown. */
    public boolean removePreset(String id) {
        if (config.getPresets().remove(id) == null) {
            return false;
        }
        save();
        return true;
    }

    /**
     * Renames a modifier id, saving immediately. Preset member lists
     * follow the rename so presets keep pointing at the same entry.
     * False when the old id is unknown or the new id is taken.
     */
    public boolean renameModifier(String oldId, String newId) {
        if (oldId.equals(newId)) {
            return config.getModifiers().containsKey(oldId);
        }
        ModifierEntry entry = config.getModifiers().get(oldId);
        if (entry == null || config.getModifiers().containsKey(newId)) {
            return false;
        }
        config.getModifiers().remove(oldId);
        config.getModifiers().put(newId, entry);
        for (ModifierPreset preset : config.getPresets().values()) {
            List<String> members = preset.getModifiers();
            if (members == null) {
                continue;
            }
            for (int index = 0; index < members.size(); index++) {
                if (members.get(index).equals(oldId)) {
                    members.set(index, newId);
                }
            }
        }
        save();
        return true;
    }

    /**
     * Renames a preset id, saving immediately. False when the old id
     * is unknown or the new id is taken.
     */
    public boolean renamePreset(String oldId, String newId) {
        if (oldId.equals(newId)) {
            return config.getPresets().containsKey(oldId);
        }
        ModifierPreset preset = config.getPresets().get(oldId);
        if (preset == null || config.getPresets().containsKey(newId)) {
            return false;
        }
        config.getPresets().remove(oldId);
        config.getPresets().put(newId, preset);
        save();
        return true;
    }

    /**
     * Patches a modifier, saving immediately. False when unknown.
     * Runs-on, options, and commands blocks are created by the
     * ensure helpers when the patch needs them.
     */
    public boolean updateModifier(String id, Consumer<ModifierEntry> patch) {
        ModifierEntry entry = config.getModifiers().get(id);
        if (entry == null) {
            return false;
        }
        patch.accept(entry);
        save();
        return true;
    }

    /**
     * Patches a preset, saving immediately. False when unknown. The
     * meta block is created when missing so display patches never
     * meet a null parent.
     */
    public boolean updatePreset(String id, Consumer<ModifierPreset> patch) {
        ModifierPreset preset = config.getPresets().get(id);
        if (preset == null) {
            return false;
        }
        if (preset.getMeta() == null) {
            preset.setMeta(new ModifierMeta());
        }
        patch.accept(preset);
        save();
        return true;
    }

    /**
     * Adds a modifier to a preset, saving immediately. True when the
     * preset changed; false when the preset is unknown or the member
     * was already listed.
     */
    public boolean memberAdd(String presetId, String modifierId) {
        ModifierPreset preset = config.getPresets().get(presetId);
        if (preset == null) {
            return false;
        }
        if (preset.getModifiers() == null) {
            preset.setModifiers(new ArrayList<>());
        }
        if (preset.getModifiers().contains(modifierId)) {
            return false;
        }
        preset.getModifiers().add(modifierId);
        save();
        return true;
    }

    /**
     * Removes a modifier from a preset, saving immediately. True when
     * the preset changed; false when the preset is unknown or the
     * member was not listed.
     */
    public boolean memberRemove(String presetId, String modifierId) {
        ModifierPreset preset = config.getPresets().get(presetId);
        if (preset == null || preset.getModifiers() == null) {
            return false;
        }
        if (!preset.getModifiers().remove(modifierId)) {
            return false;
        }
        save();
        return true;
    }

    /** Behavior block, creating it when missing. */
    public static ModifierBehavior ensureBehavior(ModifierEntry entry) {
        if (entry.getBehavior() == null) {
            entry.setBehavior(new ModifierBehavior());
        }
        return entry.getBehavior();
    }

    /** Meta block, creating it when missing. */
    public static ModifierMeta ensureMeta(ModifierEntry entry) {
        if (entry.getMeta() == null) {
            entry.setMeta(new ModifierMeta());
        }
        return entry.getMeta();
    }

    /** Options block, creating it when missing. */
    public static ModifierOptions ensureOptions(ModifierBehavior behavior) {
        if (behavior.getOptions() == null) {
            behavior.setOptions(new ModifierOptions());
        }
        return behavior.getOptions();
    }

    /** Commands block, creating it when missing. */
    public static ModifierCommands ensureCommands(ModifierBehavior behavior) {
        if (behavior.getCommands() == null) {
            behavior.setCommands(new ModifierCommands());
        }
        return behavior.getCommands();
    }

    /** Execution block, creating it when missing. */
    public static ModifierExecution ensureExecution(ModifierOptions options) {
        if (options.getExecution() == null) {
            options.setExecution(new ModifierExecution());
        }
        return options.getExecution();
    }

    /**
     * Adds a modifier, saving immediately. A taken id bumps the
     * display name with " {n}" numbering and derives the id from it.
     * Returns the final id.
     */
    public String addModifier(String id, ModifierEntry entry) {
        String finalId = id;
        if (config.getModifiers().containsKey(finalId)) {
            Set<String> takenNames = new HashSet<>();
            for (String key : config.getModifiers().keySet()) {
                takenNames.add(metaName(key));
            }
            String base = metaNameOf(entry);
            String name = base;
            String slug;
            do {
                name = ModifierNames.uniqueName(name, takenNames);
                takenNames.add(name);
                slug = ModifierNames.kebab(name);
                if (slug.isEmpty()) {
                    slug = "modifier";
                }
            } while (config.getModifiers().containsKey(slug));
            if (entry.getMeta() == null) {
                entry.setMeta(new ModifierMeta());
            }
            entry.getMeta().setName(name);
            finalId = slug;
        }
        config.getModifiers().put(finalId, entry);
        save();
        return finalId;
    }

    /**
     * Adds a preset, saving immediately. A taken id bumps the display
     * name with " {n}" numbering and derives the id from it. Returns
     * the final id.
     */
    public String addPreset(String id, ModifierPreset preset) {
        String finalId = id;
        if (config.getPresets().containsKey(finalId)) {
            Set<String> takenNames = new HashSet<>();
            for (String key : config.getPresets().keySet()) {
                takenNames.add(presetName(key));
            }
            String base = presetNameOf(preset);
            String name = base;
            String slug;
            do {
                name = ModifierNames.uniqueName(name, takenNames);
                takenNames.add(name);
                slug = ModifierNames.kebab(name);
                if (slug.isEmpty()) {
                    slug = "preset";
                }
            } while (config.getPresets().containsKey(slug));
            if (preset.getMeta() == null) {
                preset.setMeta(new ModifierMeta());
            }
            preset.getMeta().setName(name);
            finalId = slug;
        }
        config.getPresets().put(finalId, preset);
        save();
        return finalId;
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
        ModifierOptions options = options(name);
        Double interval = options == null || options.getIntervalSettings() == null
                ? null : options.getIntervalSettings().getInterval();
        return interval == null ? 60.0 : interval;
    }

    /** Interval deviation seconds; 0 when unset. */
    public double intervalDeviation(String name) {
        ModifierOptions options = options(name);
        Double deviation = options == null || options.getIntervalSettings() == null
                ? null : options.getIntervalSettings().getDeviation();
        return deviation == null ? 0.0 : deviation;
    }

    /** Raw interval behavior key, or null when unset. */
    public String intervalBehavior(String name) {
        ModifierOptions options = options(name);
        return options == null || options.getIntervalSettings() == null
                ? null : options.getIntervalSettings().getBehavior();
    }

    /** Success chance fraction; 1 when unset. */
    public double chance(String name) {
        ModifierOptions options = options(name);
        Double chance = options == null || options.getSuccessChance() == null
                ? null : options.getSuccessChance().getChance();
        return chance == null ? 1.0 : chance;
    }

    /** Raw success-chance behavior key, or null when unset. */
    public String chanceBehavior(String name) {
        ModifierOptions options = options(name);
        return options == null || options.getSuccessChance() == null
                ? null : options.getSuccessChance().getBehavior();
    }

    /** Raw pick-random behavior key, or null when unset. */
    public String pickBehavior(String name) {
        ModifierExecution execution = execution(name);
        return execution == null || execution.getPickRandom() == null
                ? null : execution.getPickRandom().getBehavior();
    }

    /** Ticks to wait after triggering; 0 when unset. */
    public long delayTicks(String name) {
        ModifierOptions options = options(name);
        Long delay = options == null ? null : options.getDelay();
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
        ModifierMeta meta = presetMeta(id);
        return orDefault(meta == null ? null : meta.getName(), DEFAULT_NAME);
    }

    public String presetDescription(String id) {
        ModifierMeta meta = presetMeta(id);
        return orDefault(meta == null ? null : meta.getDescription(), DEFAULT_DESCRIPTION);
    }

    /** Preset menu icon, with the same fallbacks as modifier icons. */
    public Material presetItem(String id) {
        ModifierMeta meta = presetMeta(id);
        return resolveItem(meta == null ? null : meta.getItem(), "Preset '" + id + "'");
    }

    /** Preset author line, or null when the preset defines none. */
    public String presetAuthor(String id) {
        ModifierMeta meta = presetMeta(id);
        String author = meta == null ? null : meta.getAuthor();
        return author == null || author.isBlank() ? null : author;
    }

    private ModifierMeta presetMeta(String id) {
        ModifierPreset preset = config.getPresets().get(id);
        return preset == null ? null : preset.getMeta();
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

    private ModifierOptions options(String name) {
        ModifierBehavior behavior = behavior(name);
        return behavior == null ? null : behavior.getOptions();
    }

    private ModifierExecution execution(String name) {
        ModifierOptions options = options(name);
        return options == null ? null : options.getExecution();
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

    private static String metaNameOf(ModifierEntry entry) {
        return entry.getMeta() == null
                ? DEFAULT_NAME : orDefault(entry.getMeta().getName(), DEFAULT_NAME);
    }

    private static String presetNameOf(ModifierPreset preset) {
        return preset.getMeta() == null
                ? DEFAULT_PRESET_NAME : orDefault(preset.getMeta().getName(), DEFAULT_PRESET_NAME);
    }

    /** Lenient material parse: trims, strips minecraft: prefix, ignores case. */
    public static Material parseMaterial(String raw) {
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
