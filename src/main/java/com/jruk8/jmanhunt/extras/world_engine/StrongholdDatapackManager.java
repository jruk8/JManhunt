package com.jruk8.jmanhunt.extras.world_engine;

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
            plugin.saveResource("extras/world-engine/strongholds.json", false);
            File source = new File(plugin.getDataFolder(), "extras/world-engine/strongholds.json");
            String content = Files.readString(source.toPath(), StandardCharsets.UTF_8);
            writeIfChanged(structureSet, content);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to apply world-engine stronghold datapack: " + exception.getMessage());
        }
    }

    private void writeIfChanged(File target, String content) throws IOException {
        if (target.exists()) {
            String existing = Files.readString(target.toPath(), StandardCharsets.UTF_8);
            if (existing.equals(content)) return;
        } else if (target.getParentFile() != null) {
            target.getParentFile().mkdirs();
        }
        Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
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
