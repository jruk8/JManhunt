package com.jruk8.jmanhunt.tutorial;

import com.jruk8.jmanhunt.tutorial.config.TutorialConfig;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

/**
 * Creates and reloads the Okaeri tutorial store. The bundled
 * Core/tutorial.yml ships the dialogue content and is copied to the data
 * folder on first load, like any other default resource.
 */
public final class TutorialConfigRegistrar {

    private final JavaPlugin plugin;
    private TutorialConfig tutorialConfig;

    public TutorialConfigRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        File file = new File(plugin.getDataFolder(), "Core/tutorial.yml");
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        if (!file.isFile()) {
            plugin.saveResource("Core/tutorial.yml", false);
        }
        this.tutorialConfig = createConfig(TutorialConfig.class, file);
    }

    public TutorialConfig getTutorialConfig() {
        return tutorialConfig;
    }

    public void reload() {
        if (this.tutorialConfig == null) {
            return;
        }
        saveAndLoad(this.tutorialConfig);
    }

    private <T extends OkaeriConfig> T createConfig(Class<T> configClass, File file) {
        return ConfigManager.create(configClass, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withBindFile(file);
            it.withRemoveOrphans(true);
            saveAndLoad(it);
        });
    }

    private static void saveAndLoad(OkaeriConfig config) {
        config.saveDefaults();
        config.load(true);
    }
}
