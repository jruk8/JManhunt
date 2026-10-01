package com.jruk8.jmanhunt.placeholders;

import com.jruk8.jmanhunt.config.SectionPinner;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import lombok.Getter;
import java.io.File;
import java.nio.file.Path;

/**
 * Creates and reloads the Okaeri placeholders store. Defaults generate
 * from {@link PlaceholderConfig}; an existing data-folder file keeps
 * its values while missing entries fill in.
 */
public final class PlaceholderConfigRegistrar {

    private final Path dataFolder;
    private final SectionPinner sections = new SectionPinner();
    @Getter
    private PlaceholderConfig placeholderConfig;

    public PlaceholderConfigRegistrar(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void register() {
        File file = dataFolder.resolve("placeholders.yml").toFile();
        this.placeholderConfig = ConfigManager.create(PlaceholderConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withBindFile(file);
            it.withRemoveOrphans(true);
            saveAndLoad(it);
        });
    }

    public void reload() {
        if (this.placeholderConfig == null) {
            return;
        }
        saveAndLoad(this.placeholderConfig);
    }

    private void saveAndLoad(OkaeriConfig config) {
        config.saveDefaults();
        config.load(true);
        sections.pin(config);
    }
}
