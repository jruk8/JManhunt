package com.jruk8.jmanhunt.world.end;

import com.jruk8.jmanhunt.config.EngineStateRepository;
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
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Owns per-match end dimensions. Each match cell gets its own end world so
 * concurrent matches never share a dragon fight; reservations map live
 * matches to their dimension, persist across restarts, and are deleted
 * with their world when the match ends.
 */
public final class EndCellManager {
    private final JManhuntPlugin plugin;
    private final EngineStateRepository engineState;
    /** Live reservations, match id to end world name. */
    private final Map<Long, String> reservations = new HashMap<>();

    public EndCellManager(JManhuntPlugin plugin, EngineStateRepository engineState) {
        this.plugin = plugin;
        this.engineState = engineState;
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
        persistReservation(matchId, name);
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

    /**
     * Releases a match reservation, deleting its end dimension. The
     * caller evacuates players first. A failed delete keeps the
     * reservation so the startup sweep retries it. True when a
     * reservation existed.
     */
    public boolean release(long matchId) {
        String name = reservations.get(matchId);
        if (name == null) {
            return false;
        }
        if (!deleteEndWorld(name, null)) {
            return true;
        }
        reservations.remove(matchId);
        dropReservation(matchId);
        return true;
    }

    /**
     * Deletes every tracked end reservation plus any stray
     * &lt;base&gt;_the_end_* world folders. No match survives a
     * restart, so every reservation is an orphan; strays are leftovers
     * from older versions. The shared &lt;base&gt;_the_end stays
     * untouched: the prefix keeps its trailing underscore. Returns the
     * number deleted.
     */
    public int deleteOrphans(String baseWorldName) {
        Map<Long, String> rows = loadReservations();
        Set<String> targets = new TreeSet<>(rows.values());
        File container = plugin.getServer().getWorldContainer();
        String[] entries = container.list((dir, name) -> new File(dir, name).isDirectory());
        if (entries != null) {
            targets.addAll(strayEndWorlds(Arrays.asList(entries), baseWorldName + "_the_end_"));
        }
        int deleted = 0;
        for (String name : targets) {
            if (!deleteEndWorld(name, null)) {
                continue;
            }
            deleted++;
            for (Map.Entry<Long, String> row : rows.entrySet()) {
                if (row.getValue().equals(name)) {
                    dropReservation(row.getKey());
                }
            }
            reservations.values().removeIf(name::equals);
        }
        return deleted;
    }

    /**
     * Container entries that are stray end worlds for the prefix,
     * sorted. Pure for tests.
     */
    static List<String> strayEndWorlds(List<String> dirNames, String prefix) {
        return dirNames.stream().filter(name -> name.startsWith(prefix)).sorted().toList();
    }

    /**
     * Unloads and deletes one end world, riding stragglers to the
     * fallback first. True when the directory is gone.
     */
    private boolean deleteEndWorld(String name, Location fallback) {
        World loaded = Bukkit.getWorld(name);
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
            FileUtils.deleteRecursively(new File(plugin.getServer().getWorldContainer(), name));
        } catch (IOException exception) {
            plugin.logger().warning("Failed to delete end dimension " + name + ": " + exception.getMessage());
            return false;
        }
        plugin.logger().debug("debug.end-cell-pruned", Map.of("cell", name));
        return true;
    }

    private void persistReservation(long matchId, String name) {
        if (engineState == null) {
            return;
        }
        try {
            engineState.putEndReservation(matchId, name);
        } catch (SQLException exception) {
            plugin.logger().warning("Could not persist end reservation for match " + matchId
                    + ": " + exception.getMessage());
        }
    }

    private void dropReservation(long matchId) {
        if (engineState == null) {
            return;
        }
        try {
            engineState.removeEndReservation(matchId);
        } catch (SQLException exception) {
            plugin.logger().warning("Could not drop end reservation for match " + matchId
                    + ": " + exception.getMessage());
        }
    }

    private Map<Long, String> loadReservations() {
        if (engineState == null) {
            return Map.of();
        }
        try {
            return engineState.endReservations();
        } catch (SQLException exception) {
            plugin.logger().warning("Could not load end reservations; sweeping stray folders only: "
                    + exception.getMessage());
            return Map.of();
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
