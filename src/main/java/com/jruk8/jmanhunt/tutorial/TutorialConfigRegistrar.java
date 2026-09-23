package com.jruk8.jmanhunt.tutorial;

import com.jruk8.jmanhunt.tutorial.config.TutorialConfig;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Creates and reloads the Okaeri tutorial store. The bundled
 * Core/tutorial.yml is loaded straight from the jar and is never
 * copied to the data folder, so the setup dialogue is not editable
 * by end users.
 */
public final class TutorialConfigRegistrar {

    private final JavaPlugin plugin;
    private TutorialConfig tutorialConfig;

    public TutorialConfigRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        File stale = new File(plugin.getDataFolder(), "Core/tutorial.yml");
        if (stale.isFile()) {
            plugin.getLogger().warning("Ignoring " + stale.getPath()
                    + ": the setup dialogue is bundled and no longer editable.");
        }
        this.tutorialConfig = ConfigManager.create(TutorialConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
            it.withRemoveOrphans(true);
        });
        reload();
    }

    public TutorialConfig getTutorialConfig() {
        return tutorialConfig;
    }

    public void reload() {
        if (this.tutorialConfig == null) {
            return;
        }
        try (InputStream bundled = plugin.getResource("Core/tutorial.yml")) {
            if (bundled == null) {
                throw new IOException("bundled Core/tutorial.yml is missing");
            }
            this.tutorialConfig.load(bundled);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load the bundled setup dialogue.", exception);
        }
    }
}
