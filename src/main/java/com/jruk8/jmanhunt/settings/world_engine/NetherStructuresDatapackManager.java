package com.jruk8.jmanhunt.settings.world_engine;

import org.bukkit.plugin.java.JavaPlugin;

/** Applies the world-engine nether structures (fortress/bastion) datapack. */
public final class NetherStructuresDatapackManager extends DatapackManager {
    public NetherStructuresDatapackManager(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String datapackFolderName() {
        return "jmanhunt-nether-structures";
    }

    @Override
    protected String structureSetPath() {
        return "data/minecraft/worldgen/structure_set/nether_structures.json";
    }

    @Override
    protected String resourcePath() {
        return "settings/world-engine/nether-structures.json";
    }

    @Override
    protected String packDescription() {
        return "JManhunt world-engine nether structure placement";
    }
}