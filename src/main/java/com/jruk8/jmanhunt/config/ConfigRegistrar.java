package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Creates and reloads the Okaeri root and sounds stores. The bundled
 * yml files seed the data folder on first run; every load writes
 * missing defaults with comments and drops orphaned keys, so old
 * settings paths and the former inline sounds block vanish on upgrade.
 */
public final class ConfigRegistrar {

    private static final Set<String> CURRENT_CATEGORIES =
            Set.of("match", "compass", "players", "server");

    private final JavaPlugin plugin;
    private JManhuntConfig root;
    private SoundsConfig sounds;

    public ConfigRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        seed("config.yml");
        seed("sounds.yml");
        List<String> stale = staleBlocks(new File(plugin.getDataFolder(), "config.yml"));
        if (!stale.isEmpty()) {
            plugin.getLogger().warning("config.yml still has retired blocks ("
                    + String.join(", ", stale) + "); settings regrouped under match, compass, "
                    + "players, and server and sounds moved to sounds.yml, so re-apply any "
                    + "tweaks there. The stale blocks will be removed.");
        }
        this.root = ConfigManager.create(JManhuntConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withBindFile(new File(plugin.getDataFolder(), "config.yml"));
            it.withRemoveOrphans(true);
            it.withLogger(plugin.getLogger());
        });
        this.sounds = ConfigManager.create(SoundsConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withBindFile(new File(plugin.getDataFolder(), "sounds.yml"));
            it.withRemoveOrphans(true);
            it.withLogger(plugin.getLogger());
        });
        reload();
    }

    public JManhuntConfig getRoot() {
        return root;
    }

    public SoundsConfig getSounds() {
        return sounds;
    }

    /**
     * Saves defaults when missing, then loads and repairs both files.
     * A damaged file logs instead of crashing; previously loaded
     * values stay live until the file is fixed and reloaded.
     */
    public void reload() {
        load(root, "config.yml");
        load(sounds, "sounds.yml");
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

    private void seed(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }

    private void load(OkaeriConfig config, String name) {
        if (config == null) {
            return;
        }
        try {
            config.saveDefaults();
            config.load(true);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not load " + name + " (" + exception.getMessage()
                    + "); check the file, then run /mh reload.");
        }
    }
}
