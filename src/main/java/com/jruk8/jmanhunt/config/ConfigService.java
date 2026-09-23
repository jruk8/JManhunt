package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.command.SettingValueParser;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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
    private final ModifierStore modifiers;
    private final Map<String, List<BiConsumer<Boolean, Boolean>>> listeners = new HashMap<>();

    public ConfigService(JManhuntPlugin plugin, ModifierStore modifiers) {
        this.plugin = plugin;
        this.modifiers = modifiers;
    }

    /** Registers a callback fired whenever the given setting is changed via {@link #setBoolean}. */
    public void onChange(String setting, BiConsumer<Boolean, Boolean> listener) {
        listeners.computeIfAbsent(setting, k -> new ArrayList<>()).add(listener);
    }

    public Set<String> settingNames() {
        return settingNames(plugin.getConfig());
    }

    /**
     * Editable setting names for the given config. Package-visible so unit
     * tests can exercise it without a running server: the plugin instance
     * itself is not mockable on the unit-test classpath.
     */
    public static Set<String> settingNames(FileConfiguration config) {
        Set<String> names = new TreeSet<>();
        var gameRules = config.getConfigurationSection("match.game-rules");
        if (gameRules != null) {
            if (gameRules.contains("enabled")) {
                names.add("match.game-rules.enabled");
            }
            var rules = gameRules.getConfigurationSection("rules");
            if (rules != null) {
                for (String key : rules.getKeys(false)) {
                    names.add("match.game-rules.rules." + key);
                }
            }
        }
        names.addAll(extraModifierNames(config));
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
        return modifiers.modifierNames();
    }

    public boolean modifierEnabled(String name) {
        return modifiers.isEnabled(name);
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

    private static Set<String> extraModifierNames(FileConfiguration root) {
        Set<String> names = new TreeSet<>();
        for (String key : root.getKeys(false)) {
            // config-version and send-anonymous-statistics are not editable.
            // A stale config.yml modifiers: block (pre-move leftover) stays
            // hidden too; modifiers live in modifiers.yml now.
            if (key.equals("config-version")
                    || key.equals("send-anonymous-statistics")
                    || key.equals("modifiers")) {
                continue;
            }
            ConfigurationSection child = root.getConfigurationSection(key);
            if (child != null) {
                collectExtraModifierNames(child, "", key + ".", names);
            } else {
                names.add(key);
            }
        }
        return names;
    }

    private static void collectExtraModifierNames(ConfigurationSection section, String prefix, String root,
            Set<String> names) {
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
                    names.add(root + path + ".enabled");
                }
                collectExtraModifierNames(child, path, root, names);
            } else {
                // Scalar leaf: booleans, ints, doubles, floats and strings
                // (including enums) are all editable in-game via /manhunt
                // modifiers. Lists and maps are excluded.
                names.add(root + path);
            }
        }
    }
}