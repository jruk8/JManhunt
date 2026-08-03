package com.jruk8.jmanhunt.settings.world_engine;

import com.jruk8.jmanhunt.ConfigService;
import com.jruk8.jmanhunt.LobbyTeleporter;
import com.jruk8.jmanhunt.StatsRepository;
import com.jruk8.jmanhunt.settings.SettingsListener;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ThreadLocalRandom;

public final class WorldEngineService implements SettingsListener, LobbyTeleporter {
    private static final int MAX_CELL_ALLOCATE_ATTEMPTS = 20;

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final WorldCellAllocator cellAllocator;
    private final StrongholdDatapackManager strongholdDatapackManager;
    private final NetherStructuresDatapackManager netherStructuresDatapackManager;
    private final EndResetManager endResetManager;

    // Tracks the start-border state so it can be expanded when the game begins.
    private boolean startBorderActive;
    private World startBorderWorld;
    private CellOrigin startBorderOrigin;
    private WorldEngineConfig startBorderConfig;
    private BukkitTask startBorderTask;

    public WorldEngineService(JavaPlugin plugin, ConfigService configService, StatsRepository statsRepository) {
        this.plugin = plugin;
        this.configService = configService;
        this.cellAllocator = new WorldCellAllocator(statsRepository);
        this.strongholdDatapackManager = new StrongholdDatapackManager(plugin);
        this.netherStructuresDatapackManager = new NetherStructuresDatapackManager(plugin);
        this.endResetManager = new EndResetManager(plugin);
    }

