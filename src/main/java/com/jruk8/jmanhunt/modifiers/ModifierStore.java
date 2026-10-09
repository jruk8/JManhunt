package com.jruk8.jmanhunt.modifiers;

import com.jruk8.jmanhunt.modifiers.config.ModifierBehavior;
import com.jruk8.jmanhunt.modifiers.config.ModifierCommands;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierExecution;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.config.ModifierOptions;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult;
import com.jruk8.jmanhunt.modifiers.files.ModifierFiles;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.bukkit.Material;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Owns the mods/ trees behind the per-file models: modifier toggles,
 * typed behavior lookups, presets, and display metadata. Every
 * mutation persists only its touched file, atomically. Item names
 * resolve without a running server so unit tests can exercise every
 * fallback.
 */
public final class ModifierStore {
    public static final String DEFAULT_NAME = "My Modifier";
    public static final String DEFAULT_DESCRIPTION = "Enable for a twist!";
    public static final String DEFAULT_PRESET_NAME = "My Preset";

    private final ModifierFiles files;
    private final Logger log;
    private final Set<String> warnedItems = new HashSet<>();
    private final Set<String> warnedBehaviorKeys = new HashSet<>();
    private final List<Consumer<String>> toggleListeners = new ArrayList<>();

    public ModifierStore(ModifierFiles files, Logger log) {
        this.files = files;
        this.log = log;
    }

    /** Clears one-per-load warnings, e.g. after /mh reload. */
    public void clearItemWarnings() {
        warnedItems.clear();
        warnedBehaviorKeys.clear();
    }

    /** Swaps contents from a fresh load pass (reload). */
    public void replaceAll(ModLoadResult result) {
        files.replaceAll(result);
    }

    /** Raw modifier entry, or null when unknown. */
    public ModifierEntry modifierEntry(String name) {
        return files.getModifiers().get(name);
    }

