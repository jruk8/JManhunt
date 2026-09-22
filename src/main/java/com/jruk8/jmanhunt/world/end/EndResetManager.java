package com.jruk8.jmanhunt.world.end;

import com.jruk8.jmanhunt.world.FileUtils;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.JManhuntPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import java.io.File;
import java.io.IOException;

public final class EndResetManager {
    private final JManhuntPlugin plugin;

    public EndResetManager(JManhuntPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Wipes and recreates the shared end dimension. Only used for matches
     * without a dedicated end cell; cell matches get their own dimension
     * from {@link EndCellManager} instead.
     */
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
        EndWorlds.clearDragonBar(plugin, endWorld);
        for (org.bukkit.Chunk chunk : endWorld.getLoadedChunks()) {
            chunk.unload();
        }

        if (!Bukkit.unloadWorld(endWorld, true)) {
            plugin.logger().warning("Could not unload end world " + endWorldName + " for reset.");
            return;
        }

        deleteEndData(config.worldName());
        WorldCreator creator = new WorldCreator(endWorldName);
        creator.environment(World.Environment.THE_END);
        creator.createWorld();
    }

    private void deleteEndData(String baseWorldName) {
        File container = plugin.getServer().getWorldContainer();
        for (String relative : new String[]{baseWorldName + "/DIM1", baseWorldName + "/dimensions/minecraft/the_end"}) {
            try {
                FileUtils.deleteRecursively(new File(container, relative));
            } catch (IOException exception) {
                plugin.logger().warning("Failed to clean end data at " + relative + ": " + exception.getMessage());
            }
        }
    }
}
