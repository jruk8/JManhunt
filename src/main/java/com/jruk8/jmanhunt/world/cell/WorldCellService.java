package com.jruk8.jmanhunt.world.cell;

import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.world.end.EndCellManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.world.border.WorldBorderService;
import com.jruk8.jmanhunt.world.teleport.MatchTeleportService;

/** Match cells: allocation, buffering, index, and game teleports. */
public final class WorldCellService {
    private static final int MAX_CELL_ALLOCATE_ATTEMPTS = 20;

    private final JManhuntPlugin plugin;
    private final WorldCellAllocator cellAllocator;
    private final EndCellManager endCells;
    private final WorldBorderService borders;
    // Ready cells kept ahead of match starts so matches never wait on
    // allocation or pregeneration.
    private final Deque<CellOrigin> cellBuffer = new ArrayDeque<>();
    private BukkitTask refillRetryTask;
    private BooleanSupplier matchRunning = () -> false;

    public WorldCellService(JManhuntPlugin plugin, EngineStateRepository engineState,
            EndCellManager endCells, WorldBorderService borders) {
        this.plugin = plugin;
        this.cellAllocator = new WorldCellAllocator(engineState);
        this.endCells = endCells;
        this.borders = borders;
    }

    /** Wires the match-running check behind the NO_MATCH_RUNNING refill policy. */
    public void setMatchRunningSupplier(BooleanSupplier matchRunning) {
        this.matchRunning = matchRunning;
    }

