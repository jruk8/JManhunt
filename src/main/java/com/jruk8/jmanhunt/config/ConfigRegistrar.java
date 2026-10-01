package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Creates and reloads the Okaeri root and sounds stores. Both files
 * generate from schema on first run; every load writes missing
 * defaults with comments and drops orphaned keys, so old settings
 * paths and the former inline sounds block vanish on upgrade.
 */
public final class ConfigRegistrar {

    private static final Set<String> CURRENT_CATEGORIES =
            Set.of("match", "compass", "players", "server");

    private final Path dataFolder;
    private final Logger log;
    private final SectionPinner sections = new SectionPinner();
    @Getter
    private JManhuntConfig root;
    @Getter
    private SoundsConfig sounds;

    public ConfigRegistrar(Path dataFolder, Logger log) {
        this.dataFolder = dataFolder;
        this.log = log;
    }

    public void register() {
        List<String> stale = staleBlocks(dataFolder.resolve("config.yml").toFile());
        if (!stale.isEmpty()) {
            log.warning("config.yml still has retired blocks ("
                    + String.join(", ", stale) + "); settings regrouped under match, compass, "
                    + "players, and server and sounds moved to sounds.yml, so re-apply any "
                    + "tweaks there. The stale blocks will be removed.");
        }
        this.root = ConfigManager.create(JManhuntConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withBindFile(dataFolder.resolve("config.yml").toFile());
            it.withRemoveOrphans(true);
            it.withLogger(log);
        });
        this.sounds = ConfigManager.create(SoundsConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withBindFile(dataFolder.resolve("sounds.yml").toFile());
            it.withRemoveOrphans(true);
            it.withLogger(log);
        });
        reload();
    }

    /**
     * Saves defaults when missing, then loads and repairs both files.
     * A damaged file logs instead of crashing; previously loaded
     * values stay live until the file is fixed and reloaded.
     */
    public void reload() {
        load(root, "config.yml");
        load(sounds, "sounds.yml");
        sections.pin(root);
        sections.pin(sounds);
    }

    /**
     * Retired pre-regroup blocks in a raw config file: the old inline
     * sounds block plus any settings child outside the four current
     * categories. Missing files report nothing.
     */
    static List<String> staleBlocks(File file) {
        List<String> stale = new ArrayList<>();
        if (file == null || !file.isFile()) {
            return stale;
        }
        YamlConfiguration raw = YamlConfiguration.loadConfiguration(file);
        if (raw.isConfigurationSection("sounds")) {
            stale.add("sounds");
        }
        ConfigurationSection settings = raw.getConfigurationSection("settings");
        if (settings == null) {
            return stale;
        }
        for (String child : settings.getKeys(false)) {
            if (!CURRENT_CATEGORIES.contains(child)) {
                stale.add("settings." + child);
            }
        }
        return stale;
    }

    private void load(OkaeriConfig config, String name) {
        if (config == null) {
            return;
        }
        try {
            config.saveDefaults();
            config.load(true);
        } catch (RuntimeException exception) {
            log.warning("Could not load " + name + " (" + exception.getMessage()
                    + "); check the file, then run /mh reload.");
        }
    }
}
