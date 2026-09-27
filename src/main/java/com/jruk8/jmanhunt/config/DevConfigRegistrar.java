package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Creates and reloads the Okaeri dev-data store. The bundled
 * Core/dev.yml is loaded straight from the jar and is never copied
 * to the data folder, so lobby presets are dev-time only.
 */
public final class DevConfigRegistrar {

    private final JavaPlugin plugin;
    private DevConfig devConfig;

    public DevConfigRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        this.devConfig = ConfigManager.create(DevConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withRemoveOrphans(true);
        });
        reload();
    }

    public DevConfig getDevConfig() {
        return devConfig;
    }

    public void reload() {
        if (this.devConfig == null) {
            return;
        }
        try (InputStream bundled = plugin.getResource("Core/dev.yml")) {
            if (bundled == null) {
                throw new IOException("bundled Core/dev.yml is missing");
            }
            this.devConfig.load(bundled);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load the bundled dev data.", exception);
        }
    }
}
