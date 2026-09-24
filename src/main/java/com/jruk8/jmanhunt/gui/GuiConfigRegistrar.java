package com.jruk8.jmanhunt.gui;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Creates and reloads the Okaeri GUI data store. The bundled
 * Core/gui.yml is loaded straight from the jar and is never copied
 * to the data folder, so category icons and descriptions are not
 * editable by end users.
 */
public final class GuiConfigRegistrar {

    private final JavaPlugin plugin;
    private GuiConfig guiConfig;

    public GuiConfigRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        this.guiConfig = ConfigManager.create(GuiConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withRemoveOrphans(true);
        });
        reload();
    }

    public GuiConfig getGuiConfig() {
        return guiConfig;
    }

    public void reload() {
        if (this.guiConfig == null) {
            return;
        }
        try (InputStream bundled = plugin.getResource("Core/gui.yml")) {
            if (bundled == null) {
                throw new IOException("bundled Core/gui.yml is missing");
            }
            this.guiConfig.load(bundled);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load the bundled GUI data.", exception);
        }
    }
}
