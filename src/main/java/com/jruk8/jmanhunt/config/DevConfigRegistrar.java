package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import lombok.Getter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Function;

/**
 * Creates and reloads the Okaeri dev-data store. The bundled
 * Core/dev.yml is loaded straight from the jar and is never copied
 * to the data folder, so lobby presets are dev-time only.
 */
public final class DevConfigRegistrar {

    private final Function<String, InputStream> resources;
    private final SectionPinner sections = new SectionPinner();
    @Getter
    private DevConfig devConfig;

    public DevConfigRegistrar(Function<String, InputStream> resources) {
        this.resources = resources;
    }

    public void register() {
        this.devConfig = ConfigManager.create(DevConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withRemoveOrphans(true);
        });
        reload();
    }

    public void reload() {
        if (this.devConfig == null) {
            return;
        }
        try (InputStream bundled = resources.apply("Core/dev.yml")) {
            if (bundled == null) {
                throw new IOException("bundled Core/dev.yml is missing");
            }
            this.devConfig.load(bundled);
            sections.pin(this.devConfig);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load the bundled dev data.", exception);
        }
    }
}
