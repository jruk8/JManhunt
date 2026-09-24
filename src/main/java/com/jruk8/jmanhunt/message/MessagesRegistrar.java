package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

/**
 * Creates and reloads the Okaeri messages store. The bundled
 * messages.yml seeds the data folder on first run; every load
 * writes missing defaults and drops orphaned keys (including
 * the retired messages-version), so no migration step exists.
 */
public final class MessagesRegistrar {

    private final JavaPlugin plugin;
    private MessagesConfig messages;

    public MessagesRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = ConfigManager.create(MessagesConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withBindFile(file);
            it.withRemoveOrphans(true);
            it.withLogger(plugin.getLogger());
        });
        reload();
    }

    public MessagesConfig getMessagesConfig() {
        return messages;
    }

    /**
     * Saves defaults when missing, then loads and repairs the file.
     * A damaged file logs instead of crashing; previously loaded
     * values stay live until the file is fixed and reloaded.
     */
    public void reload() {
        if (messages == null) {
            return;
        }
        try {
            messages.saveDefaults();
            messages.load(true);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not load messages.yml (" + exception.getMessage()
                    + "); check the file, then run /mh reload.");
        }
    }
}
