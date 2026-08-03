package com.jruk8.jmanhunt.settings.world_engine;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Base class for datapacks that override vanilla structure sets. */
public abstract class DatapackManager {
    protected final JavaPlugin plugin;

    protected DatapackManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void apply(String worldName, boolean enabled) {
        if (!enabled) return;
        File worldFolder = new File(plugin.getServer().getWorldContainer(), worldName);
        File datapackRoot = new File(worldFolder, "datapacks/" + datapackFolderName());
        File structureSet = new File(datapackRoot, structureSetPath());
        File mcMeta = new File(datapackRoot, "pack.mcmeta");
        try {
            if (structureSet.getParentFile() != null) structureSet.getParentFile().mkdirs();
            writeIfChanged(mcMeta, packMeta());
            plugin.saveResource(resourcePath(), false);
            File source = new File(plugin.getDataFolder(), resourcePath());
            String content = Files.readString(source.toPath(), StandardCharsets.UTF_8);
            boolean changed = writeIfChanged(structureSet, content);
            if (changed) reloadDataPacks();
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to apply " + datapackFolderName() + " datapack: " + exception.getMessage());
        }
    }

    public void remove(String worldName, boolean enabled) {
        if (enabled) {
            plugin.getLogger().warning("Attempted remove datapack with feature enabled. " +
                    "This message should not happen. Contact an admin.");
            return;
        }
        File worldFolder = new File(plugin.getServer().getWorldContainer(), worldName);
        File datapackRoot = new File(worldFolder, "datapacks/" + datapackFolderName());
        if (datapackRoot.exists()) {
            try {
                FileUtils.deleteRecursively(datapackRoot);
                reloadDataPacks();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to delete datapack folder: " + e.getMessage());
            }
        }
    }

    protected abstract String datapackFolderName();
    protected abstract String structureSetPath();
    protected abstract String resourcePath();
    protected abstract String packDescription();

    private boolean writeIfChanged(File target, String content) throws IOException {
        if (target.exists()) {
            String existing = Files.readString(target.toPath(), StandardCharsets.UTF_8);
            if (existing.equals(content)) return false;
        } else if (target.getParentFile() != null) {
            target.getParentFile().mkdirs();
        }
        Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
        return true;
    }

    private void reloadDataPacks() {
        plugin.getServer().reloadData();
    }

    private String packMeta() {
        return """
                {
                  "pack": {
                    "description": "%s",
                    "min_format": 83,
                    "max_format": 255
                  }
                }
                """.formatted(packDescription());
    }
}