package com.jruk8.jmanhunt.world.structure;

import com.jruk8.jmanhunt.core.JManhuntLogger;
import org.bukkit.Server;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Applies the world-engine stronghold placement datapack. */
public final class StrongholdDatapackManager extends DatapackManager {
    public StrongholdDatapackManager(Server server, Path dataFolder, JManhuntLogger log,
            Consumer<String> saveResource) {
        super(server, dataFolder, log, saveResource);
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