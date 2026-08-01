package com.jruk8.jmanhunt.extras.world_engine;

import com.jruk8.jmanhunt.ConfigService;
import com.jruk8.jmanhunt.StatsRepository;
import com.jruk8.jmanhunt.extras.ExtrasListener;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ThreadLocalRandom;

public final class WorldEngineService implements ExtrasListener, LobbyTeleporter {
    private static final int MAX_CELL_ALLOCATE_ATTEMPTS = 20;

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final WorldCellAllocator cellAllocator;
    private final StrongholdDatapackManager strongholdDatapackManager;
    private final EndResetManager endResetManager;

    public WorldEngineService(JavaPlugin plugin, ConfigService configService, StatsRepository statsRepository) {
        this.plugin = plugin;
        this.configService = configService;
        this.cellAllocator = new WorldCellAllocator(statsRepository);
        this.strongholdDatapackManager = new StrongholdDatapackManager(plugin);
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

    public void onMatchEnd(List<Player> participants) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled()) return;

        Location lobby = getValidLobby(config);
        if (lobby == null) return;

        for (Player player : participants) {
            player.teleport(lobby);
        }
        endResetManager.reset(config, lobby);
    }

    public void teleportToLobby(Player player) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled()) return;

        Location lobby = getValidLobby(config);
        if (lobby != null) {
            player.teleport(lobby);
        }
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
        boolean isDisabled = !configService.getBoolean("extras.world-engine.enabled", false);
        if (isDisabled) {
            strongholdDatapackManager.remove(WorldEngineConfig.fromConfig(plugin.getConfig()));
        }
    }

    @Override
    public void onReload() {
        strongholdDatapackManager.apply(WorldEngineConfig.fromConfig(plugin.getConfig()));
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

            boolean useAlgo = configService.getBoolean("extras.world-engine.use-spawnpoint-algorithm", true);
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
        for (Player player : participants) {
            Location spawn = randomSpawnInCell(world, origin.x(), origin.z(), config.tpSpreadRadius(),
                    player.getLocation().getYaw(), player.getLocation().getPitch());
            player.teleport(spawn);
        }

        Location lobby = resolveLobby(config);
        endResetManager.reset(config, lobby);

        setWorldBorder(world, config, origin);
    }

    /**
     * Sets the world border for the overworld and its corresponding Nether world.
     */
    private void setWorldBorder(World overworld, WorldEngineConfig config, CellOrigin origin) {
        if (configService.getBoolean("extras.world-engine.use-world-border", false)) {
            return;
        }

        WorldBorder border = overworld.getWorldBorder();
        border.setCenter(origin.x(), origin.z());
        border.setSize(config.cellSize());

        World nether = getNetherWorld(overworld);
        if (nether == null) {
            plugin.getLogger().warning("Could not find matching Nether world for '"
                    + overworld.getName() + "'. Skipping Nether world border sync.");
            return;
        }

        WorldBorder netherBorder = nether.getWorldBorder();
        netherBorder.setCenter(origin.x() / 8.0, origin.z() / 8.0);
        netherBorder.setSize(config.cellSize() / 8.0);
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
