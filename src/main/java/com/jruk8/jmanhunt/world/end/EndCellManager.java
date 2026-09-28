package com.jruk8.jmanhunt.world.end;

import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.world.DimensionWorlds;
import com.jruk8.jmanhunt.world.FileUtils;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.JManhuntPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Owns the shared pool of reusable end dimensions. Pool members live as
 * {@code <base>_<n>} worlds in the world container (classic layout) or the
 * main level's dimensions dir (Paper 26+ creator layout); loaded worlds
 * count regardless of layout. A free buffer of them
 * is kept ready, and matches are assigned the lowest free member when a
 * player first enters an end portal. Used members reset with a fresh
 * deterministic seed when their match ends and rejoin the free buffer.
 */
public final class EndCellManager {
    /**
     * Overflow ceiling multiplier: at most buffer times this many free
     * dimensions are kept before the buffer event trims the highest-n
     * member. Trimming at exactly the buffer size would churn disk with
     * constant generation and deletion; five times the buffer means the
     * pool almost never runs dry while spikes still get reclaimed.
     */
    static final int OVERFLOW_MULTIPLIER = 5;

    private final JManhuntPlugin plugin;
    private final EngineStateRepository engineState;
    /** Live assignments, match id to pool world name. */
    private final Map<Long, String> reservations = new HashMap<>();

    public EndCellManager(JManhuntPlugin plugin, EngineStateRepository engineState) {
        this.plugin = plugin;
        this.engineState = engineState;
    }

    /** Pool member world name: the base plus a hardcoded underscore and number. */
    public static String poolName(String baseName, long n) {
        return baseName + "_" + n;
    }

    /** Next pool number: highest existing plus 1, or 1 when the pool is empty. Pure for tests. */
    static long nextN(Set<Long> existing) {
        return existing.stream().mapToLong(Long::longValue).max().orElse(0L) + 1;
    }

    /** Lowest pool number with no live assignment, if any. Pure for tests. */
    static OptionalLong lowestFree(Set<Long> existing, Set<Long> assigned) {
        return existing.stream().mapToLong(Long::longValue).filter(n -> !assigned.contains(n)).min();
    }

    /** True when the free count dropped below the configured buffer. Pure for tests. */
    static boolean needsTopUp(int freeCount, int buffer) {
        return freeCount < buffer;
    }

    /** True when the free count exceeds the overflow ceiling. Pure for tests. */
    static boolean exceedsOverflow(int freeCount, int buffer) {
        return freeCount > (long) buffer * OVERFLOW_MULTIPLIER;
    }

    /** Highest free pool number: the overflow trim victim. Pure for tests. */
    static OptionalLong overflowVictim(Set<Long> free) {
        return free.stream().mapToLong(Long::longValue).max();
    }

    /**
     * Pool number of a world-container directory, if it matches
     * {@code <base>_<positive integer>}. Anything else is a stray that
     * the pool ignores. Pure for tests.
     */
    static OptionalLong parsePoolNumber(String dirName, String baseName) {
        String prefix = baseName + "_";
        if (!dirName.startsWith(prefix)) {
            return OptionalLong.empty();
        }
        try {
            long n = Long.parseLong(dirName.substring(prefix.length()));
            return n >= 1 ? OptionalLong.of(n) : OptionalLong.empty();
        } catch (NumberFormatException invalid) {
            return OptionalLong.empty();
        }
    }

    /**
     * Stray end world folders, sorted: every legacy
     * {@code <world>_the_end_*} folder plus pool-prefixed folders that are
     * not {@code <base>_<positive integer>}. Numeric pool members and the
     * shared {@code <world>_the_end} are spared. Pure for tests.
     */
    static List<String> strayEndWorlds(List<String> dirNames, String legacyPrefix, String poolBase) {
        return dirNames.stream()
                .filter(name -> name.startsWith(legacyPrefix) || isPoolStray(name, poolBase))
                .sorted()
                .toList();
    }

