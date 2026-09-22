package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.JManhuntPlugin;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import java.io.File;

/**
 * Creates and reloads the Okaeri lobby store, mirroring the
 * JManhunt-Challenges config structure: one registrar owns creation
 * (bind file, remove orphans, save defaults plus load) and reload
 * repeats the save-and-load.
 */
public final class LobbyConfigRegistrar {

    private final JManhuntPlugin plugin;
    private LobbyConfig lobbyConfig;

    public LobbyConfigRegistrar(JManhuntPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        File file = new File(plugin.getDataFolder(), "settings/world-engine/lobby-config.yml");
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        this.lobbyConfig = createConfig(LobbyConfig.class, file);
    }

    public LobbyConfig getLobbyConfig() {
        return lobbyConfig;
    }

    public void reload() {
        if (this.lobbyConfig == null) {
            return;
        }
        saveAndLoad(this.lobbyConfig);
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
