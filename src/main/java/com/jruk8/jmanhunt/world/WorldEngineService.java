package com.jruk8.jmanhunt.world;

import com.jruk8.jmanhunt.lobby.LobbyConfig;
import com.jruk8.jmanhunt.lobby.LobbyWorld;
import com.jruk8.jmanhunt.lobby.LobbyWorldManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.world.end.EndCellManager;
import com.jruk8.jmanhunt.world.end.EndResetManager;
import com.jruk8.jmanhunt.world.structure.NetherStructuresDatapackManager;
import com.jruk8.jmanhunt.world.structure.OverworldStructuresDatapackManager;
import com.jruk8.jmanhunt.world.structure.StrongholdDatapackManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.config.SettingsListener;
import com.jruk8.jmanhunt.core.JManhuntPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

public final class WorldEngineService implements SettingsListener, LobbyTeleporter {
    private static final int MAX_CELL_ALLOCATE_ATTEMPTS = 20;

    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final ConfigService configService;
    private final WorldCellAllocator cellAllocator;
    private final StrongholdDatapackManager strongholdDatapackManager;
    private final NetherStructuresDatapackManager netherStructuresDatapackManager;
    private final OverworldStructuresDatapackManager overworldStructuresDatapackManager;
    private final EndResetManager endResetManager;

    // Tracks the start-border state so it can be expanded when the game begins.
    private boolean startBorderActive;
    private World startBorderWorld;
    private CellOrigin startBorderOrigin;
    private WorldEngineConfig startBorderConfig;
    private BukkitTask startBorderTask;

    // Names of the worlds whose borders this match touched, so they can be
    // restored to vanilla defaults even when the engine or the border itself
    // is disabled afterwards.
    private final List<String> borderedWorldNames = new ArrayList<>();

    // Ready cells kept ahead of match starts so matches never wait on
    // allocation or pregeneration.
    private final Deque<CellOrigin> cellBuffer = new ArrayDeque<>();
    private BukkitTask refillRetryTask;
    private BooleanSupplier matchRunning = () -> false;
    private final EndCellManager endCells;
    private final LobbyWorldManager lobbyWorlds;