    private static boolean isPoolStray(String dirName, String poolBase) {
        return dirName.startsWith(poolBase + "_") && parsePoolNumber(dirName, poolBase).isEmpty();
    }

    private static int freeCount(Set<Long> existing, Set<Long> assigned) {
        int count = 0;
        for (long n : existing) {
            if (!assigned.contains(n)) {
                count++;
            }
        }
        return count;
    }

    /** Assigned end world of a live match, if it has one loaded. */
    public Optional<World> endWorldFor(long matchId) {
        String name = reservations.get(matchId);
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(Bukkit.getWorld(name));
    }

    /**
     * Assigns the lowest free pool member to a match on first end-portal
     * entry, loading it, and records it for the whole match from that
     * point on. Matches with an assignment get their member back without
     * touching the pool. When taking the member drops the free count
     * below the buffer, one replacement is generated during the same
     * portal jump. Empty when the overworld is missing or generation
     * fails; the caller then leaves the portal alone.
     */
    public Optional<World> assign(WorldEngineConfig config, long matchId) {
        String current = reservations.get(matchId);
        if (current != null) {
            return Optional.ofNullable(loadDimension(current));
        }
        Map<Long, String> pool = scanPool(poolDirs(config), config.endBaseName());
        Set<Long> assigned = assignedNumbers(config.endBaseName());
        if (lowestFree(pool.keySet(), assigned).isEmpty()) {
            // Pool exhausted: generate one on demand rather than failing
            // the portal jump. The buffer event normally prevents this.
            if (generateNext(config, pool).isEmpty()) {
                return Optional.empty();
            }
        }
        long chosen = lowestFree(pool.keySet(), assigned).orElseThrow();
        String name = pool.get(chosen);
        reservations.put(matchId, name);
        persistReservation(matchId, name);
        plugin.logger().debug("debug.end-cell-reserved",
                Map.of("cell", name, "id", String.valueOf(matchId)));
        World world = loadDimension(name);
        if (world == null) {
            return Optional.empty();
        }
        assigned.add(chosen);
        if (needsTopUp(freeCount(pool.keySet(), assigned), config.endBuffer())) {
            generateNext(config, pool);
        }
        return Optional.of(world);
    }

    /**
     * Releases a match assignment, resetting its dimension with a fresh
     * deterministic seed so it rejoins the free buffer. The caller
     * evacuates players first. A failed reset keeps the reservation so
     * the startup sweep retries it. True when a reservation existed.
     */
    public boolean release(long matchId) {
        String name = reservations.get(matchId);
        if (name == null) {
            return false;
        }
        World world = Bukkit.getWorld(name);
        if (world == null) {
            world = loadDimension(name);
        }
        if (world == null) {
            reservations.remove(matchId);
            dropReservation(matchId);
            return true;
        }
        if (!resetEndWorld(world, null, EndSeedHasher.resetSeed(world.getSeed()))) {
            return true;
        }
        reservations.remove(matchId);
        dropReservation(matchId);
        plugin.logger().debug("debug.end-cell-reset", Map.of("cell", name));
        return true;
    }

    /**
     * Buffer event upkeep: generates exactly one member when the free
     * count is below the buffer, then deletes exactly one member (the
     * highest free number) when it exceeds the overflow ceiling.
     * Assigned members are never trim candidates. Runs inside the cell
     * buffer refill so one event drives both buffers.
     */
    public void maintainBuffer(WorldEngineConfig config) {
        File container = worldContainer();
        List<String> entries = poolDirs(config);
        Map<Long, String> pool = scanPool(entries, config.endBaseName());
        Set<Long> assigned = assignedNumbers(config.endBaseName());
        Set<Long> free = new TreeSet<>(pool.keySet());
        free.removeAll(assigned);
        plugin.logger().debug("debug.end-pool-scan", Map.of(
                "container", container.getAbsolutePath(),
                "entries", String.valueOf(entries.size()),
                "pool", describe(pool.keySet()),
                "loaded", describe(loadedPoolWorlds(config.endBaseName())),
                "free", String.valueOf(free.size()),
                "buffer", String.valueOf(config.endBuffer()),
                "assigned", describe(assigned)));
        if (needsTopUp(free.size(), config.endBuffer())) {
            topUp(config, pool, free);
        }
        if (exceedsOverflow(free.size(), config.endBuffer())) {
            trimOverflow(config, pool, free);
        }
    }

