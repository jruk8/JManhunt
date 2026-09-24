package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.modifiers.ModifierStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Central point for reading and writing plugin config settings. Reads
 * resolve dotted paths against the typed Okaeri root; writes validate
 * through the setting registry, so the chat command and the GUI share
 * one typed path with identical bounds and option checks.
 */
public final class ConfigService {
    private static final JManhuntConfig DEFAULTS = new JManhuntConfig();

    private final JManhuntConfig root;
    private final ModifierStore modifiers;
    private final Runnable saver;
    private final Map<String, List<BiConsumer<Boolean, Boolean>>> listeners = new HashMap<>();

    /**
     * @param root live Okaeri store, held by reference across reloads
     *        (null only in modifier-only unit tests, where setting
     *        reads fall back to their defaults)
     */
    public ConfigService(JManhuntConfig root, ModifierStore modifiers) {
        this(root, modifiers, root == null ? () -> {} : root::save);
    }

    /**
     * Test seam: bare schema instances have no Okaeri binder, so unit
     * tests pass a no-op saver while production saves through the root.
     */
    ConfigService(JManhuntConfig root, ModifierStore modifiers, Runnable saver) {
        this.root = root;
        this.modifiers = modifiers;
        this.saver = saver;
    }

    /** Registers a callback fired whenever the given setting is changed via a boolean write. */
    public void onChange(String setting, BiConsumer<Boolean, Boolean> listener) {
        listeners.computeIfAbsent(setting, k -> new ArrayList<>()).add(listener);
    }

    public Set<String> settingNames() {
        return SettingRegistry.settingNames();
    }

    /** Registry descriptor for the path, or null when it is not an editable setting. */
    public SettingDescriptor describe(String setting) {
        return SettingRegistry.byPath(setting);
    }

    /** Lobby preset keys from the world-engine map, for completion and error text. */
    public Set<String> lobbyPresetKeys() {
        if (root == null || root.getWorldEngine() == null
                || root.getWorldEngine().getLobbyPresets() == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(root.getWorldEngine().getLobbyPresets().keySet());
    }

    /** True when the path names an editable string list. */
    public boolean isList(String setting) {
        return SettingRegistry.isListPath(setting);
    }

    /** True when the path addresses one list entry (list path plus an integer tail). */
    public boolean isIndexPath(String setting) {
        if (setting == null) {
            return false;
        }
        int dot = setting.lastIndexOf('.');
        if (dot == -1 || !isList(setting.substring(0, dot))) {
            return false;
        }
        try {
            Integer.parseInt(setting.substring(dot + 1).trim());
            return true;
        } catch (NumberFormatException expected) {
            return false;
        }
    }

    public boolean getBoolean(String setting, boolean defaultValue) {
        Object value = rawValue(setting);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return defaultValue;
    }

    public String getString(String setting, String defaultValue) {
        Object value = rawValue(setting);
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Enum<?> option) {
            return option.name();
        }
        return defaultValue;
    }

    public int getInt(String setting, int defaultValue) {
        Object value = rawValue(setting);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }

    public double getDouble(String setting, double defaultValue) {
        Object value = rawValue(setting);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }

    public float getFloat(String setting, float defaultValue) {
        return (float) getDouble(setting, defaultValue);
    }

    public List<String> getStringList(String setting) {
        Object value = rawValue(setting);
        if (value instanceof List<?> list) {
            List<String> strings = new ArrayList<>(list.size());
            for (Object entry : list) {
                strings.add(entry == null ? "null" : String.valueOf(entry));
            }
            return strings;
        }
        return List.of();
    }

    /** Returns the config value for the given path, with enums as their names, or null if absent. */
    public Object getValue(String setting) {
        Object value = rawValue(setting);
        if (value instanceof Enum<?> option) {
            return option.name();
        }
        return value;
    }

