package com.jruk8.jmanhunt.message;

import com.jruk8.jmanhunt.config.SectionPinner;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import lombok.Getter;
import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Creates and reloads the Okaeri messages store. The file generates
 * from schema on first run; every load writes missing defaults and
 * drops orphaned keys (including the retired messages-version), so
 * no migration step exists.
 */
public final class MessagesRegistrar {

    private final Path dataFolder;
    private final Logger log;
    private final SectionPinner sections = new SectionPinner();
    @Getter
    private MessagesConfig messagesConfig;

    public MessagesRegistrar(Path dataFolder, Logger log) {
        this.dataFolder = dataFolder;
        this.log = log;
    }

    public void register() {
        File file = dataFolder.resolve("messages.yml").toFile();
        this.messagesConfig = ConfigManager.create(MessagesConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withBindFile(file);
            it.withRemoveOrphans(true);
            it.withLogger(log);
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
            log.warning("Could not load messages.yml (" + exception.getMessage()
                    + "); check the file, then run /mh reload.");
        }
    }
}
