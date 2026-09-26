package com.jruk8.jmanhunt.lobby.config;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.config.SettingRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Per-lobby setting and modifier overrides plus effective resolution.
 * Setting overrides live as nested maps mirroring the config shape
 * (full dotted paths, only overridden leaves stored); modifier
 * overrides map ids to forced enabled flags. Readers resolve
 * override-first-then-global; corrupt overrides fall back to the
 * global, never to the default. Writes validate exactly like globals
 * and prune emptied parents, including husk entries.
 */
public final class OverrideService {
    private ConfigService config;
    private final LobbyConfig lobbies;
    private final Runnable saver;

    /**
     * @param config globals plus registry reads for validation
     * @param lobbies live lobby store holding the override maps
     * @param saver persists the lobby store after writes
     */
    public OverrideService(ConfigService config, LobbyConfig lobbies, Runnable saver) {
        this.config = config;
        this.lobbies = lobbies;
        this.saver = saver;
    }

    /** Updates the globals reference after a reload. */
    public void reload(ConfigService config) {
        this.config = config;
    }

    /** Parses a lobby id: a non-negative integer, nothing else. */
    public static OptionalInt parseLobbyId(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            int id = Integer.parseInt(raw.trim());
            return id < 0 ? OptionalInt.empty() : OptionalInt.of(id);
        } catch (NumberFormatException expected) {
            return OptionalInt.empty();
        }
    }

    /** Lobby ids holding at least one override, for completion. */
    public Set<Integer> overrideLobbyIds() {
        Set<Integer> ids = new LinkedHashSet<>();
        for (Map.Entry<String, LobbyConfig.LobbyEntry> entry : lobbies.getLobbies().entrySet()) {
            OptionalInt id = parseLobbyId(entry.getKey());
            if (id.isEmpty()) {
                continue;
            }
            LobbyConfig.OverridesData overrides = entry.getValue().getOverrides();
            if (overrides != null
                    && (!overrides.getSettings().isEmpty() || !overrides.getModifiers().isEmpty())) {
                ids.add(id.getAsInt());
            }
        }
        return ids;
    }

    /**
     * Raw override node at the path, or empty when absent or a branch.
     * Both plain maps and Okaeri-loaded sections navigate.
     */
    public Optional<Object> rawOverride(int lobbyId, String path) {
        LobbyConfig.LobbyEntry entry = lobbies.getLobbies().get(String.valueOf(lobbyId));
        if (entry == null || entry.getOverrides() == null || path == null || path.isBlank()) {
            return Optional.empty();
        }
        Object node = getNode(entry.getOverrides().getSettings(), path.trim().split("\\."));
        if (node == null || node instanceof Map || node instanceof ConfigurationSection) {
            return Optional.empty();
        }
        return Optional.of(node);
    }

    /** True when the lobby overrides the scalar or list at the path. */
    public boolean hasSettingOverride(int lobbyId, String path) {
        return rawOverride(lobbyId, path).isPresent();
    }

    /** True when the lobby overrides the whole string list. */
    public boolean hasListOverride(int lobbyId, String listPath) {
        return rawOverride(lobbyId, listPath).map(List.class::isInstance).orElse(false);
    }

    /** True when any override sits at or beneath the path (category glow). */
    public boolean hasOverridesBeneath(int lobbyId, String path) {
        return countOverrides(lobbyId, path) > 0;
    }

    /** Leaf override count at or beneath a path (stored lists count as one). */
    public int countOverrides(int lobbyId, String path) {
        LobbyConfig.LobbyEntry entry = lobbies.getLobbies().get(String.valueOf(lobbyId));
        if (entry == null || entry.getOverrides() == null || path == null || path.isBlank()) {
            return 0;
        }
        Object node = getNode(entry.getOverrides().getSettings(), path.trim().split("\\."));
        return node == null ? 0 : leafCount(node);
    }

    /**
     * Validates and stores a scalar override, creating the lobby entry
     * on demand. Dynamic bounds resolve against this lobby's effective
     * values. Lists and unknown paths fail like globals.
     */
    public ConfigService.SetOutcome setSettingOverride(int lobbyId, String path, String raw) {
        SettingDescriptor descriptor = path == null ? null : SettingRegistry.byPath(path.trim());
        if (descriptor == null) {
            return ConfigService.SetOutcome.fail("manhunt.setting-invalid", Map.of());
        }
        SettingRegistry.ValidationOutcome validation = SettingRegistry.validate(
                descriptor, raw, lookup -> effectiveRaw(lobbyId, lookup));
        if (!validation.ok()) {
            Map<String, String> slots = new HashMap<>(validation.slots());
            slots.put("setting", descriptor.path());
            return ConfigService.SetOutcome.fail(validation.errorKey(), slots);
        }
        Object oldValue = effectiveValue(lobbyId, descriptor.path());
        putNode(entryForWrite(lobbyId).getOverrides().getSettings(),
                descriptor.path().split("\\."), validation.value());
        saver.run();
        return ConfigService.SetOutcome.ok(descriptor, oldValue, validation.value());
    }

    /**
     * Stores a whole string-list override, replacing any previous one.
     * Entries are trimmed verbatim like global list writes.
     */
    public ConfigService.SetOutcome setListOverride(int lobbyId, String listPath,
            List<String> entries) {
        String canonical = listPath == null ? null
                : SettingRegistry.canonicalListPath(listPath.trim());
        if (canonical == null) {
            return ConfigService.SetOutcome.fail("manhunt.setting-invalid", Map.of());
        }
        List<String> stored = new ArrayList<>(entries.size());
        for (String entry : entries) {
            stored.add(entry == null ? "" : entry.trim());
        }
        Object oldValue = new ArrayList<>(getStringList(lobbyId, canonical));
        putNode(entryForWrite(lobbyId).getOverrides().getSettings(),
                canonical.split("\\."), stored);
        saver.run();
        return ConfigService.SetOutcome.ok(null, oldValue, new ArrayList<>(stored));
    }

    /** Replaces one effective list entry and stores the whole list. */
    public ConfigService.SetOutcome listSetOverride(int lobbyId, String listPath, int index,
            String raw) {
        String canonical = listPath == null ? null
                : SettingRegistry.canonicalListPath(listPath.trim());
        if (canonical == null) {
            return ConfigService.SetOutcome.fail("manhunt.setting-invalid", Map.of());
        }
        List<String> current = new ArrayList<>(getStringList(lobbyId, canonical));
        if (index < 0 || index >= current.size()) {
            return ConfigService.SetOutcome.fail("manhunt.setting-index-invalid",
                    Map.of("setting", canonical, "index", String.valueOf(index),
                            "size", String.valueOf(current.size())));
        }
        Object oldValue = current.get(index);
        current.set(index, raw == null ? "" : raw.trim());
        putNode(entryForWrite(lobbyId).getOverrides().getSettings(),
                canonical.split("\\."), current);
        saver.run();
        return ConfigService.SetOutcome.ok(null, oldValue, current.get(index));
    }

    /** Appends one list entry to the effective list and stores the whole list. */
    public ConfigService.SetOutcome listAddOverride(int lobbyId, String listPath, String raw) {
        String canonical = listPath == null ? null
                : SettingRegistry.canonicalListPath(listPath.trim());
        if (canonical == null) {
            return ConfigService.SetOutcome.fail("manhunt.setting-invalid", Map.of());
        }
        List<String> current = new ArrayList<>(getStringList(lobbyId, canonical));
        String value = raw == null ? "" : raw.trim();
        current.add(value);
        putNode(entryForWrite(lobbyId).getOverrides().getSettings(),
                canonical.split("\\."), current);
        saver.run();
        return ConfigService.SetOutcome.ok(null, "-", value);
    }

    /** Removes one effective list entry and stores the whole list. */
    public ConfigService.SetOutcome listRemoveOverride(int lobbyId, String listPath, int index) {
        String canonical = listPath == null ? null
                : SettingRegistry.canonicalListPath(listPath.trim());
        if (canonical == null) {
            return ConfigService.SetOutcome.fail("manhunt.setting-invalid", Map.of());
        }
        List<String> current = new ArrayList<>(getStringList(lobbyId, canonical));
        if (index < 0 || index >= current.size()) {
            return ConfigService.SetOutcome.fail("manhunt.setting-index-invalid",
                    Map.of("setting", canonical, "index", String.valueOf(index),
                            "size", String.valueOf(current.size())));
        }
        Object oldValue = current.remove(index);
        putNode(entryForWrite(lobbyId).getOverrides().getSettings(),
                canonical.split("\\."), current);
        saver.run();
        return ConfigService.SetOutcome.ok(null, oldValue, "-");
    }

    /**
     * Removes the node at the path (scalar, list, or whole subtree) and
     * prunes emptied parents. Returns the removed leaf count.
     */
    public int clearOverrides(int lobbyId, String path) {
        LobbyConfig.LobbyEntry entry = lobbies.getLobbies().get(String.valueOf(lobbyId));
        if (entry == null || entry.getOverrides() == null || path == null || path.isBlank()) {
            return 0;
        }
        int removed = removeRecursive(entry.getOverrides().getSettings(),
                path.trim().split("\\."), 0);
        if (removed > 0) {
            pruneEntryIfEmpty(lobbyId);
            saver.run();
        }
        return removed;
    }

    /** Removes every override of one lobby. Returns the removed leaf count. */
    public int clearLobby(int lobbyId) {
        LobbyConfig.LobbyEntry entry = lobbies.getLobbies().get(String.valueOf(lobbyId));
        if (entry == null || entry.getOverrides() == null) {
            return 0;
        }
        int removed = leafCount(entry.getOverrides().getSettings())
                + entry.getOverrides().getModifiers().size();
        entry.getOverrides().getSettings().clear();
        entry.getOverrides().getModifiers().clear();
        if (removed > 0) {
            pruneEntryIfEmpty(lobbyId);
            saver.run();
        }
        return removed;
    }

    /** Forced enabled flag for one modifier, or empty when not overridden. */
    public Optional<Boolean> modifierOverride(int lobbyId, String id) {
        LobbyConfig.LobbyEntry entry = lobbies.getLobbies().get(String.valueOf(lobbyId));
        if (entry == null || entry.getOverrides() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entry.getOverrides().getModifiers().get(id));
    }

    /** True when the lobby forces the modifier's enabled flag. */
    public boolean hasModifierOverride(int lobbyId, String id) {
        return modifierOverride(lobbyId, id).isPresent();
    }

    /**
     * Forces one modifier's enabled flag. False when the id is unknown;
     * unknown lobby entries are created on demand.
     */
    public boolean setModifierOverride(int lobbyId, String id, boolean value) {
        if (id == null || !config.modifierNames().contains(id)) {
            return false;
        }
        entryForWrite(lobbyId).getOverrides().getModifiers().put(id, value);
        saver.run();
        return true;
    }

    /** Removes one modifier override. True when one existed. */
    public boolean clearModifierOverride(int lobbyId, String id) {
        LobbyConfig.LobbyEntry entry = lobbies.getLobbies().get(String.valueOf(lobbyId));
        if (entry == null || entry.getOverrides() == null || id == null) {
            return false;
        }
        boolean removed = entry.getOverrides().getModifiers().remove(id) != null;
        if (removed) {
            pruneEntryIfEmpty(lobbyId);
            saver.run();
        }
        return removed;
    }

    /**
     * Effective raw value: the override when one is stored, else the
     * global. A null lobby always reads the global.
     */
    public Object effectiveRaw(Integer lobbyId, String path) {
        if (lobbyId == null) {
            return config.getValue(path);
        }
        return rawOverride(lobbyId, path).orElseGet(() -> config.getValue(path));
    }

    /** Effective display value with enums as their names. */
    public Object effectiveValue(Integer lobbyId, String path) {
        return ConfigService.normalizedValue(effectiveRaw(lobbyId, path));
    }

    /** Effective boolean: corrupt overrides fall back to the global. */
    public boolean getBoolean(Integer lobbyId, String setting, boolean defaultValue) {
        Object override = lobbyId == null ? null
                : rawOverride(lobbyId, setting).orElse(null);
        if (override instanceof Boolean bool) {
            return bool;
        }
        return config.getBoolean(setting, defaultValue);
    }

    /** Effective string: corrupt overrides fall back to the global. */
    public String getString(Integer lobbyId, String setting, String defaultValue) {
        Object override = lobbyId == null ? null
                : rawOverride(lobbyId, setting).orElse(null);
        if (override instanceof String text) {
            return text;
        }
        return config.getString(setting, defaultValue);
    }

    /** Effective int: corrupt overrides fall back to the global. */
    public int getInt(Integer lobbyId, String setting, int defaultValue) {
        Object override = lobbyId == null ? null
                : rawOverride(lobbyId, setting).orElse(null);
        if (override instanceof Number number) {
            return number.intValue();
        }
        return config.getInt(setting, defaultValue);
    }

    /** Effective double: corrupt overrides fall back to the global. */
    public double getDouble(Integer lobbyId, String setting, double defaultValue) {
        Object override = lobbyId == null ? null
                : rawOverride(lobbyId, setting).orElse(null);
        if (override instanceof Number number) {
            return number.doubleValue();
        }
        return config.getDouble(setting, defaultValue);
    }

    public float getFloat(Integer lobbyId, String setting, float defaultValue) {
        return (float) getDouble(lobbyId, setting, defaultValue);
    }

    /** Effective string list: corrupt overrides fall back to the global. */
    public List<String> getStringList(Integer lobbyId, String setting) {
        Object override = lobbyId == null ? null
                : rawOverride(lobbyId, setting).orElse(null);
        if (override instanceof List<?> list) {
            return ConfigService.stringListValue(list);
        }
        return config.getStringList(setting);
    }

    /** Effective enum: corrupt or unmatched overrides fall back to the global. */
    public <T extends Enum<T>> T getEnum(Integer lobbyId, String setting, Class<T> type,
            T fallback) {
        Object override = lobbyId == null ? null
                : rawOverride(lobbyId, setting).orElse(null);
        if (override instanceof String raw) {
            for (T constant : type.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(raw.trim())) {
                    return constant;
                }
            }
        }
        return config.getEnum(setting, type, fallback);
    }

    /** Effective modifier flag: the override wins, else the global. */
    public boolean modifierEnabled(Integer lobbyId, String name) {
        if (lobbyId != null) {
            Optional<Boolean> override = modifierOverride(lobbyId, name);
            if (override.isPresent()) {
                return override.get();
            }
        }
        return config.modifierEnabled(name);
    }

    /**
     * Effective preset flag: true when every member is effectively
     * enabled. Unknown or memberless presets read as off, like globals.
     */
    public boolean presetEnabled(Integer lobbyId, String id) {
        List<String> members = config.presetMembers(id);
        if (members.isEmpty() || !config.presetNames().contains(id)) {
            return false;
        }
        for (String member : members) {
            if (!modifierEnabled(lobbyId, member)) {
                return false;
            }
        }
        return true;
    }

    /** Lobby entry for writes, created on demand for unknown ids. */
    private LobbyConfig.LobbyEntry entryForWrite(int lobbyId) {
        LobbyConfig.LobbyEntry entry = lobbies.getLobbies()
                .computeIfAbsent(String.valueOf(lobbyId), key -> new LobbyConfig.LobbyEntry());
        if (entry.getOverrides() == null) {
            entry.setOverrides(new LobbyConfig.OverridesData());
        }
        return entry;
    }

    /** Drops entries left with no tp, no bounds, and no overrides. */
    private void pruneEntryIfEmpty(int lobbyId) {
        String key = String.valueOf(lobbyId);
        LobbyConfig.LobbyEntry entry = lobbies.getLobbies().get(key);
        if (entry == null) {
            return;
        }
        LobbyConfig.OverridesData overrides = entry.getOverrides();
        boolean overridesEmpty = overrides == null
                || (overrides.getSettings().isEmpty() && overrides.getModifiers().isEmpty());
        LobbyConfig.BoundsData bounds = entry.getBounds();
        boolean boundsEmpty = bounds == null || (bounds.getPos1() == null && bounds.getPos2() == null);
        if (entry.getLobbytp() == null && boundsEmpty && overridesEmpty) {
            lobbies.getLobbies().remove(key);
        }
    }

    /** Reads one node through maps and loaded sections. */
    private static Object getNode(Object root, String[] segments) {
        Object current = root;
        for (String segment : segments) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(segment);
            } else if (current instanceof ConfigurationSection section) {
                current = section.get(segment);
            } else {
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /** Writes one leaf, creating same-typed parents along the way. */
    @SuppressWarnings("unchecked")
    private static void putNode(Object root, String[] segments, Object value) {
        Object current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            String segment = segments[index];
            if (current instanceof Map<?, ?> map) {
                Object child = map.get(segment);
                if (!isBranch(child)) {
                    child = new LinkedHashMap<String, Object>();
                    ((Map<String, Object>) map).put(segment, child);
                }
                current = child;
            } else if (current instanceof ConfigurationSection section) {
                Object child = section.get(segment);
                if (!isBranch(child)) {
                    section.set(segment, null);
                    child = section.createSection(segment);
                }
                current = child;
            } else {
                return;
            }
        }
        String leaf = segments[segments.length - 1];
        if (current instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).put(leaf, value);
        } else if (current instanceof ConfigurationSection section) {
            section.set(leaf, value);
        }
    }

    /**
     * Removes the node at the path and prunes newly emptied parents.
     * Returns the removed leaf count (a stored list counts as one).
     */
    @SuppressWarnings("unchecked")
    private static int removeRecursive(Object node, String[] segments, int index) {
        String segment = segments[index];
        boolean last = index == segments.length - 1;
        if (node instanceof Map<?, ?> map) {
            Object child = map.get(segment);
            if (child == null) {
                return 0;
            }
            if (last) {
                int removed = leafCount(child);
                ((Map<String, Object>) map).remove(segment);
                return removed;
            }
            int removed = removeRecursive(child, segments, index + 1);
            if (removed > 0 && isEmptyBranch(child)) {
                ((Map<String, Object>) map).remove(segment);
            }
            return removed;
        }
        if (node instanceof ConfigurationSection section) {
            Object child = section.get(segment);
            if (child == null) {
                return 0;
            }
            if (last) {
                int removed = leafCount(child);
                section.set(segment, null);
                return removed;
            }
            int removed = removeRecursive(child, segments, index + 1);
            if (removed > 0 && isEmptyBranch(child)) {
                section.set(segment, null);
            }
            return removed;
        }
        return 0;
    }

    /** True for navigable containers: plain maps and loaded sections. */
    private static boolean isBranch(Object node) {
        return node instanceof Map || node instanceof ConfigurationSection;
    }

    private static boolean isEmptyBranch(Object node) {
        if (node instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (node instanceof ConfigurationSection section) {
            return section.getKeys(false).isEmpty();
        }
        return false;
    }

    /** Leaf count of a subtree: stored lists count as one leaf. */
    private static int leafCount(Object node) {
        if (node instanceof Map<?, ?> map) {
            int count = 0;
            for (Object child : map.values()) {
                count += leafCount(child);
            }
            return count;
        }
        if (node instanceof ConfigurationSection section) {
            int count = 0;
            for (String key : section.getKeys(false)) {
                count += leafCount(section.get(key));
            }
            return count;
        }
        return 1;
    }
}
