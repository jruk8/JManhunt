package com.jruk8.jmanhunt.message;

import com.jruk8.jmanhunt.config.SectionPinner;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

/**
 * Creates and reloads the Okaeri messages store. The file generates
 * from schema on first run; every load writes missing defaults and
 * drops orphaned keys (including the retired messages-version), so
 * no migration step exists.
 */
public final class MessagesRegistrar {

    private final JavaPlugin plugin;
    private final SectionPinner sections = new SectionPinner();
    @Getter
    private MessagesConfig messagesConfig;

    public MessagesRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.messagesConfig = ConfigManager.create(MessagesConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withBindFile(file);
            it.withRemoveOrphans(true);
            it.withLogger(plugin.getLogger());
        });
        reload();
    }

    /**
     * Saves defaults when missing, then loads and repairs the file.
     * A damaged file logs instead of crashing; previously loaded
     * values stay live until the file is fixed and reloaded.
     */
    public void reload() {
        if (messagesConfig == null) {
            return;
        }
        try {
            messagesConfig.saveDefaults();
            messagesConfig.load(true);
            sections.pin(messagesConfig);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not load messages.yml (" + exception.getMessage()
                    + "); check the file, then run /mh reload.");
        }
    }
}
