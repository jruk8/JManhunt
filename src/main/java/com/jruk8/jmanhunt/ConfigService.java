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
            if (defaults.contains("enabled")) names.add("default-commands.enabled");
            for (String key : defaults.getKeys(false)) {
                if (!key.equals("enabled")) names.add("default-commands." + key);
            }
        }
        for (String name : modifierNames()) names.add("custom-modifiers." + name + ".enabled");
        names.addAll(extraModifierNames());
        var challenges = plugin.getConfig().getConfigurationSection("challenges");
        if (challenges != null) {
            for (String name : challenges.getKeys(false)) {
                names.add("challenges." + name + ".enabled");
            }
        }
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
        if (list == null) return;
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
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null && child.contains("enabled")) {
                names.add("settings." + path + ".enabled");
                // Also recurse into the section to find nested enabled keys
                // (e.g. world-engine.world-border.enabled inside world-engine).
                collectExtraModifierNames(child, path, names);
            } else if (child != null) {
                collectExtraModifierNames(child, path, names);
            } else if (section.isBoolean(key)) {
                // Use the path as-is so that the "enabled" key itself resolves
                // to settings.<path> (e.g. settings.world-engine.enabled) and
                // plain boolean toggles resolve to their actual config path
                // (e.g. settings.loot-tables.custom-piglin-barter).
                names.add("settings." + path);
            }
        }
    }
}