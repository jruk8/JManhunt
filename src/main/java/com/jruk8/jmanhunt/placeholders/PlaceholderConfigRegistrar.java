package com.jruk8.jmanhunt.placeholders;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

/**
 * Creates and reloads the Okaeri placeholders store. Defaults generate
 * from {@link PlaceholderConfig}; an existing data-folder file keeps
 * its values while missing entries fill in.
 */
public final class PlaceholderConfigRegistrar {

    private final JavaPlugin plugin;
    private PlaceholderConfig placeholderConfig;

    public PlaceholderConfigRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        File file = new File(plugin.getDataFolder(), "placeholders.yml");
        this.placeholderConfig = ConfigManager.create(PlaceholderConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withBindFile(file);
            it.withRemoveOrphans(true);
            saveAndLoad(it);
        });
    }

    public PlaceholderConfig getPlaceholderConfig() {
        return placeholderConfig;
    }

    public void reload() {
        if (this.placeholderConfig == null) {
            return;
        }
        saveAndLoad(this.placeholderConfig);
    }

    private static void saveAndLoad(OkaeriConfig config) {
        config.saveDefaults();
        config.load(true);
    }
}