    /** Raw preset, or null when unknown. */
    public ModifierPreset presetEntry(String id) {
        return files.getPresets().get(id);
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
    public String createPreset(String displayName, String author) {
        ModifierPreset preset = new ModifierPreset();
        ModifierMeta meta = new ModifierMeta();
        meta.setName(displayName);
        meta.setDescription(DEFAULT_DESCRIPTION);
        meta.setItem(Material.STONE.name());
        meta.setAuthor(author);
        preset.setMeta(meta);
        preset.setModifiers(new ArrayList<>());
        String slug = ModifierNames.kebab(displayName);
        return addPreset(slug.isEmpty() ? "preset" : slug, preset);
    }

    /** Removes a modifier and deletes its file. False when unknown. */
    public boolean removeModifier(String id) {
        if (files.getModifiers().remove(id) == null) {
            return false;
        }
        files.deleteModifierFile(id, log);
        return true;
    }

    /** Removes a preset and deletes its file. False when unknown. */
    public boolean removePreset(String id) {
        if (files.getPresets().remove(id) == null) {
            return false;
        }
        files.deletePresetFile(id, log);
        return true;
    }

    /**
     * Renames a modifier id, moving its file within its directory.
     * Preset member lists follow the rename so presets keep pointing
     * at the same entry. False when the old id is unknown or the new
     * id is taken.
     */
    public boolean renameModifier(String oldId, String newId) {
        if (oldId.equals(newId)) {
            return files.getModifiers().containsKey(oldId);
        }
        ModifierEntry entry = files.getModifiers().get(oldId);
        if (entry == null || files.getModifiers().containsKey(newId)) {
            return false;
        }
        files.getModifiers().remove(oldId);
        files.getModifiers().put(newId, entry);
        files.moveModifierFile(oldId, newId, log);
        for (Map.Entry<String, ModifierPreset> preset : files.getPresets().entrySet()) {
            List<String> members = preset.getValue().getModifiers();
            if (members == null) {
                continue;
            }
            boolean touched = false;
            for (int index = 0; index < members.size(); index++) {
                if (members.get(index).equals(oldId)) {
                    members.set(index, newId);
                    touched = true;
                }
            }
            if (touched) {
                files.savePreset(preset.getKey(), log);
            }
        }
        return true;
    }

    /**
     * Renames a preset id, moving its file within its directory.
     * False when the old id is unknown or the new id is taken.
     */
    public boolean renamePreset(String oldId, String newId) {
        if (oldId.equals(newId)) {
            return files.getPresets().containsKey(oldId);
        }
        ModifierPreset preset = files.getPresets().get(oldId);
        if (preset == null || files.getPresets().containsKey(newId)) {
            return false;
        }
        files.getPresets().remove(oldId);
        files.getPresets().put(newId, preset);
        files.movePresetFile(oldId, newId, log);
        return true;
    }

    /**
     * Patches a modifier, saving immediately. False when unknown.
     * Runs-on, options, and commands blocks are created by the
     * ensure helpers when the patch needs them.
     */
    public boolean updateModifier(String id, Consumer<ModifierEntry> patch) {
        ModifierEntry entry = files.getModifiers().get(id);
        if (entry == null) {
            return false;
        }
        patch.accept(entry);
        files.saveModifier(id, log);
        return true;
    }

    /**
     * Patches a preset, saving immediately. False when unknown. The
     * meta block is created when missing so display patches never
     * meet a null parent.
     */
    public boolean updatePreset(String id, Consumer<ModifierPreset> patch) {
        ModifierPreset preset = files.getPresets().get(id);
        if (preset == null) {
            return false;
        }
        if (preset.getMeta() == null) {
            preset.setMeta(new ModifierMeta());
        }
        patch.accept(preset);
        files.savePreset(id, log);
        return true;
    }

    /**
     * Adds a modifier to a preset, saving immediately. True when the
     * preset changed; false when the preset is unknown or the member
     * was already listed.
     */
    public boolean memberAdd(String presetId, String modifierId) {
        ModifierPreset preset = files.getPresets().get(presetId);
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
        files.savePreset(presetId, log);
        return true;
    }

    /**
     * Removes a modifier from a preset, saving immediately. True when
     * the preset changed; false when the preset is unknown or the
     * member was not listed.
     */
    public boolean memberRemove(String presetId, String modifierId) {
        ModifierPreset preset = files.getPresets().get(presetId);
        if (preset == null || preset.getModifiers() == null) {
            return false;
        }
        if (!preset.getModifiers().remove(modifierId)) {
            return false;
        }
        files.savePreset(presetId, log);
        return true;
    }

    /** Behavior block at one index, creating map and block when missing. */
    public static ModifierBehavior ensureBehavior(ModifierEntry entry, int index) {
        if (entry.getBehavior() == null) {
            entry.setBehavior(new LinkedHashMap<>());
        }
        return entry.getBehavior().computeIfAbsent(String.valueOf(index),
                key -> new ModifierBehavior());
    }

    /**
     * Present behavior indexes, sorted ascending. Non-numeric keys warn
     * once per load and are skipped; sparse indexes need no contiguity.
     */
    public List<Integer> behaviorIndexes(String name) {
        ModifierEntry entry = files.getModifiers().get(name);
        if (entry == null || entry.getBehavior() == null) {
            return List.of();
        }
        List<Integer> indexes = new ArrayList<>();
        for (String key : entry.getBehavior().keySet()) {
            try {
                indexes.add(Integer.parseInt(key.trim()));
            } catch (NumberFormatException invalid) {
                if (warnedBehaviorKeys.add(name + ":" + key)) {
                    log.warning("Modifier '" + name + "' has non-numeric behavior key '"
                            + key + "'; skipping.");
                }
            }
        }
        indexes.sort(Integer::compareTo);
        return indexes;
    }

    /**
     * Appends an empty behavior, saving immediately. The index is one
     * past the highest present index, or 0 when none exist. Returns the
     * new index, or -1 when the modifier is unknown.
     */
    public int addBehavior(String name) {
        ModifierEntry entry = files.getModifiers().get(name);
        if (entry == null) {
            return -1;
        }
        int index = 0;
        for (int present : behaviorIndexes(name)) {
            index = Math.max(index, present + 1);
        }
        ensureBehavior(entry, index);
        files.saveModifier(name, log);
        return index;
    }

    /** Removes one behavior, saving immediately. False when nothing removed. */
    public boolean removeBehavior(String name, int index) {
        ModifierEntry entry = files.getModifiers().get(name);
        if (entry == null || entry.getBehavior() == null) {
            return false;
        }
        if (entry.getBehavior().remove(String.valueOf(index)) == null) {
            return false;
        }
        files.saveModifier(name, log);
        return true;
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
        if (files.getModifiers().containsKey(finalId)) {
            Set<String> takenNames = new HashSet<>();
            for (String key : files.getModifiers().keySet()) {
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
            } while (files.getModifiers().containsKey(slug));
            if (entry.getMeta() == null) {
                entry.setMeta(new ModifierMeta());
            }
            entry.getMeta().setName(name);
            finalId = slug;
        }
        files.getModifiers().put(finalId, entry);
        files.saveModifier(finalId, log);
        return finalId;
    }

    /**
     * Adds a preset, saving immediately. A taken id bumps the display
     * name with " {n}" numbering and derives the id from it. Returns
     * the final id.
     */
    public String addPreset(String id, ModifierPreset preset) {
        String finalId = id;
        if (files.getPresets().containsKey(finalId)) {
            Set<String> takenNames = new HashSet<>();
            for (String key : files.getPresets().keySet()) {
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
            } while (files.getPresets().containsKey(slug));
            if (preset.getMeta() == null) {
                preset.setMeta(new ModifierMeta());
            }
            preset.getMeta().setName(name);
            finalId = slug;
        }
        files.getPresets().put(finalId, preset);
        files.savePreset(finalId, log);
        return finalId;
    }

    public Set<String> modifierNames() {
        return new LinkedHashSet<>(files.getModifiers().keySet());
    }

    /** True when a modifier id is loaded (root or any subdir). */
    public boolean hasModifier(String id) {
        return files.getModifiers().containsKey(id);
    }

    /** Known source file for one modifier, or null. */
    public Path modifierPath(String id) {
        return files.modifierPath(id);
    }

    public boolean isEnabled(String name) {
        ModifierEntry entry = files.getModifiers().get(name);
        return entry != null && entry.isEnabled();
    }

    /** Subscribes to global modifier toggles; notified only on actual flips. */
    public void addToggleListener(Consumer<String> listener) {
        toggleListeners.add(listener);
    }

    /**
     * Sets enabled and saves. Returns false when the modifier is unknown;
     * save failures only log since the in-memory value already applied.
     * Listeners hear only genuine flips, never redundant re-sets.
     */
    public boolean setEnabled(String name, boolean value) {
        ModifierEntry entry = files.getModifiers().get(name);
        if (entry == null) {
            return false;
        }
        boolean changed = entry.isEnabled() != value;
        entry.setEnabled(value);
        files.saveModifier(name, log);
        if (changed) {
            for (Consumer<String> listener : List.copyOf(toggleListeners)) {
                listener.accept(name);
            }
        }
        return true;
    }

    /** Trigger names, or empty when the modifier omits runs-on. */
    public List<String> runsOn(String name, int index) {
        ModifierBehavior behavior = behavior(name, index);
        return behavior == null || behavior.getRunsOn() == null ? List.of() : behavior.getRunsOn();
    }

    /** Raw pre-start-order key, or null when unset. */
    public String preStartOrder(String name, int index) {
        ModifierBehavior behavior = behavior(name, index);
        return behavior == null || behavior.getOnStart() == null
                ? null : behavior.getOnStart().getPreStartOrder();
    }

    /** Interval seconds; 60 when unset. */
    public double intervalSeconds(String name, int index) {
        ModifierOptions options = options(name, index);
        Double interval = options == null || options.getIntervalSettings() == null
                ? null : options.getIntervalSettings().getInterval();
        return interval == null ? 60.0 : interval;
    }

    /** Interval deviation seconds; 0 when unset. */
    public double intervalDeviation(String name, int index) {
        ModifierOptions options = options(name, index);
        Double deviation = options == null || options.getIntervalSettings() == null
                ? null : options.getIntervalSettings().getDeviation();
        return deviation == null ? 0.0 : deviation;
    }

    /** Raw interval behavior key, or null when unset. */
    public String intervalBehavior(String name, int index) {
        ModifierOptions options = options(name, index);
        return options == null || options.getIntervalSettings() == null
                ? null : options.getIntervalSettings().getBehavior();
    }

    /** Success chance fraction; 1 when unset. */
    public double chance(String name, int index) {
        ModifierOptions options = options(name, index);
        Double chance = options == null || options.getSuccessChance() == null
                ? null : options.getSuccessChance().getChance();
        return chance == null ? 1.0 : chance;
    }

    /** Raw success-chance behavior key, or null when unset. */
    public String chanceBehavior(String name, int index) {
        ModifierOptions options = options(name, index);
        return options == null || options.getSuccessChance() == null
                ? null : options.getSuccessChance().getBehavior();
    }

    /** Raw pick-random behavior key, or null when unset. */
    public String pickBehavior(String name, int index) {
        ModifierExecution execution = execution(name, index);
        return execution == null || execution.getPickRandom() == null
                ? null : execution.getPickRandom().getBehavior();
    }

    /** Ticks to wait after triggering; 0 when unset. */
    public long delayTicks(String name, int index) {
        ModifierOptions options = options(name, index);
        Long delay = options == null ? null : options.getDelay();
        return delay == null ? 0L : delay;
    }

    /** One command list; empty when the modifier or list is unknown. */
    public List<String> commandList(String name, int index, String listKey) {
        ModifierCommands commands = commands(name, index);
        if (commands == null) {
            return List.of();
        }
        List<String> lines = commands.getLists().get(listKey);
        return lines == null ? List.of() : lines;
    }

    /** Raw execution selection key, or null when unset. */
    public String selection(String name, int index) {
        ModifierExecution execution = execution(name, index);
        return execution == null ? null : execution.getSelection();
    }

    /** Pick-random line count; 1 when unset. */
    public int pickCount(String name, int index) {
        ModifierExecution execution = execution(name, index);
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
        return new LinkedHashSet<>(files.getPresets().keySet());
    }

    /** True when a preset id is loaded (root or any subdir). */
    public boolean hasPreset(String id) {
        return files.getPresets().containsKey(id);
    }

    /** Known source file for one preset, or null. */
    public Path presetPath(String id) {
        return files.presetPath(id);
    }

    /** Preset member ids; empty when the preset is unknown. */
    public List<String> presetMembers(String id) {
        ModifierPreset preset = files.getPresets().get(id);
        return preset == null || preset.getModifiers() == null ? List.of() : preset.getModifiers();
    }

    /**
     * True when every present member of the preset is enabled. Unknown
     * presets and presets with no present members read as off; missing
     * member ids never drag the flag down.
     */
    public boolean presetEnabled(String id) {
        if (!presetNames().contains(id)) {
            return false;
        }
        boolean any = false;
        for (String member : presetMembers(id)) {
            if (!hasModifier(member)) {
                continue;
            }
            any = true;
            if (!isEnabled(member)) {
                return false;
            }
        }
        return any;
    }

    /** Display name for a preset member; unknown ids read as quoted missing. */
    public String memberName(String id) {
        return hasModifier(id) ? metaName(id) : "'" + id + "' missing";
    }

    /** Member ids of a preset with no loaded modifier. */
    public List<String> presetMissing(String id) {
        List<String> missing = new ArrayList<>();
        for (String member : presetMembers(id)) {
            if (!hasModifier(member)) {
                missing.add(member);
            }
        }
        return missing;
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
        ModifierPreset preset = files.getPresets().get(id);
        return preset == null ? null : preset.getMeta();
    }

    private ModifierBehavior behavior(String name, int index) {
        ModifierEntry entry = files.getModifiers().get(name);
        if (entry == null || entry.getBehavior() == null) {
            return null;
        }
        return entry.getBehavior().get(String.valueOf(index));
    }

    private ModifierMeta meta(String name) {
        ModifierEntry entry = files.getModifiers().get(name);
        return entry == null ? null : entry.getMeta();
    }

    private ModifierCommands commands(String name, int index) {
        ModifierBehavior behavior = behavior(name, index);
        return behavior == null ? null : behavior.getCommands();
    }

    private ModifierOptions options(String name, int index) {
        ModifierBehavior behavior = behavior(name, index);
        return behavior == null ? null : behavior.getOptions();
    }

    private ModifierExecution execution(String name, int index) {
        ModifierOptions options = options(name, index);
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
}
