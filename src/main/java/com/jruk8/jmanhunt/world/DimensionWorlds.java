package com.jruk8.jmanhunt.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared get/create/exists system for creator-made worlds (the lobby
 * world, pooled end dimensions). Paper 26+ stores them under the main
 * level's dimensions dir instead of the container root, so every lookup
 * checks both layouts. File cores are pure for tests; only the final
 * load/create calls touch Bukkit.
 */
public final class DimensionWorlds {
    /** Dimensions root segment under a parent world. */
    public static final String DIMENSIONS_SEGMENT = "dimensions/minecraft";

    private DimensionWorlds() {
    }

    /** Dimensions root for a parent world: container/parent/dimensions/minecraft. */
    public static File dimensionRoot(File container, String parentWorldName) {
        return new File(container, parentWorldName + "/" + DIMENSIONS_SEGMENT);
    }

    /** Folder names under the parent's dimensions root, or empty. */
    public static List<String> dimensionDirs(File container, String parentWorldName) {
        String[] entries = dimensionRoot(container, parentWorldName)
                .list((dir, name) -> new File(dir, name).isDirectory());
        return entries == null ? List.of() : Arrays.asList(entries);
    }

    /**
     * Locates an unloaded world's folder: container root first, then the
     * parent's dimensions dir. Returns the dimensions candidate when
     * neither exists (deleting it is a no-op).
     */
    public static File unloadedFolder(File container, String parentWorldName, String name) {
        File root = new File(container, name);
        if (root.isDirectory()) {
            return root;
        }
        return new File(dimensionRoot(container, parentWorldName), name);
    }

    /** True when the world's folder waits on disk in either layout. */
    public static boolean folderExists(File container, String parentWorldName, String name) {
        return unloadedFolder(container, parentWorldName, name).isDirectory();
    }

    /** True when the world is loaded or a folder waits in either layout. */
    public static boolean exists(File container, String parentWorldName, String name) {
        return Bukkit.getWorld(name) != null
                || folderExists(container, parentWorldName, name);
    }

    /**
     * The loaded world, or a fresh creation with the given customizer
     * (environment, seed, generator). Null when creation fails.
     */
    public static World loadOrCreate(String name, Consumer<WorldCreator> customize) {
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            return loaded;
        }
        WorldCreator creator = new WorldCreator(name);
        customize.accept(creator);
        return creator.createWorld();
    }
}