    /** Reads an enum-typed path leniently, falling back when missing or unknown. */
    public <T extends Enum<T>> T getEnum(String setting, Class<T> type, T fallback) {
        Object value = rawValue(setting);
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        if (value instanceof String raw) {
            for (T constant : type.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(raw.trim())) {
                    return constant;
                }
            }
        }
        return fallback;
    }

    /** Schema default for the path, display-normalized, or null when unknown. */
    public Object defaultValue(String setting) {
        Object value = ConfigPathMapper.get(DEFAULTS, setting);
        if (value instanceof Enum<?> option) {
            return option.name();
        }
        return value;
    }

    /** True when the live value differs from the schema default. */
    public boolean isModified(String setting) {
        return !Objects.equals(displayValue(getValue(setting)), displayValue(defaultValue(setting)));
    }

    /**
     * Display form of a config value: enum names as-is, floating point
     * trimmed to at most three decimals, everything else verbatim.
     */
    public static String displayValue(Object value) {
        if (value instanceof Enum<?> option) {
            return option.name();
        }
        if (value instanceof Double number) {
            return trimDouble(number);
        }
        if (value instanceof Float number) {
            return trimDouble(number.doubleValue());
        }
        return String.valueOf(value);
    }

    private static String trimDouble(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        String text = String.format(Locale.ROOT, "%.3f", value);
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    /**
     * Validates and applies a raw string value. Returns the outcome with
     * the canonical old and new values, or a message key plus slots that
     * render the failure.
     */
    public SetOutcome setValue(String setting, String raw) {
        SettingDescriptor descriptor = SettingRegistry.byPath(setting);
        if (descriptor == null) {
            if (isIndexPath(setting)) {
                int dot = setting.lastIndexOf('.');
                return listSet(setting.substring(0, dot),
                        Integer.parseInt(setting.substring(dot + 1).trim()), raw);
            }
            return SetOutcome.fail("manhunt.setting-invalid", Map.of());
        }
        SettingRegistry.ValidationOutcome validation =
                SettingRegistry.validate(descriptor, raw, this::getValue);
        if (!validation.ok()) {
            Map<String, String> slots = new HashMap<>(validation.slots());
            slots.put("setting", descriptor.path());
            return SetOutcome.fail(validation.errorKey(), slots);
        }
        return applyValue(descriptor, validation.value());
    }

    /** Replaces one list entry. Index errors fail with the list size attached. */
    public SetOutcome listSet(String listPath, int index, String raw) {
        List<Object> live = liveList(listPath);
        if (live == null) {
            return SetOutcome.fail("manhunt.setting-invalid", Map.of());
        }
        if (index < 0 || index >= live.size()) {
            return SetOutcome.fail("manhunt.setting-index-invalid",
                    Map.of("setting", listPath, "index", String.valueOf(index),
                            "size", String.valueOf(live.size())));
        }
        Object oldValue = live.get(index);
        live.set(index, raw == null ? "" : raw.trim());
        saver.run();
        return SetOutcome.ok(null, oldValue, live.get(index));
    }

    /** Appends one list entry. */
    public SetOutcome listAdd(String listPath, String raw) {
        List<Object> live = liveList(listPath);
        if (live == null) {
            return SetOutcome.fail("manhunt.setting-invalid", Map.of());
        }
        String value = raw == null ? "" : raw.trim();
        live.add(value);
        saver.run();
        return SetOutcome.ok(null, "-", value);
    }

    /** Removes one list entry. Index errors fail with the list size attached. */
    public SetOutcome listRemove(String listPath, int index) {
        List<Object> live = liveList(listPath);
        if (live == null) {
            return SetOutcome.fail("manhunt.setting-invalid", Map.of());
        }
        if (index < 0 || index >= live.size()) {
            return SetOutcome.fail("manhunt.setting-index-invalid",
                    Map.of("setting", listPath, "index", String.valueOf(index),
                            "size", String.valueOf(live.size())));
        }
        Object oldValue = live.remove(index);
        saver.run();
        return SetOutcome.ok(null, oldValue, "-");
    }

    @SuppressWarnings("unchecked")
    private List<Object> liveList(String listPath) {
        if (root == null) {
            return null;
        }
        String canonical = SettingRegistry.canonicalListPath(listPath);
        Object node = ConfigPathMapper.get(root, canonical == null ? listPath : canonical);
        if (!(node instanceof List<?> list)) {
            return null;
        }
        return (List<Object>) list;
    }

    /** Applies a pre-validated boolean and fires change listeners. */
    public boolean setBoolean(String setting, boolean value) {
        SettingDescriptor descriptor = SettingRegistry.byPath(setting);
        if (descriptor == null || descriptor.type() != SettingType.BOOL) {
            return false;
        }
        return applyValue(descriptor, value).ok();
    }

    private SetOutcome applyValue(SettingDescriptor descriptor, Object value) {
        if (root == null) {
            return SetOutcome.fail("manhunt.setting-invalid", Map.of());
        }
        Object oldValue = getValue(descriptor.path());
        if (!ConfigPathMapper.set(root, descriptor.path(), value)) {
            return SetOutcome.fail("manhunt.setting-invalid", Map.of());
        }
        saver.run();
        if (value instanceof Boolean bool) {
            boolean oldBool = oldValue instanceof Boolean old ? old : !bool;
            fireChange(descriptor.path(), oldBool, bool);
        }
        return SetOutcome.ok(descriptor, oldValue, getValue(descriptor.path()));
    }

    private Object rawValue(String setting) {
        if (root == null) {
            return null;
        }
        return ConfigPathMapper.get(root, setting);
    }

    /** Typed write result: canonical values on success, message slots on failure. */
    public record SetOutcome(boolean ok, SettingDescriptor descriptor, Object oldValue,
            Object newValue, String errorKey, Map<String, String> slots) {
        static SetOutcome ok(SettingDescriptor descriptor, Object oldValue, Object newValue) {
            return new SetOutcome(true, descriptor, oldValue, newValue, null, Map.of());
        }

        static SetOutcome fail(String errorKey, Map<String, String> slots) {
            return new SetOutcome(false, null, null, null, errorKey, slots);
        }
    }

    public Set<String> modifierNames() {
        return modifiers.modifierNames();
    }

    public boolean modifierEnabled(String name) {
        return modifiers.isEnabled(name);
    }

    public boolean setModifierEnabled(String name, boolean value) {
        return modifiers.setEnabled(name, value);
    }

    public Set<String> presetNames() {
        return modifiers.presetNames();
    }

    public List<String> presetMembers(String id) {
        return modifiers.presetMembers(id);
    }

    /**
     * True when every member of the preset is enabled. Unknown or
     * memberless presets read as off.
     */
    public boolean presetEnabled(String id) {
        return modifiers.presetEnabled(id);
    }

    /** The wrapped modifier store, for menu text and icon reads. */
    public ModifierStore modifiers() {
        return modifiers;
    }

    /**
     * Flips every member of the preset at once. Returns false when the
     * preset is unknown; unknown member ids are skipped.
     */
    public boolean setPreset(String id, boolean value) {
        if (!modifiers.presetNames().contains(id)) {
            return false;
        }
        for (String member : modifiers.presetMembers(id)) {
            modifiers.setEnabled(member, value);
        }
        return true;
    }

    public List<String> runsOn(String name) {
        return modifiers.runsOn(name);
    }

    public String preStartOrder(String name) {
        return modifiers.preStartOrder(name);
    }

    public double intervalSeconds(String name) {
        return modifiers.intervalSeconds(name);
    }

    public double intervalDeviation(String name) {
        return modifiers.intervalDeviation(name);
    }

    public String intervalBehavior(String name) {
        return modifiers.intervalBehavior(name);
    }

    public double chance(String name) {
        return modifiers.chance(name);
    }

    public String chanceBehavior(String name) {
        return modifiers.chanceBehavior(name);
    }

    public String pickBehavior(String name) {
        return modifiers.pickBehavior(name);
    }

    public long delayTicks(String name) {
        return modifiers.delayTicks(name);
    }

    public List<String> commandList(String name, String listKey) {
        return modifiers.commandList(name, listKey);
    }

    public String selection(String name) {
        return modifiers.selection(name);
    }

    public int pickCount(String name) {
        return modifiers.pickCount(name);
    }

    private void fireChange(String setting, boolean oldValue, boolean newValue) {
        List<BiConsumer<Boolean, Boolean>> list = listeners.get(setting);
        if (list == null) {
            return;
        }
        for (BiConsumer<Boolean, Boolean> listener : list) {
            listener.accept(oldValue, newValue);
        }
    }
}