    public void onMatchStart(List<Player> participants) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled() || participants.isEmpty()) return;
        World world = Bukkit.getWorld(config.worldName());
        if (world == null) return;

        CellOrigin origin;
        try {
            origin = findValidOrigin(world, config)
                    .orElseThrow(Exception::new);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not find a valid spawn cell for world-engine after "
                    + MAX_CELL_ALLOCATE_ATTEMPTS + " attempts. Skipping teleport.");
            return;
        }

        teleportToGame(participants, world, config, origin);
        setWorldBorder(world, config, origin);
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

    public void onMatchEnd(List<Player> participants) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled()) return;

        // Cancel any pending start-border task.
        if (startBorderTask != null) {
            startBorderTask.cancel();
            startBorderTask = null;
        }
        startBorderActive = false;

        Location lobby = getValidLobby(config);
        if (lobby == null) return;

        for (Player player : participants) {
            player.teleport(lobby);
            player.setRespawnLocation(lobby, true);
        }
        endResetManager.reset(config, lobby);
        clearWorldBorder(lobby.getWorld());
    }

    public boolean teleportToLobby(List<Player> targets) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled()) return false;

        Location lobby = getValidLobby(config);
        if (lobby != null) {
            for (Entity entity : targets) {
                entity.teleport(lobby);
            }
        }
        else {
            plugin.getLogger().warning("Skipping lobby teleport because lobby world was not found.");
            return false;
        }
        // set spawnpoints
        return true;
    }

    @Override
    public boolean setSpawnToLobby(List<Player> targets) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled()) return false;

        Location lobby = getValidLobby(config);
        if (lobby == null) return false;

        for (Player player : targets) {
            player.setRespawnLocation(lobby, true);
        }
        return true;
    }

    private Location getValidLobby(WorldEngineConfig config) {
        Location lobby = resolveLobby(config);
        if (lobby == null || lobby.getWorld() == null) {
            plugin.getLogger().warning("Skipping world-engine lobby teleport because lobby world was not found.");
            return null;
        }
        return lobby;
    }

    @Override
    public void onStart() {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        boolean isDisabled = !configService.getBoolean("settings.world-engine.enabled", false);
        if (isDisabled) {
            strongholdDatapackManager.remove(config.worldName(), false);
            netherStructuresDatapackManager.remove(config.worldName(), false);
            return;
        }
        strongholdDatapackManager.apply(config.worldName(), true);
        boolean netherEnabled = configService.getBoolean("settings.world-engine.nether-structures.enabled", false);
        netherStructuresDatapackManager.apply(config.worldName(), netherEnabled);
    }

    @Override
    public void onReload() {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        boolean worldEnabled = config.enabled();
        strongholdDatapackManager.apply(config.worldName(), worldEnabled);
        if (!worldEnabled) {
            strongholdDatapackManager.remove(config.worldName(), false);
            netherStructuresDatapackManager.remove(config.worldName(), false);
            return;
        }
        boolean netherEnabled = configService.getBoolean("settings.world-engine.nether-structures.enabled", false);
        netherStructuresDatapackManager.apply(config.worldName(), netherEnabled);
        if (!netherEnabled) {
            netherStructuresDatapackManager.remove(config.worldName(), false);
        }
    }

    @Override
    public String getDataPath() {
        return "world-engine/strongholds.json";
    }

    private record CellOrigin(int x, int z) {}

    private Optional<CellOrigin> findValidOrigin(World world, WorldEngineConfig config) {
        for (int iter = 0; iter < MAX_CELL_ALLOCATE_ATTEMPTS; iter++) {
            OptionalLong startIndex = cellAllocator.reserveStartIndex(1);
            if (startIndex.isEmpty()) return Optional.empty();
            long baseIndex = startIndex.getAsLong();

            SpiralCoordinateMapper.CellCoordinate cell = SpiralCoordinateMapper.toCoordinate(baseIndex);
            int originX = toBlockCoordinate(cell.x() * config.cellSize());
            int originZ = toBlockCoordinate(cell.z() * config.cellSize());

            boolean useAlgo = configService.getBoolean("settings.world-engine.use-spawnpoint-algorithm", true);
            if (useAlgo) {
                Block centerBlock = world.getHighestBlockAt(originX, originZ);
                Material type = centerBlock.getType();
                boolean isLastAttempt = (iter == MAX_CELL_ALLOCATE_ATTEMPTS - 1);

                if ((type == Material.WATER || type == Material.LAVA || !centerBlock.isSolid()) && !isLastAttempt) {
                    continue; // Bad terrain, try again
                }

                if (isLastAttempt && (type == Material.WATER || type == Material.LAVA || !centerBlock.isSolid())) {
                    plugin.getLogger().warning("Could not find a valid spawn cell after "
                            + MAX_CELL_ALLOCATE_ATTEMPTS + " attempts. Using last attempted cell.");
                }
            }

            return Optional.of(new CellOrigin(originX, originZ));
        }
        return Optional.empty();
    }

    private void teleportToGame(List<Player> participants, World world, WorldEngineConfig config, CellOrigin origin) {
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

        Location lobby = resolveLobby(config);
        endResetManager.reset(config, lobby);

        setWorldBorder(world, config, origin);
    }

    /**
     * Sets the world border for the overworld and its corresponding Nether world.
     * If start-border is active, sets the initial smaller border; otherwise sets
     * the full cell size border directly.
     */
    private void setWorldBorder(World overworld, WorldEngineConfig config, CellOrigin origin) {
        if (!config.worldBorderEnabled()) {
            return;
        }

        // Check if start-border should be used (requires start-on-speedrunner-damage enabled).
        boolean startOnDamage = configService.getBoolean("settings.start-on-speedrunner-damage.enabled", false);
        boolean useStartBorder = config.startBorderActive() && startOnDamage;

        if (useStartBorder) {
            // Set the initial smaller start border.
            int startDiameter = config.startBorderDiameter();
            WorldBorder border = overworld.getWorldBorder();
            border.setCenter(origin.x(), origin.z());
            border.setSize(startDiameter);
            border.setDamageBuffer(config.damageBuffer());
            border.setDamageAmount(config.damageAmount());

            World nether = getNetherWorld(overworld);
            if (nether != null) {
                WorldBorder netherBorder = nether.getWorldBorder();
                netherBorder.setCenter(origin.x() / 8.0, origin.z() / 8.0);
                netherBorder.setSize(startDiameter / 8.0);
                netherBorder.setDamageBuffer(config.damageBuffer());
                netherBorder.setDamageAmount(config.damageAmount());
            } else {
                plugin.getLogger().warning("Could not find matching Nether world for '"
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
        border.setDamageBuffer(config.damageBuffer());
        border.setDamageAmount(config.damageAmount());

        World nether = getNetherWorld(overworld);
        if (nether == null) {
            plugin.getLogger().warning("Could not find matching Nether world for '"
                    + overworld.getName() + "'. Skipping Nether world border sync.");
            return;
        }

        WorldBorder netherBorder = nether.getWorldBorder();
        netherBorder.setCenter(origin.x() / 8.0, origin.z() / 8.0);
        netherBorder.setSize(config.cellSize() / 8.0);
        netherBorder.setDamageBuffer(config.damageBuffer());
        netherBorder.setDamageAmount(config.damageAmount());
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
        border.setDamageBuffer(config.damageBuffer());
        border.setDamageAmount(config.damageAmount());

        World nether = getNetherWorld(overworld);
        if (nether == null) {
            plugin.getLogger().warning("Could not find matching Nether world for '"
                    + overworld.getName() + "'. Skipping Nether world border sync.");
            return;
        }

        WorldBorder netherBorder = nether.getWorldBorder();
        netherBorder.setCenter(origin.x() / 8.0, origin.z() / 8.0);
        netherBorder.setSize(config.cellSize() / 8.0, seconds);
        netherBorder.setDamageBuffer(config.damageBuffer());
        netherBorder.setDamageAmount(config.damageAmount());
    }

    private void clearWorldBorder(World world) {
        WorldBorder border = world.getWorldBorder();
        border.reset();
    }

    private World getNetherWorld(World overworld) {
        if (overworld.getEnvironment() == World.Environment.NETHER) {
            return overworld; // already the Nether, guard against double-wrapping
        }
        return Bukkit.getWorld(overworld.getName() + "_nether");
    }

    private Location resolveLobby(WorldEngineConfig config) {
        Location configured = config.lobbyLocation();
        World lobbyWorld = configured.getWorld();
        if (lobbyWorld == null) lobbyWorld = Bukkit.getWorld(config.worldName());
        if (lobbyWorld == null && !Bukkit.getWorlds().isEmpty()) lobbyWorld = Bukkit.getWorlds().get(0);
        if (lobbyWorld == null) return null;
        return new Location(lobbyWorld, configured.getX(), configured.getY(), configured.getZ(),
                configured.getYaw(), configured.getPitch());
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
}
