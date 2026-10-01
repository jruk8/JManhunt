package com.jruk8.jmanhunt.lobby.config;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.SectionPinner;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import java.io.File;
import lombok.Getter;

/**
 * Creates and reloads the Okaeri lobby store, mirroring the
 * JManhunt-Challenges config structure: one registrar owns creation
 * (bind file, remove orphans, save defaults plus load) and reload
 * repeats the save-and-load.
 */
public final class LobbyConfigRegistrar {

    private final JManhuntPlugin plugin;
    private final SectionPinner sections = new SectionPinner();
    @Getter
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

    private void saveAndLoad(OkaeriConfig config) {
        config.saveDefaults();
        config.load(true);
        sections.pin(config);
    }
}
