package com.jruk8.jmanhunt.settings.world_engine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class EndResetManager {
    private final JavaPlugin plugin;

    public EndResetManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reset(WorldEngineConfig config, Location lobbyLocation) {
        String endWorldName = config.worldName() + "_the_end";
        World endWorld = Bukkit.getWorld(endWorldName);
        if (endWorld == null) {
            return;
        }

        if (lobbyLocation != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(endWorld)) {
                    player.teleport(lobbyLocation);
                }
            }
        }
        // The vanilla dragon health bar is tracked per player and is not
        // always cleared when its world is unloaded and recreated, so remove
        // every viewer explicitly. Otherwise the stale bar stays on screen
        // for anyone who was in the End during the reset.
        clearDragonBar(endWorld);
        for (org.bukkit.Chunk chunk : endWorld.getLoadedChunks()) {
            chunk.unload();
        }

        if (!Bukkit.unloadWorld(endWorld, true)) {
            plugin.getLogger().warning("Could not unload end world " + endWorldName + " for reset.");
            return;
        }

        deleteFolder(new File(plugin.getServer().getWorldContainer(), config.worldName() + "/DIM1"));
        deleteFolder(new File(plugin.getServer().getWorldContainer(),
                config.worldName() + "/dimensions/minecraft/the_end"));

        WorldCreator creator = new WorldCreator(endWorldName);
        creator.environment(World.Environment.THE_END);
        creator.createWorld();
    }

    private void clearDragonBar(World endWorld) {
        try {
            var battle = endWorld.getEnderDragonBattle();
            if (battle == null) {
                return;
            }
            var bar = battle.getBossBar();
            if (bar == null) {
                return;
            }
            bar.removeAll();
        } catch (UnsupportedOperationException exception) {
            plugin.getLogger().fine("Could not clear dragon bar in " + endWorld.getName() + ": " + exception.getMessage());
        }
    }

    private void deleteFolder(File folder) {
        if (!folder.exists()) {
            return;
        }
        try {
            List<Path> paths = Files.walk(folder.toPath()).sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to clean end data at " + folder + ": " + exception.getMessage());
        }
    }
}
