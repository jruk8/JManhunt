package com.jruk8.jmanhunt;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Updates user YAML files by adding missing defaults while retaining user values. */
public final class YamlFileUpdater {
    private YamlFileUpdater() {
    }

    public static FileConfiguration update(
            JavaPlugin plugin, String resourceName, String versionKey, int currentVersion) {
        return update(plugin, resourceName, versionKey, currentVersion, Map.of());
    }

    public static FileConfiguration update(
            JavaPlugin plugin, String resourceName, String versionKey, int currentVersion,
            Map<String, String> moves) {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
        }
        FileConfiguration user = YamlConfiguration.loadConfiguration(file);
        try (InputStream stream = plugin.getResource(resourceName)) {
            if (stream == null) {
                return user;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            // Relocate before merging so freshly added defaults never block a
            // move from an old path.
            relocateKeys(user, moves);
            mergeMissing(user, defaults, "");
            user.set(versionKey, currentVersion);
            user.options().parseComments(true);
            user.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not update " + resourceName + ": " + exception.getMessage());
        }
        return user;
    }

    /**
     * Moves values from old config paths to new ones. A move only carries the
     * value when the target is absent (an already-customized target wins), but
     * the stale source path is always cleared.
     */
    static void relocateKeys(FileConfiguration user, Map<String, String> moves) {
        for (Map.Entry<String, String> move : moves.entrySet()) {
            relocate(user, move.getKey(), move.getValue());
        }
    }

    private static void relocate(FileConfiguration user, String from, String to) {
        if (!user.contains(from)) {
            return;
        }
        if (!user.contains(to)) {
            ConfigurationSection source = user.getConfigurationSection(from);
            if (source != null) {
                copySection(source, user.createSection(to));
            } else {
                user.set(to, user.get(from));
            }
        }
        user.set(from, null);
    }

    private static void copySection(ConfigurationSection from, ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            ConfigurationSection child = from.getConfigurationSection(key);
            if (child != null) {
                copySection(child, to.createSection(key));
            } else {
                to.set(key, from.get(key));
            }
        }
    }

    private static void mergeMissing(FileConfiguration user, ConfigurationSection defaults, String prefix) {
        for (String key : defaults.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (defaults.isConfigurationSection(key)) {
                if (!user.isConfigurationSection(path)) {
                    user.createSection(path);
                }
                mergeMissing(user, defaults.getConfigurationSection(key), path);
            } else if (!user.contains(path)) {
                // Under custom-modifiers.*.commands, treat the section as
                // user-owned and do not inject default command role keys
                // (player / speedrunner / hunter / console / ...) so that
                // renamed or removed keys are not resurrected on reload.
                if (isCustomModifierCommands(prefix)) {
                    ConfigurationSection commandsSection = user.getConfigurationSection(prefix);
                    if (commandsSection != null) {
                        continue;
                    }
                }
                user.set(path, defaults.get(path));
            }
        }
    }

    private static boolean isCustomModifierCommands(String prefix) {
        if (!prefix.startsWith("custom-modifiers.")) {
            return false;
        }
        return prefix.contains(".commands");
    }
}