    /**
     * Deletes every tracked assignment plus stray end world folders. No
     * match survives a restart, so every assignment is an orphan; strays
     * are legacy {@code <world>_the_end_*} folders (removed, never
     * converted) and non-numeric pool-prefixed folders. Numeric pool
     * members stay: they are the free buffer, not orphans. The shared
     * {@code <world>_the_end} stays untouched. Returns the number
     * deleted.
     */
    public int deleteOrphans(WorldEngineConfig config) {
        Map<Long, String> rows = loadReservations();
        Set<String> targets = new TreeSet<>(rows.values());
        targets.addAll(strayEndWorlds(poolDirs(config),
                config.worldName() + "_the_end_", config.endBaseName()));
        int deleted = 0;
        for (String name : targets) {
            if (!deleteEndWorld(config, name, null)) {
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

    /** Generates the next pool member (highest existing plus 1). Empty when skipped or failed. */
    private OptionalLong generateNext(WorldEngineConfig config, Map<Long, String> pool) {
        OptionalLong seed = overworldSeed(config);
        if (seed.isEmpty()) {
            return OptionalLong.empty();
        }
        long n = nextN(pool.keySet());
        plugin.logger().debug("debug.end-cell-topup", Map.of(
                "cell", poolName(config.endBaseName(), n),
                "pool", describe(pool.keySet())));
        if (generateDimension(config, n, seed.getAsLong()) == null) {
            return OptionalLong.empty();
        }
        pool.put(n, poolName(config.endBaseName(), n));
        return OptionalLong.of(n);
    }

    /** Generates one member and counts it as free. */
    private void topUp(WorldEngineConfig config, Map<Long, String> pool, Set<Long> free) {
        generateNext(config, pool).ifPresent(free::add);
    }

    /** Deletes the highest free member, skipping it when occupied. */
    private void trimOverflow(WorldEngineConfig config, Map<Long, String> pool, Set<Long> free) {
        OptionalLong victim = overflowVictim(free);
        if (victim.isEmpty()) {
            return;
        }
        String name = pool.get(victim.getAsLong());
        World loaded = Bukkit.getWorld(name);
        if (loaded != null && !loaded.getPlayers().isEmpty()) {
            return;
        }
        if (deleteEndWorld(config, name, null)) {
            free.remove(victim.getAsLong());
        }
    }

    /** Pool folders by number, strays ignored. */
    private Map<Long, String> scanPool(List<String> dirNames, String baseName) {
        Map<Long, String> pool = new TreeMap<>();
        for (String dir : dirNames) {
            OptionalLong n = parsePoolNumber(dir, baseName);
            if (n.isPresent()) {
                pool.putIfAbsent(n.getAsLong(), dir);
            }
        }
        return pool;
    }

    /** Pool numbers behind the live assignments. */
    private Set<Long> assignedNumbers(String baseName) {
        Set<Long> assigned = new TreeSet<>();
        for (String name : reservations.values()) {
            parsePoolNumber(name, baseName).ifPresent(assigned::add);
        }
        return assigned;
    }

    private File worldContainer() {
        return plugin.getServer().getWorldContainer();
    }

    private List<String> containerDirs() {
        String[] entries = worldContainer().list((dir, name) -> new File(dir, name).isDirectory());
        return entries == null ? List.of() : Arrays.asList(entries);
    }

    /**
     * Everywhere pool members can live: container-root folders (classic
     * layout), loaded worlds (authoritative regardless of layout), and
     * the main level's dimensions dir (Paper 26+ creator layout).
     */
    private List<String> poolDirs(WorldEngineConfig config) {
        List<String> dirs = new ArrayList<>(containerDirs());
        dirs.addAll(loadedPoolWorlds(config.endBaseName()));
        dirs.addAll(DimensionWorlds.dimensionDirs(worldContainer(), config.worldName()));
        return dirs;
    }

    /** Sorted comma list for debug lines, or (empty). Pure for tests. */
    static String describe(Collection<?> values) {
        if (values.isEmpty()) {
            return "(empty)";
        }
        return String.join(", ", values.stream().map(String::valueOf).sorted().toList());
    }

    /** Loaded worlds under the pool prefix, sorted. */
    private static List<String> loadedPoolWorlds(String baseName) {
        String prefix = baseName + "_";
        return Bukkit.getWorlds().stream()
                .map(World::getName)
                .filter(name -> name.startsWith(prefix))
                .sorted()
                .toList();
    }

    /** Loads a pool member, or returns it when already loaded. */
    private World loadDimension(String name) {
        return DimensionWorlds.loadOrCreate(name,
                creator -> creator.environment(World.Environment.THE_END));
    }

    /** Generates a fresh pool member with its deterministic seed. Null when creation fails. */
    private World generateDimension(WorldEngineConfig config, long n, long seed) {
        String name = poolName(config.endBaseName(), n);
        boolean loaded = Bukkit.getWorld(name) != null;
        boolean folder = loaded
                || DimensionWorlds.unloadedFolder(worldContainer(), config.worldName(), name).isDirectory();
        plugin.logger().debug("debug.end-cell-create-attempt", Map.of(
                "cell", name,
                "loaded", String.valueOf(loaded),
                "folder", String.valueOf(folder)));
        World created = DimensionWorlds.loadOrCreate(name, creator -> {
            creator.environment(World.Environment.THE_END);
            creator.seed(EndSeedHasher.initialSeed(seed, n));
        });
        if (created == null) {
            plugin.logger().warning("Could not generate end dimension " + name + ".");
            return null;
        }
        plugin.logger().debug("debug.end-cell-created", Map.of("cell", name));
        return created;
    }

    private OptionalLong overworldSeed(WorldEngineConfig config) {
        World overworld = Bukkit.getWorld(config.worldName());
        return overworld == null ? OptionalLong.empty() : OptionalLong.of(overworld.getSeed());
    }

    /**
     * Unloads and deletes one end world, riding stragglers to the
     * fallback first. True when the directory is gone.
     */
    private boolean deleteEndWorld(WorldEngineConfig config, String name, Location fallback) {
        World loaded = Bukkit.getWorld(name);
        File liveFolder = loaded == null ? null : loaded.getWorldFolder();
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
        File target = liveFolder != null ? liveFolder
                : DimensionWorlds.unloadedFolder(worldContainer(), config.worldName(), name);
        try {
            FileUtils.deleteRecursively(target);
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

    /**
     * Wipes and recreates one pool member in place with a new seed. True
     * when the member is back and loadable.
     */
    private boolean resetEndWorld(World world, Location evacuateTo, long newSeed) {
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
        File folder = world.getWorldFolder();
        if (!Bukkit.unloadWorld(world, true)) {
            plugin.logger().warning("Could not unload end world " + name + " for reset.");
            return false;
        }
        try {
            FileUtils.deleteRecursively(folder);
        } catch (IOException exception) {
            plugin.logger().warning("Failed to clean end data at " + name + ": " + exception.getMessage());
            return false;
        }
        World recreated = DimensionWorlds.loadOrCreate(name, creator -> {
            creator.environment(World.Environment.THE_END);
            creator.seed(newSeed);
        });
        if (recreated == null) {
            plugin.logger().warning("Could not recreate end world " + name + " after reset.");
            return false;
        }
        return true;
    }
}
