package com.jruk8.jmanhunt.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.Map;

/** Relocates renamed YAML keys while retaining user values. */
public final class YamlFileUpdater {
    private YamlFileUpdater() {
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

}
