package com.jruk8.jmanhunt;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;

/** Central point for reading/writing plugin config settings and reacting to changes. */
public final class ConfigService {
    private final JManhuntPlugin plugin;
    private final Map<String, List<BiConsumer<Boolean, Boolean>>> listeners = new HashMap<>();

    public ConfigService(JManhuntPlugin plugin) {
        this.plugin = plugin;
    }

    /** Registers a callback fired whenever the given setting is changed via {@link #setBoolean}. */
    public void onChange(String setting, BiConsumer<Boolean, Boolean> listener) {
        listeners.computeIfAbsent(setting, k -> new ArrayList<>()).add(listener);
    }

    public Set<String> settingNames() {
        Set<String> names = new TreeSet<>();
        var defaults = plugin.getConfig().getConfigurationSection("gamestate-commands.default-commands");
        if (defaults != null) {
            if (defaults.contains("enabled")) {
                names.add("default-commands.enabled");
            }
            for (String key : defaults.getKeys(false)) {
                if (!key.equals("enabled")) {
                    names.add("default-commands." + key);
                }
            }
        }
        for (String name : modifierNames()) {
            names.add("custom-modifiers." + name + ".enabled");
        }
        names.addAll(extraModifierNames());
        return names;
    }

    public boolean getBoolean(String setting, boolean defaultValue) {
        return plugin.getConfig().getBoolean(setting, defaultValue);
    }

    public String getString(String setting, String defaultValue) {
        return plugin.getConfig().getString(setting, defaultValue);
    }

    public float getFloat(String setting, float defaultValue) {
        return (float) plugin.getConfig().getDouble(setting, defaultValue);
    }

    public int getInt(String setting, int defaultValue) {
        return plugin.getConfig().getInt(setting, defaultValue);
    }

    /** Returns the raw config value for the given path, or null if absent. */
    public Object getValue(String setting) {
        return plugin.getConfig().get(setting);
    }

    /**
     * Sets a scalar config value parsed from a raw string. Values are parsed
     * against the current type in config.yml: booleans and numbers are
     * validated, strings/enums are stored verbatim. Returns true on success.
     */
    public boolean setValue(String setting, String raw) {
        Object current = plugin.getConfig().get(setting);
        return SettingValueParser.parse(current, raw, (oldValue, newValue) -> {
            if (newValue instanceof Boolean bool) {
                setBoolean(setting, bool);
            } else {
                plugin.getConfig().set(setting, newValue);
                plugin.saveConfig();
            }
        });
    }

    public boolean setBoolean(String setting, boolean value) {
        boolean oldValue = plugin.getConfig().getBoolean(setting);
        plugin.getConfig().set(setting, value);
        plugin.saveConfig();

        fireChange(setting, oldValue, value);
        return true;
    }

    public Set<String> modifierNames() {
        var section = plugin.getConfig().getConfigurationSection("custom-modifiers");
        return section == null ? Set.of() : section.getKeys(false);
    }

    public boolean modifierEnabled(String name) {
        return plugin.getConfig().getBoolean("custom-modifiers." + name + ".enabled", false);
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

    private Set<String> extraModifierNames() {
        Set<String> names = new TreeSet<>();
        collectExtraModifierNames(plugin.getConfig().getConfigurationSection("settings"), "", names);
        return names;
    }

    private void collectExtraModifierNames(ConfigurationSection section, String prefix, Set<String> names) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) {
                // Recurse into nested sections. Also add explicit "enabled"
                // toggles so that boolean switches are listed under their
                // .enabled path as before.
                if (child.contains("enabled")) {
                    names.add("settings." + path + ".enabled");
                }
                collectExtraModifierNames(child, path, names);
            } else {
                // Scalar leaf: booleans, ints, doubles, floats and strings
                // (including enums) are all editable in-game via /manhunt
                // modifiers. Lists and maps are excluded.
                names.add("settings." + path);
            }
        }
    }
}