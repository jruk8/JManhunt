package com.jruk8.jmanhunt.world.structure;

import com.jruk8.jmanhunt.core.JManhuntLogger;
import org.bukkit.Server;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Applies the world-engine nether structures (fortress/bastion) datapack. */
public final class NetherStructuresDatapackManager extends DatapackManager {
    public NetherStructuresDatapackManager(Server server, Path dataFolder, JManhuntLogger log,
            Consumer<String> saveResource) {
        super(server, dataFolder, log, saveResource);
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