    /**
     * Teleports participants to the next match cell. Returns the used cell
     * index, or empty when the engine is off, the world is missing, or no
     * valid cell could be allocated. The real border is only applied for a
     * lone match; concurrent matches use pseudo-borders instead.
     */
    public OptionalLong onMatchStart(List<Player> participants, List<Player> spectators,
            boolean applyBorder, int lobbyId, long matchId) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.configService());
        if (!config.enabled() || participants.isEmpty()) {
            return OptionalLong.empty();
        }
        World world = Bukkit.getWorld(config.worldName());
        if (world == null) {
            return OptionalLong.empty();
        }

        CellOrigin origin = cellBuffer.poll();
        if (origin == null) {
            try {
                origin = fetchCell(world, config)
                        .orElseThrow(Exception::new);
            } catch (Exception e) {
                plugin.logger().severe("Could not find a valid spawn cell for world-engine after "
                        + MAX_CELL_ALLOCATE_ATTEMPTS + " attempts. Skipping teleport.");
                return OptionalLong.empty();
            }
        }

        Location cellRoot = teleportToGame(participants, world, config, origin, lobbyId, applyBorder, matchId);
        teleportSpectatorsToCell(spectators, cellRoot);
        if (applyBorder) {
            borders.setWorldBorder(world, config, origin);
        }
        return OptionalLong.of(origin.index());
    }

    /**
     * Teleports NONE players to the match cell center so they can spectate
     * the match instead of waiting in the lobby. Skipped entirely when
     * spectator handling for NONE players is disabled.
     */
    private void teleportSpectatorsToCell(List<Player> spectators, Location cellRoot) {
        if (spectators.isEmpty() || cellRoot == null) {
            return;
        }
        // AFK players never reach this list; the toggle moves NONE and
        // spectator-role watchers together, and leaves them put when off.
        if (!plugin.configService().getBoolean("settings.players.roles.turn-nones-spectator.enabled", false)) {
            return;
        }
        for (Player spectator : spectators) {
            spectator.teleport(cellRoot);
            spectator.setRespawnLocation(cellRoot, true);
            spectator.setGameMode(GameMode.SPECTATOR);
        }
    }

    /**
     * Teleports mid-match joiners to random spawns inside a match cell and
     * pins their respawn to the cell center. No-op when the engine is off or
     * the world is missing.
     */
    public void teleportJoinersToCell(List<Player> joiners, long cellIndex) {
        if (joiners.isEmpty()) {
            return;
        }
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.configService());
        if (!config.enabled()) {
            return;
        }
        World world = Bukkit.getWorld(config.worldName());
        if (world == null) {
            return;
        }
        CellCoordinate grid = SpiralCoordinateMapper.toCoordinate(cellIndex);
        int originX = MatchTeleportService.toBlockCoordinate(grid.x() * config.cellSize());
        int originZ = MatchTeleportService.toBlockCoordinate(grid.z() * config.cellSize());
        Location cellRoot = new Location(world, originX + 0.5,
                world.getHighestBlockYAt(originX, originZ, HeightMap.MOTION_BLOCKING) + 1,
                originZ + 0.5);
        List<Location> spawns = MatchTeleportService.spreadSpawnsForConfig(world, originX, originZ,
                config.tpSpreadRadius(), joiners, config);
        for (int index = 0; index < joiners.size(); index++) {
            joiners.get(index).teleport(spawns.get(index));
            joiners.get(index).setRespawnLocation(cellRoot, true);
        }
    }

    /** Buffered cell indexes, oldest first. */
    public List<Long> bufferedCellIndexes() {
        return cellBuffer.stream().map(CellOrigin::index).toList();
    }

    /**
     * Fetches cells until the buffer is full, running the preloading
     * commands for each. Honors the NO_MATCH_RUNNING policy and retries
     * failed fetches after a delay instead of spinning.
     */
    public void refillBuffer() {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.configService());
        if (!config.enabled()) {
            return;
        }
        if (BufferRefillPolicy.parse(plugin.configService()
                .getString("world-engine.preloading.cell-buffer.increment-when", "ALWAYS"))
                == BufferRefillPolicy.NO_MATCH_RUNNING && matchRunning.getAsBoolean()) {
            return;
        }
        int target = bufferTarget();
        while (cellBuffer.size() < target) {
            World world = Bukkit.getWorld(config.worldName());
            if (world == null) {
                return;
            }
            Optional<CellOrigin> fetched = fetchCell(world, config);
            if (fetched.isEmpty()) {
                scheduleRefillRetry();
                return;
            }
            cellBuffer.add(fetched.get());
            runPreloadingCommands(config, fetched.get());
            plugin.logger().debug("debug.cell-buffer-add", Map.of(
                    "index", String.valueOf(fetched.get().index()),
                    "count", String.valueOf(cellBuffer.size()),
                    "target", String.valueOf(target)));
        }
    }

    /** Ready cells to keep on hand. Minimum 1. */
    private int bufferTarget() {
        return Math.max(1, plugin.configService()
                .getInt("world-engine.preloading.cell-buffer.stored-cells-buffer", 1));
    }

    /** Allocates one valid cell, logging it for debug recipients. */
    private Optional<CellOrigin> fetchCell(World world, WorldEngineConfig config) {
        Optional<CellOrigin> origin = findValidOrigin(world, config);
        origin.ifPresent(cell -> plugin.logger().debug("debug.cell-fetched", Map.of(
                "index", String.valueOf(cell.index()),
                "x", String.valueOf(cell.x()),
                "z", String.valueOf(cell.z()))));
        return origin;
    }

    /** Retries a failed refill once after 30 seconds; concurrent retries never stack. */
    private void scheduleRefillRetry() {
        if (refillRetryTask != null) {
            return;
        }
        plugin.logger().debug("debug.cell-fetch-failed", Map.of("seconds", "30"));
        refillRetryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            refillRetryTask = null;
            refillBuffer();
        }, 600L);
    }

    /** Surface center of a match cell, for end-exit routing. */
    public Optional<Location> cellRoot(long cellIndex) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.configService());
        if (!config.enabled()) {
            return Optional.empty();
        }
        World world = Bukkit.getWorld(config.worldName());
        if (world == null) {
            return Optional.empty();
        }
        CellCoordinate grid = SpiralCoordinateMapper.toCoordinate(cellIndex);
        int originX = MatchTeleportService.toBlockCoordinate(grid.x() * config.cellSize());
        int originZ = MatchTeleportService.toBlockCoordinate(grid.z() * config.cellSize());
        return Optional.of(new Location(world, originX + 0.5,
                world.getHighestBlockYAt(originX, originZ, HeightMap.MOTION_BLOCKING) + 1,
                originZ + 0.5));
    }

    /**
     * Highest usable cell index for the given cell size. The grid spans
     * 59,900,000 blocks per axis, so the cap is that span in cells, squared.
     */
    public static long maxCellIndex(int cellSize) {
        long span = 59_900_000L / Math.max(1, cellSize);
        return span * span;
    }

    public static long clampCellIndex(long value, long max) {
        return Math.clamp(value, 0L, max);
    }

    /** Current cell index cap for the live configuration. */
    public long cellIndexCap() {
        return maxCellIndex(WorldEngineConfig.fromConfig(plugin.configService()).cellSize());
    }

    /** Current cell index, or empty when the engine store is unavailable. */
    public OptionalLong cellIndex() {
        return cellAllocator.currentStartIndex();
    }

    /** Overwrites the cell index. Returns false when the store is unavailable. */
    public boolean cellIndex(long value) {
        return cellAllocator.setStartIndex(value);
    }

    /**
     * Restarts the cell counter at zero once it grows past the addressable
     * grid so allocation never runs off the world. New cells may then
     * overlap old ones, hence the warning.
     */
    private void enforceCellIndexCap(WorldEngineConfig config) {
        OptionalLong current = cellAllocator.currentStartIndex();
        if (current.isPresent() && current.getAsLong() > maxCellIndex(config.cellSize())) {
            plugin.logger().warning("World-engine cell index " + current.getAsLong()
                    + " exceeds the addressable grid for cell size " + config.cellSize()
                    + ". Restarting the index at zero; the world should be manually reset "
                    + "because new cells may overlap old ones.");
            cellAllocator.setStartIndex(0L);
        }
    }

    private Optional<CellOrigin> findValidOrigin(World world, WorldEngineConfig config) {
        enforceCellIndexCap(config);
        for (int iter = 0; iter < MAX_CELL_ALLOCATE_ATTEMPTS; iter++) {
            OptionalLong startIndex = cellAllocator.reserveStartIndex(1);
            if (startIndex.isEmpty()) {
                return Optional.empty();
            }
            long baseIndex = startIndex.getAsLong();

            CellCoordinate cell = SpiralCoordinateMapper.toCoordinate(baseIndex);
            int originX = MatchTeleportService.toBlockCoordinate(cell.x() * config.cellSize());
            int originZ = MatchTeleportService.toBlockCoordinate(cell.z() * config.cellSize());

            if (config.spawnpointAlgorithmEnabled()) {
                Block centerBlock = world.getHighestBlockAt(originX, originZ);
                Material type = centerBlock.getType();
                boolean isLastAttempt = (iter == MAX_CELL_ALLOCATE_ATTEMPTS - 1);

                if ((type == Material.WATER || type == Material.LAVA || !centerBlock.isSolid()) && !isLastAttempt) {
                    continue; // Bad terrain, try again
                }

                if (isLastAttempt && (type == Material.WATER || type == Material.LAVA || !centerBlock.isSolid())) {
                    plugin.logger().warning("Could not find a valid spawn cell after "
                            + MAX_CELL_ALLOCATE_ATTEMPTS + " attempts. Using last attempted cell.");
                }
            }

            return Optional.of(new CellOrigin(originX, originZ, baseIndex));
        }
        return Optional.empty();
    }

    private Location teleportToGame(List<Player> participants, World world, WorldEngineConfig config,
                                    CellOrigin origin, int lobbyId, boolean applyBorder, long matchId) {
        // Use the cell root as the respawn location for all participants so
        // that deaths send them back to the cell center rather than the lobby.
        Location cellRoot = new Location(world, origin.x() + 0.5,
                world.getHighestBlockYAt(origin.x(), origin.z(), HeightMap.MOTION_BLOCKING) + 1,
                origin.z() + 0.5);
        List<Location> spawns = MatchTeleportService.spreadSpawnsForConfig(world, origin.x(), origin.z(),
                config.tpSpreadRadius(), participants, config);
        for (int index = 0; index < participants.size(); index++) {
            participants.get(index).teleport(spawns.get(index));
            participants.get(index).setRespawnLocation(cellRoot, true);
        }

        endCells.ensureEndCell(config, origin.index(), matchId);

        if (applyBorder) {
            borders.setWorldBorder(world, config, origin);
        }
        return cellRoot;
    }

    /**
     * Runs the configured preloading console commands, replacing
     * {@code <cellX>} and {@code <cellZ>} with the cell's block coordinates.
     * Used to pre-generate the cell area with chunk-generation plugins such
     * as Chunky before players teleport in.
     */
    private void runPreloadingCommands(WorldEngineConfig config, CellOrigin origin) {
        List<String> commands = plugin.configService().getStringList("world-engine.preloading.commands");
        if (commands.isEmpty()) {
            return;
        }
        for (String command : commands) {
            if (command.isBlank()) {
                continue;
            }
            String parsed = command.replace("<cellX>", String.valueOf(origin.x()))
                    .replace("<cellZ>", String.valueOf(origin.z()))
                    .replace("<world>", config.worldName());
            if (parsed.startsWith("/")) {
                parsed = parsed.substring(1);
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            } catch (Exception e) {
                plugin.logger().severe("Failed to run preloading command '%s'. Skipping..".formatted(command));
                e.printStackTrace();
            }
        }
    }
}
