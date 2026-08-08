package com.jruk8.jmanhunt.settings.world_engine;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applies the world-engine overworld structures datapack that boosts the
 * spawn frequency of villages, shipwrecks, buried treasure, dungeons and
 * ruined portals. Each vanilla structure set is overridden with tighter
 * spacing/separation so more structures generate in the overworld.
 */
public final class OverworldStructuresDatapackManager extends DatapackManager {
    public OverworldStructuresDatapackManager(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String datapackFolderName() {
        return "jmanhunt-overworld-structures";
    }

    @Override
    protected String structureSetPath() {
        // Unused; overridden by structureSetFiles() below.
        return "data/minecraft/worldgen/structure_set/villages.json";
    }

    @Override
    protected String resourcePath() {
        // Unused; overridden by structureSetFiles() below.
        return "settings/world-engine/overworld-structures/villages.json";
    }

    @Override
    protected String packDescription() {
        return "JManhunt world-engine overworld structure placement";
    }

    @Override
    protected Map<String, String> structureSetFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        String base = "settings/world-engine/overworld-structures/";
        files.put("data/minecraft/worldgen/structure_set/villages.json", base + "villages.json");
        files.put("data/minecraft/worldgen/structure_set/shipwrecks.json", base + "shipwrecks.json");
        files.put("data/minecraft/worldgen/structure_set/buried_treasures.json", base + "buried-treasures.json");
        files.put("data/minecraft/worldgen/structure_set/ruined_portals.json", base + "ruined-portals.json");
        return files;
    }
}