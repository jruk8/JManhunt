package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

/**
 * Creates and reloads the Okaeri modifiers store. The bundled
 * modifiers.yml seeds the data folder on first run; version merges
 * run through YamlFileUpdater before every load, so Okaeri only
 * ever reads an up-to-date file.
 */
public final class ModifiersRegistrar {

    private final JavaPlugin plugin;
    private ModifiersConfig modifiersConfig;

    public ModifiersRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        File file = new File(plugin.getDataFolder(), "modifiers.yml");
        if (!file.exists()) {
            plugin.saveResource("modifiers.yml", false);
        }
        this.modifiersConfig = ConfigManager.create(ModifiersConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit(), new ModifierCommandsPack());
            it.withBindFile(file);
            it.withRemoveOrphans(true);
            it.withLogger(plugin.getLogger());
        });
        reload();
    }

    public ModifiersConfig getModifiersConfig() {
        return modifiersConfig;
    }

    /**
     * Saves defaults when missing, then loads and repairs the file.
     * A damaged file logs instead of crashing; previously loaded
     * values stay live until the file is fixed and reloaded.
     */
    public void reload() {
        if (this.modifiersConfig == null) {
            return;
        }
        try {
            this.modifiersConfig.saveDefaults();
            this.modifiersConfig.load(true);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not load modifiers.yml (" + exception.getMessage()
                    + "); check the file, then run /mh reload.");
        }
    }
}