    public WorldEngineService(JManhuntPlugin plugin, MessageService messages, ConfigService configService,
            EngineStateRepository engineState) {
        this.plugin = plugin;
        this.messages = messages;
        this.configService = configService;
        this.cellAllocator = new WorldCellAllocator(engineState);
        this.strongholdDatapackManager = new StrongholdDatapackManager(plugin);
        this.netherStructuresDatapackManager = new NetherStructuresDatapackManager(plugin);
        this.overworldStructuresDatapackManager = new OverworldStructuresDatapackManager(plugin);
        this.endResetManager = new EndResetManager(plugin);
        this.endCells = new EndCellManager(plugin, engineState);
        this.lobbyWorlds = new LobbyWorldManager(plugin);
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
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled() || participants.isEmpty()) return OptionalLong.empty();
        World world = Bukkit.getWorld(config.worldName());
        if (world == null) return OptionalLong.empty();

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
            setWorldBorder(world, config, origin);
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
        if (!plugin.getConfig().getBoolean("settings.roles.turn-nones-spectator.enabled", false)) {
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
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled()) {
            return;
        }
        World world = Bukkit.getWorld(config.worldName());
        if (world == null) {
            return;
        }
        CellCoordinate grid = SpiralCoordinateMapper.toCoordinate(cellIndex);
        int originX = toBlockCoordinate(grid.x() * config.cellSize());
        int originZ = toBlockCoordinate(grid.z() * config.cellSize());
        Location cellRoot = new Location(world, originX + 0.5,
                world.getHighestBlockYAt(originX, originZ, HeightMap.MOTION_BLOCKING) + 1,
                originZ + 0.5);
        for (Player player : joiners) {
            Location spawn = randomSpawnInCell(world, originX, originZ, config.tpSpreadRadius(),
                    player.getLocation().getYaw(), player.getLocation().getPitch());
            player.teleport(spawn);
            player.setRespawnLocation(cellRoot, true);
        }
    }

    /**
     * Tops up the ready-cell buffer. Called when a match ends and when an
     * autostart countdown begins; a periodic task covers matches started
     * while others run.
     */
    public void prepareNextCell() {
        refillBuffer();
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
    private void refillBuffer() {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled()) return;
        if (BufferRefillPolicy.parse(plugin.getConfig()
                .getString("world-engine.preloading.cell-buffer.increment-when", "ALWAYS"))
                == BufferRefillPolicy.NO_MATCH_RUNNING && matchRunning.getAsBoolean()) {
            return;
        }
        int target = bufferTarget();
        while (cellBuffer.size() < target) {
            World world = Bukkit.getWorld(config.worldName());
            if (world == null) return;
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
        return Math.max(1, plugin.getConfig()
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

    /**
     * Called when the game actually begins (via speedrunner damage or force start).
     * If the start-border is active, expands it to the full cell size.
     */
    public void onBeginGame() {
        if (!startBorderActive || startBorderWorld == null || startBorderConfig == null) return;

        if (startBorderConfig.skipFadeout()) {
            // Snap to cell size immediately, no animation.
            applyCellBorderSize(startBorderWorld, startBorderConfig, startBorderOrigin);
        } else {
            // Animate the expansion over the configured fadeout time.
            int fadeoutSeconds = startBorderConfig.startBorderFadeoutTime();
            applyCellBorderSize(startBorderWorld, startBorderConfig, startBorderOrigin, fadeoutSeconds);
        }

        startBorderActive = false;
    }

    public void onMatchEnd(List<Player> participants, List<Player> spectators, int lobbyId, long matchId) {
        clearInstanceBorders();
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled()) return;

        List<Player> returning = new ArrayList<>(participants);
        returning.addAll(spectators);
        Location lobby = resolveLobbyTeleport(lobbyId, returning);
        if (lobby == null) return;

        endCells.endWorldFor(matchId).ifPresentOrElse(endWorld -> {
            // Everyone in the dedicated end rides to the lobby, including
            // players outside the participant lists.
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(endWorld)) {
                    player.teleport(lobby);
                    player.setRespawnLocation(lobby, true);
                }
            }
            endCells.release(matchId);
        }, () -> endResetManager.reset(config, lobby));

        for (Player player : participants) {
            player.teleport(lobby);
            player.setRespawnLocation(lobby, true);
        }
        // Spectators placed at the cell center at match start return to the
        // lobby with everyone else. When NONE spectator handling is disabled
        // they were never moved, so they are left alone.
        if (plugin.getConfig().getBoolean("settings.roles.turn-nones-spectator.enabled", false)) {
            for (Player spectator : spectators) {
                spectator.teleport(lobby);
                spectator.setRespawnLocation(lobby, true);
            }
        }
        clearWorldBorder(lobby.getWorld());
    }

    /** Clears real borders and start-border state, e.g. when matches go concurrent. */
    public void clearInstanceBorders() {
        resetTrackedBorders();
        if (startBorderTask != null) {
            startBorderTask.cancel();
            startBorderTask = null;
        }
        startBorderActive = false;
    }

    /**
     * Applies the real world border for one cell, e.g. when concurrency
     * drops back to a single match. Honors the start-border phase for
     * matches that have not begun yet.
     */
    public void applyInstanceBorder(long cellIndex, boolean begun) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled() || !config.worldBorderEnabled()) {
            return;
        }
        World world = Bukkit.getWorld(config.worldName());
        if (world == null) {
            return;
        }
        CellCoordinate grid = SpiralCoordinateMapper.toCoordinate(cellIndex);
        CellOrigin origin = new CellOrigin(
                toBlockCoordinate(grid.x() * config.cellSize()),
                toBlockCoordinate(grid.z() * config.cellSize()),
                cellIndex);
        if (begun) {
            trackBorderedWorlds(world);
            applyCellBorderSize(world, config, origin);
        } else {
            setWorldBorder(world, config, origin);
        }
    }

    @Override
    public boolean teleportToLobby(List<Player> targets, int lobbyId) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled()) return false;

        Location lobby = resolveLobbyTeleport(lobbyId, targets);
        if (lobby == null) {
            return false;
        }
        for (Entity entity : targets) {
            entity.teleport(lobby);
        }
        return true;
    }

    @Override
    public boolean setSpawnToLobby(List<Player> targets, int lobbyId) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled()) return false;

        Location lobby = resolveLobbyTeleport(lobbyId, targets);
        if (lobby == null) return false;

        for (Player player : targets) {
            player.setRespawnLocation(lobby, true);
        }
        return true;
    }

    /**
     * True when newcomers have a lobby to wait in: the engine is on and
     * the lobby (or a fallback lobby) has a valid teleport.
     */
    public boolean hasLobbyLocation(int lobbyId) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        return config.enabled() && resolveLobby(lobbyId, false) != null;
    }

    /**
     * Resolves a lobby teleport: the lobby's own lobbytp in the lobby
     * world, else the lowest lobby id with a valid lobbytp as a
     * fallback (debug-logged). A missing or unloaded lobby world
     * resolves like a missing lobby entirely.
     */
    private Location resolveLobby(int lobbyId, boolean logFallback) {
        World lobbyWorld = Bukkit.getWorld(lobbyWorlds.lobbyWorldName());
        Map<Integer, LobbyConfig.LobbyTp> tps = lobbyTps();
        LobbyConfig.LobbyTp own = tps.get(lobbyId);
        if (lobbyWorld != null && own != null) {
            return toLobbyLocation(lobbyWorld, own);
        }
        if (lobbyWorld == null || tps.isEmpty()) {
            return null;
        }
        int fallback = tps.keySet().stream().min(Integer::compare).orElseThrow();
        if (logFallback) {
            plugin.logger().debug("debug.lobby-fallback", Map.of(
                    "lobby", String.valueOf(lobbyId), "fallback", String.valueOf(fallback)));
        }
        return toLobbyLocation(lobbyWorld, tps.get(fallback));
    }

    /**
     * resolveLobby plus the nothing-anywhere announcement: when no
     * lobbytp exists anywhere (or the lobby world is missing), every
     * target is told that no lobby exists and to contact an
     * administrator, and the miss is debug-logged.
     */
    private Location resolveLobbyTeleport(int lobbyId, List<Player> targets) {
        Location lobby = resolveLobby(lobbyId, true);
        if (lobby != null) {
            return lobby;
        }
        plugin.logger().debug("debug.lobby-missing", Map.of("lobby", String.valueOf(lobbyId)));
        for (Player target : targets) {
            messages.message(target, "manhunt.lobby-no-location-anywhere",
                    Map.of("lobby", String.valueOf(lobbyId)));
        }
        return null;
    }

    /** Valid lobbytps keyed by lobby id: integer keys with a stored lobbytp. */
    private Map<Integer, LobbyConfig.LobbyTp> lobbyTps() {
        Map<Integer, LobbyConfig.LobbyTp> tps = new HashMap<>();
        LobbyConfig lobbyConfig = plugin.lobbyConfig();
        if (lobbyConfig == null || lobbyConfig.getLobbies() == null) {
            return tps;
        }
        for (Map.Entry<String, LobbyConfig.LobbyEntry> entry : lobbyConfig.getLobbies().entrySet()) {
            int id;
            try {
                id = Integer.parseInt(entry.getKey().trim());
            } catch (NumberFormatException expected) {
                continue;
            }
            if (id < 0 || entry.getValue() == null || entry.getValue().getLobbytp() == null) {
                continue;
            }
            tps.put(id, entry.getValue().getLobbytp());
        }
        return tps;
    }

    /** Lobbytp coordinates as a location in the given lobby world. */
    private static Location toLobbyLocation(World lobbyWorld, LobbyConfig.LobbyTp point) {
        return new Location(lobbyWorld, point.getX(), point.getY(), point.getZ(),
                point.getYaw(), point.getPitch());
    }

    @Override
    public void onStart() {
        refreshDatapacks();
        refillBuffer();
        // Periodic top-up for matches consumed while others run. Plugin
        // tasks are cancelled automatically on disable.
        Bukkit.getScheduler().runTaskTimer(plugin, this::refillBuffer, 1200L, 1200L);
    }

    /** Dedicated end world of a live match, if it has one. */
    public Optional<World> matchEndWorld(long matchId) {
        return endCells.endWorldFor(matchId);
    }

    /** Configured lobby world name. */
    public String lobbyWorldName() {
        return lobbyWorlds.lobbyWorldName();
    }

    /** True when the lobby world is loaded or has a folder waiting. */
    public boolean lobbyWorldExists() {
        return lobbyWorlds.lobbyWorldExists();
    }

    /**
     * Arms or confirms lobby-world generation for one sender key. True only
     * on a matching second call within the timeout.
     */
    public boolean confirmLobbyGeneration(String senderKey) {
        return lobbyWorlds.confirmGeneration(senderKey, lobbyWorlds.lobbyWorldName());
    }

    /** Loads or generates the lobby world. Empty when creation fails. */
    public Optional<LobbyWorld> ensureLobbyWorld() {
        return lobbyWorlds.ensureLobbyWorld();
    }

    /**
     * True when void rescue applies in a world: the lobby world, never the
     * game world, even if an admin points both names at the same world.
     */
    public boolean rescuesVoidIn(World world) {
        if (world == null) {
            return false;
        }
        String name = world.getName();
        return name.equals(lobbyWorldName())
                && !name.equals(plugin.getConfig().getString("world-engine.world-name", "world"));
    }

    /**
     * Rescue destination for a void fall in the lobby world: the member
     * lobby's location when it sits in the lobby world, else lobby 0's
     * when it does, else the lobby world's own spawn. Never a game-world
     * location. Empty only when the lobby world is not loaded.
     */
    public Optional<Location> lobbyRescueLocation(OptionalInt memberLobby) {
        String lobbyWorld = lobbyWorlds.lobbyWorldName();
        World world = Bukkit.getWorld(lobbyWorld);
        Map<Integer, Location> locations = new HashMap<>();
        if (world != null) {
            for (Map.Entry<Integer, LobbyConfig.LobbyTp> entry : lobbyTps().entrySet()) {
                locations.put(entry.getKey(), toLobbyLocation(world, entry.getValue()));
            }
        }
        Optional<Location> configured = LobbyWorldManager.inLobbyWorld(
                LobbyWorldManager.selectRescueLocation(locations, memberLobby), lobbyWorld);
        if (configured.isPresent()) {
            return configured;
        }
        if (world != null) {
            return Optional.of(world.getSpawnLocation());
        }
        return Optional.empty();
    }

    /**
     * Startup sweep for orphaned end dimensions: reservations left by
     * restarts or crashes plus stray folders from older versions.
     */
    public void deleteOrphanedEndCells() {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        int deleted = endCells.deleteOrphans(config.worldName());
        if (deleted > 0) {
            plugin.logger().info("Deleted " + deleted + " orphaned end dimension(s).");
        }
    }

    /** True when lobby-world-name collides with the game world name. */
    public boolean lobbyWorldNameClashes() {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        return LobbyWorldManager.namesClash(lobbyWorlds.lobbyWorldName(), config.worldName());
    }

    /**
     * Warns when lobby-world-name matches the game world name, in which
     * case lobby world loading stays refused until it is renamed. True
     * when clean. Runs on enable and reload.
     */
    public boolean validateLobbyWorldName() {
        if (!lobbyWorldNameClashes()) {
            return true;
        }
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        plugin.logger().warning("world-engine.lobby-world-name '" + lobbyWorlds.lobbyWorldName()
                + "' matches the game world '" + config.worldName()
                + "'. Lobby world loading stays disabled until it is renamed.");
        return false;
    }

    /** Surface center of a match cell, for end-exit routing. */
    public Optional<Location> cellRoot(long cellIndex) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled()) {
            return Optional.empty();
        }
        World world = Bukkit.getWorld(config.worldName());
        if (world == null) {
            return Optional.empty();
        }
        CellCoordinate grid = SpiralCoordinateMapper.toCoordinate(cellIndex);
        int originX = toBlockCoordinate(grid.x() * config.cellSize());
        int originZ = toBlockCoordinate(grid.z() * config.cellSize());
        return Optional.of(new Location(world, originX + 0.5,
                world.getHighestBlockYAt(originX, originZ, HeightMap.MOTION_BLOCKING) + 1,
                originZ + 0.5));
    }

    @Override
    public void onReload() {
        refreshDatapacks();
    }

    /**
     * Applies or removes every datapack from its own flag. Structure boosts
     * are independent of the world engine: each pack follows only its own
     * enabled state.
     */
    private void refreshDatapacks() {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        boolean worldEnabled = configService.getBoolean("world-engine.enabled", false);
        strongholdDatapackManager.apply(config.worldName(), worldEnabled);
        if (!worldEnabled) {
            strongholdDatapackManager.remove(config.worldName(), false);
        }
        boolean netherEnabled = configService.getBoolean("settings.game-boosts.nether-structures.enabled", false);
        netherStructuresDatapackManager.apply(config.worldName(), netherEnabled);
        if (!netherEnabled) {
            netherStructuresDatapackManager.remove(config.worldName(), false);
        }
        boolean overworldEnabled = configService.getBoolean("settings.game-boosts.overworld-structures.enabled", false);
        overworldStructuresDatapackManager.apply(config.worldName(), overworldEnabled);
        if (!overworldEnabled) {
            overworldStructuresDatapackManager.remove(config.worldName(), false);
        }
    }

    @Override
    public String getDataPath() {
        return "settings/world-engine/strongholds.json";
    }

    private record CellOrigin(int x, int z, long index) {}

    /**
     * Highest usable cell index for the given cell size. The grid spans
     * 59,900,000 blocks per axis, so the cap is that span in cells, squared.
     */
    static long maxCellIndex(int cellSize) {
        long span = 59_900_000L / Math.max(1, cellSize);
        return span * span;
    }

    public static long clampCellIndex(long value, long max) {
        return Math.clamp(value, 0L, max);
    }

    /** Current cell index cap for the live configuration. */
    public long cellIndexCap() {
        return maxCellIndex(WorldEngineConfig.fromConfig(plugin.getConfig()).cellSize());
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
            if (startIndex.isEmpty()) return Optional.empty();
            long baseIndex = startIndex.getAsLong();

            CellCoordinate cell = SpiralCoordinateMapper.toCoordinate(baseIndex);
            int originX = toBlockCoordinate(cell.x() * config.cellSize());
            int originZ = toBlockCoordinate(cell.z() * config.cellSize());

            boolean useAlgo = configService.getBoolean("world-engine.use-spawnpoint-algorithm", true);
            if (useAlgo) {
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
        for (Player player : participants) {
            Location spawn = randomSpawnInCell(world, origin.x(), origin.z(), config.tpSpreadRadius(),
                    player.getLocation().getYaw(), player.getLocation().getPitch());
            player.teleport(spawn);
            player.setRespawnLocation(cellRoot, true);
        }

        endCells.ensureEndCell(config, origin.index(), matchId);

        if (applyBorder) {
            setWorldBorder(world, config, origin);
        }
        return cellRoot;
    }

    /**
     * Sets the world border for the overworld and its corresponding Nether world.
     * If start-border is active, sets the initial smaller border; otherwise sets
     * the full cell size border directly.
     */
    private void setWorldBorder(World overworld, WorldEngineConfig config, CellOrigin origin) {
        if (!config.worldBorderEnabled()) {
            // A border from an earlier match must not survive being disabled.
            resetTrackedBorders();
            return;
        }
        trackBorderedWorlds(overworld);

        // Check if start-border should be used (requires start-on-speedrunner-damage enabled).
        boolean startOnDamage = configService.getBoolean("settings.start-on-speedrunner-damage.enabled", false);
        boolean useStartBorder = config.startBorderActive() && startOnDamage;

        if (useStartBorder) {
            // Set the initial smaller start border.
            int startDiameter = config.startBorderDiameter();
            WorldBorder border = overworld.getWorldBorder();
            border.setCenter(origin.x(), origin.z());
            border.setSize(startDiameter);
            border.setWarningDistance(warningDistance(startDiameter));
            border.setDamageBuffer(config.damageBuffer());
            border.setDamageAmount(config.damageAmount());

            World nether = getNetherWorld(overworld);
            if (nether != null) {
                WorldBorder netherBorder = nether.getWorldBorder();
                netherBorder.setCenter(origin.x() / 8.0, origin.z() / 8.0);
                netherBorder.setSize(startDiameter / 8.0);
                netherBorder.setWarningDistance(warningDistance(startDiameter / 8.0));
                netherBorder.setDamageBuffer(config.damageBuffer());
                netherBorder.setDamageAmount(config.damageAmount());
            } else {
                plugin.logger().warning("Could not find matching Nether world for '"
                        + overworld.getName() + "'. Skipping Nether world border sync.");
            }

            // Store state for onBeginGame() to expand the border later.
            startBorderActive = true;
            startBorderWorld = overworld;
            startBorderOrigin = origin;
            startBorderConfig = config;
        } else {
            // Set the full cell size border directly.
            applyCellBorderSize(overworld, config, origin);
        }
    }

    /**
     * Applies the full cell size border to the overworld and Nether.
     */
    private void applyCellBorderSize(World overworld, WorldEngineConfig config, CellOrigin origin) {
        WorldBorder border = overworld.getWorldBorder();
        border.setCenter(origin.x(), origin.z());
        border.setSize(config.cellSize());
        border.setWarningDistance(warningDistance(config.cellSize()));
        border.setDamageBuffer(config.damageBuffer());
        border.setDamageAmount(config.damageAmount());

        World nether = getNetherWorld(overworld);
        if (nether == null) {
            plugin.logger().warning("Could not find matching Nether world for '"
                    + overworld.getName() + "'. Skipping Nether world border sync.");
            return;
        }

        WorldBorder netherBorder = nether.getWorldBorder();
        netherBorder.setCenter(origin.x() / 8.0, origin.z() / 8.0);
        netherBorder.setSize(config.cellSize() / 8.0);
        netherBorder.setWarningDistance(warningDistance(config.cellSize() / 8.0));
        netherBorder.setDamageBuffer(config.damageBuffer());
        netherBorder.setDamageAmount(config.damageAmount());
    }

    /**
     * Border warning distance for an applied border size: a hundredth of
     * the size, at least one block. Each world scales by its own applied
     * size, so the Nether (1/8 scale) warns the same effective distance.
     * Pure for tests.
     */
    static int warningDistance(double borderSize) {
        return Math.max(1, (int) (borderSize / 100.0));
    }

    /**
     * Applies the full cell size border to the overworld and Nether with
     * an animated transition over the given duration in seconds.
     */
    @SuppressWarnings("removal") // setSize(double, long) is the only animated overload available
    private void applyCellBorderSize(World overworld, WorldEngineConfig config, CellOrigin origin, int seconds) {
        WorldBorder border = overworld.getWorldBorder();
        border.setCenter(origin.x(), origin.z());
        border.setSize(config.cellSize(), seconds);
        border.setWarningDistance(warningDistance(config.cellSize()));
        border.setDamageBuffer(config.damageBuffer());
        border.setDamageAmount(config.damageAmount());

        World nether = getNetherWorld(overworld);
        if (nether == null) {
            plugin.logger().warning("Could not find matching Nether world for '"
                    + overworld.getName() + "'. Skipping Nether world border sync.");
            return;
        }

        WorldBorder netherBorder = nether.getWorldBorder();
        netherBorder.setCenter(origin.x() / 8.0, origin.z() / 8.0);
        netherBorder.setSize(config.cellSize() / 8.0, seconds);
        netherBorder.setWarningDistance(warningDistance(config.cellSize() / 8.0));
        netherBorder.setDamageBuffer(config.damageBuffer());
        netherBorder.setDamageAmount(config.damageAmount());
    }

    private void clearWorldBorder(World world) {
        WorldBorder border = world.getWorldBorder();
        border.reset();
    }

    private void trackBorderedWorlds(World overworld) {
        trackBorderedWorld(overworld);
        World nether = getNetherWorld(overworld);
        if (nether != null) {
            trackBorderedWorld(nether);
        }
    }

    private void trackBorderedWorld(World world) {
        if (!borderedWorldNames.contains(world.getName())) {
            borderedWorldNames.add(world.getName());
        }
    }

    /**
     * Restores vanilla border defaults (center 0, 0, size 59999968) on every
     * world bordered during the match.
     */
    private void resetTrackedBorders() {
        for (String name : borderedWorldNames) {
            World world = Bukkit.getWorld(name);
            if (world != null) {
                world.getWorldBorder().reset();
            }
        }
        borderedWorldNames.clear();
    }

    private World getNetherWorld(World overworld) {
        if (overworld.getEnvironment() == World.Environment.NETHER) {
            return overworld; // already the Nether, guard against double-wrapping
        }
        return Bukkit.getWorld(overworld.getName() + "_nether");
    }



    private Location randomSpawnInCell(World world, int centerX, int centerZ, int radius, float yaw, float pitch) {
        int offsetX = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
        int offsetZ = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
        int x = centerX + offsetX;
        int z = centerZ + offsetZ;
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) + 1;
        return new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);
    }

    private int toBlockCoordinate(long value) {
        if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) value;
    }

    /**
     * Runs the configured preloading console commands, replacing
     * {@code <cellX>} and {@code <cellZ>} with the cell's block coordinates.
     * Used to pre-generate the cell area with chunk-generation plugins such
     * as Chunky before players teleport in.
     */
    private void runPreloadingCommands(WorldEngineConfig config, CellOrigin origin) {
        List<String> commands = plugin.getConfig().getStringList("world-engine.preloading.commands");
        if (commands.isEmpty()) return;
        for (String command : commands) {
            if (command.isBlank()) continue;
            String parsed = command.replace("<cellX>", String.valueOf(origin.x()))
                    .replace("<cellZ>", String.valueOf(origin.z()))
                    .replace("<world>", config.worldName());
            if (parsed.startsWith("/")) parsed = parsed.substring(1);
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            } catch (Exception e) {
                plugin.logger().severe("Failed to run preloading command '%s'. Skipping..".formatted(command));
                e.printStackTrace();
            }
        }
    }
}
