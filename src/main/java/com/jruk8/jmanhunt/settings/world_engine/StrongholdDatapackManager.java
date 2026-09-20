package com.jruk8.jmanhunt.settings.world_engine;

import com.jruk8.jmanhunt.JManhuntPlugin;

/** Applies the world-engine stronghold placement datapack. */
public final class StrongholdDatapackManager extends DatapackManager {
    public StrongholdDatapackManager(JManhuntPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String datapackFolderName() {
        return "jmanhunt-world-engine";
    }

    @Override
    protected String structureSetPath() {
        return "data/minecraft/worldgen/structure_set/strongholds.json";
    }

    @Override
    protected String resourcePath() {
        return "settings/world-engine/strongholds.json";
    }

    @Override
    protected String packDescription() {
        return "JManhunt world-engine stronghold placement";
    }
}