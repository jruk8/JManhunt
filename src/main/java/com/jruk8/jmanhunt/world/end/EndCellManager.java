package com.jruk8.jmanhunt.world.end;

import com.jruk8.jmanhunt.world.FileUtils;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.core.JManhuntPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Owns per-match end dimensions. Each match cell gets its own end world so
 * concurrent matches never share a dragon fight; reservations map live
 * matches to their dimension and extras are pruned back to the buffer.
 */
public final class EndCellManager {
    private final JManhuntPlugin plugin;
    /** Live reservations, match id to end world name. */
    private final Map<Long, String> reservations = new HashMap<>();

    public EndCellManager(JManhuntPlugin plugin) {
        this.plugin = plugin;
    }

    /** Dedicated end world name for a match cell. The trailing underscore keeps it distinct from the shared end. */
    public static String endCellName(String baseWorldName, long cellIndex) {
        return baseWorldName + "_the_end_" + cellIndex;
    }

    /**
     * Ensures a dedicated end for a match cell: created fresh, or wiped and
     * recreated when the cell is reused. Returns its world name.
     */
    public String ensureEndCell(WorldEngineConfig config, long cellIndex, long matchId) {
        String name = endCellName(config.worldName(), cellIndex);
        reservations.put(matchId, name);
        plugin.logger().debug("debug.end-cell-reserved",
                Map.of("cell", name, "id", String.valueOf(matchId)));
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            resetEndWorld(existing, null);
            plugin.logger().debug("debug.end-cell-reset", Map.of("cell", name));
        } else {
            WorldCreator creator = new WorldCreator(name);
            creator.environment(World.Environment.THE_END);
            creator.createWorld();
            plugin.logger().debug("debug.end-cell-created", Map.of("cell", name));
        }
        return name;
    }

    /** Dedicated end world of a live match, if it has one loaded. */
    public Optional<World> endWorldFor(long matchId) {
        String name = reservations.get(matchId);
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(Bukkit.getWorld(name));
    }

    /** Releases a match reservation. True when one existed. */
    public boolean release(long matchId) {
        return reservations.remove(matchId) != null;
    }

    /**
     * Prunes one unreserved end-cell dimension beyond the buffer, oldest
     * first. Stragglers still inside ride to the fallback location. Restart
     * leftovers are pruned from disk the same way.
     */
    public void pruneExtras(String baseWorldName, int buffer, Location fallback) {
        String prefix = baseWorldName + "_the_end_";
        File container = plugin.getServer().getWorldContainer();
        String[] entries = container.list((dir, name) -> name.startsWith(prefix) && new File(dir, name).isDirectory());
        if (entries == null) {
            return;
        }
        Optional<String> victim = selectPruneCandidate(Arrays.asList(entries), prefix,
                new HashSet<>(reservations.values()), buffer);
        if (victim.isEmpty()) {
            return;
        }
        World loaded = Bukkit.getWorld(victim.get());
        if (loaded != null) {
            if (fallback != null) {
                for (Player player : loaded.getPlayers()) {
                    player.teleport(fallback);
                }
            }
            EndWorlds.clearDragonBar(plugin, loaded);
            for (Chunk chunk : loaded.getLoadedChunks()) {
                chunk.unload();
            }
            Bukkit.unloadWorld(loaded, false);
        }
        try {
            FileUtils.deleteRecursively(new File(container, victim.get()));
        } catch (IOException exception) {
            plugin.logger().warning("Failed to prune end dimension " + victim.get() + ": " + exception.getMessage());
            return;
        }
        plugin.logger().debug("debug.end-cell-pruned", Map.of("cell", victim.get()));
    }

    /**
     * Oldest unreserved end-cell directory beyond the buffer, if any.
     * Unparsable suffixes sort last so numbered cells prune first.
     */
    static Optional<String> selectPruneCandidate(List<String> dirNames, String prefix, Set<String> reserved, int buffer) {
        List<String> owned = dirNames.stream()
                .filter(name -> name.startsWith(prefix) && !reserved.contains(name))
                .sorted(Comparator.comparingLong((String name) -> cellSuffix(name, prefix))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        if (owned.size() <= buffer) {
            return Optional.empty();
        }
        return Optional.of(owned.get(0));
    }

    private static long cellSuffix(String name, String prefix) {
        try {
            return Long.parseLong(name.substring(prefix.length()));
        } catch (NumberFormatException | IndexOutOfBoundsException exception) {
            return Long.MAX_VALUE;
        }
    }

    /** Wipes and recreates one dedicated end world in place. */
    private void resetEndWorld(World world, Location evacuateTo) {
        if (evacuateTo != null) {
            for (Player player : world.getPlayers()) {
                player.teleport(evacuateTo);
            }
        }
        EndWorlds.clearDragonBar(plugin, world);
        for (Chunk chunk : world.getLoadedChunks()) {
            chunk.unload();
        }
        String name = world.getName();
        if (!Bukkit.unloadWorld(world, true)) {
            plugin.logger().warning("Could not unload end world " + name + " for reset.");
            return;
        }
        try {
            FileUtils.deleteRecursively(new File(plugin.getServer().getWorldContainer(), name));
        } catch (IOException exception) {
            plugin.logger().warning("Failed to clean end data at " + name + ": " + exception.getMessage());
            return;
        }
        WorldCreator creator = new WorldCreator(name);
        creator.environment(World.Environment.THE_END);
        creator.createWorld();
    }
}
