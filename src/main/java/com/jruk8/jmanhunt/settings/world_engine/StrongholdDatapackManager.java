package com.jruk8.jmanhunt.settings.world_engine;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class StrongholdDatapackManager {
    private final JavaPlugin plugin;

    public StrongholdDatapackManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void apply(WorldEngineConfig config) {
        if (!config.enabled()) return;
        File worldFolder = new File(plugin.getServer().getWorldContainer(), config.worldName());
        File datapackRoot = new File(worldFolder, "datapacks/jmanhunt-world-engine");
        File structureSet = new File(datapackRoot,
                "data/minecraft/worldgen/structure_set/strongholds.json");
        File mcMeta = new File(datapackRoot, "pack.mcmeta");
        try {
            if (structureSet.getParentFile() != null) structureSet.getParentFile().mkdirs();
            writeIfChanged(mcMeta, packMeta());
            plugin.saveResource("settings/world-engine/strongholds.json", false);
            File source = new File(plugin.getDataFolder(), "settings/world-engine/strongholds.json");
            String content = Files.readString(source.toPath(), StandardCharsets.UTF_8);
            boolean changed = writeIfChanged(structureSet, content);
            if (changed) {
                reloadDataPacks();
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to apply world-engine stronghold datapack: " + exception.getMessage());
        }
    }

    public void remove(WorldEngineConfig config) {
        if (config.enabled()) {
            plugin.getLogger().warning("Attempted remove datapack with world engine enabled. " +
                    "This message should not happen. Contact an admin.");
            return;
        }
        File worldFolder = new File(plugin.getServer().getWorldContainer(), config.worldName());
        File datapackRoot = new File(worldFolder, "datapacks/jmanhunt-world-engine");
        if (datapackRoot.exists()) {
            try {
                FileUtils.deleteRecursively(datapackRoot);
                reloadDataPacks();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to delete datapack folder: " + e.getMessage());
            }
        }
    }

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
                    "description": "JManhunt world-engine stronghold placement",
                    "min_format": 83,
                    "max_format": 255
                  }
                }
                """;
    }
}
