package com.jruk8.jmanhunt.gui;

import com.jruk8.jmanhunt.config.SectionPinner;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import lombok.Getter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Function;

/**
 * Creates and reloads the Okaeri GUI data store. The bundled
 * Core/gui.yml is loaded straight from the jar and is never copied
 * to the data folder, so category icons and descriptions are not
 * editable by end users.
 */
public final class GuiConfigRegistrar {

    private final Function<String, InputStream> resources;
    private final SectionPinner sections = new SectionPinner();
    @Getter
    private GuiConfig guiConfig;

    public GuiConfigRegistrar(Function<String, InputStream> resources) {
        this.resources = resources;
    }

    public void register() {
        this.guiConfig = ConfigManager.create(GuiConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withRemoveOrphans(true);
        });
        reload();
    }

    public void reload() {
        if (this.guiConfig == null) {
            return;
        }
        try (InputStream bundled = resources.apply("Core/gui.yml")) {
            if (bundled == null) {
                throw new IOException("bundled Core/gui.yml is missing");
            }
            this.guiConfig.load(bundled);
            sections.pin(this.guiConfig);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load the bundled GUI data.", exception);
        }
    }
}
