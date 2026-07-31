package com.jruk8.jmanhunt.extras.world_engine;

import com.jruk8.jmanhunt.StatsRepository;
import com.jruk8.jmanhunt.extras.ExtrasListener;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ThreadLocalRandom;

public final class WorldEngineService implements ExtrasListener {
    private final JavaPlugin plugin;
    private final WorldCellAllocator cellAllocator;
    private final StrongholdDatapackManager strongholdDatapackManager;
    private final EndResetManager endResetManager;

    public WorldEngineService(JavaPlugin plugin, StatsRepository statsRepository) {
        this.plugin = plugin;
        this.cellAllocator = new WorldCellAllocator(statsRepository);
        this.strongholdDatapackManager = new StrongholdDatapackManager(plugin);
        this.endResetManager = new EndResetManager(plugin);
    }

    public void onMatchStart(List<Player> participants) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled() || participants.isEmpty()) return;
        World world = Bukkit.getWorld(config.worldName());
        if (world == null) return;

        OptionalLong startIndex = cellAllocator.reserveStartIndex(1);
        if (startIndex.isEmpty()) return;

        long baseIndex = startIndex.getAsLong();

        SpiralCoordinateMapper.CellCoordinate cell = SpiralCoordinateMapper.toCoordinate(baseIndex);
        int originX = toBlockCoordinate(cell.x() * config.cellSize());
        int originZ = toBlockCoordinate(cell.z() * config.cellSize());

        for (Player player : participants) {
            Location spawn = randomSpawnInCell(world, originX, originZ, config.tpSpreadRadius(),
                    player.getLocation().getYaw(), player.getLocation().getPitch());
            player.teleport(spawn);
        }
        // reset on start as well to ensure the end is clean for the match
        Location lobby = resolveLobby(config);
        endResetManager.reset(config, lobby);
    }

    public void onMatchEnd(List<Player> participants) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled()) return;
        Location lobby = resolveLobby(config);
        if (lobby == null || lobby.getWorld() == null) {
            plugin.getLogger().warning("Skipping world-engine lobby teleport because lobby world was not found.");
            return;
        }
        for (Player player : participants) {
            player.teleport(lobby);
        }
        endResetManager.reset(config, lobby);
    }

    @Override
    public void onReload() {
        strongholdDatapackManager.apply(WorldEngineConfig.fromConfig(plugin.getConfig()));
    }

    @Override
    public String getDataPath() {
        return "world-engine/strongholds.json";
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
