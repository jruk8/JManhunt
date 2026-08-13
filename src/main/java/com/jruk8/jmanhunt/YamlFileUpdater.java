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

/** Updates user YAML files by adding missing defaults while retaining user values. */
public final class YamlFileUpdater {
    private YamlFileUpdater() {
    }

    public static FileConfiguration update(
            JavaPlugin plugin, String resourceName, String versionKey, int currentVersion) {
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
            mergeMissing(user, defaults, "");
            user.set(versionKey, currentVersion);
            user.options().parseComments(true);
            user.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not update " + resourceName + ": " + exception.getMessage());
        }
        return user;
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
