package com.jruk8.jmanhunt.world.teleport;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.jruk8.jmanhunt.world.WorldEngineConfig;

/** Lobby teleports plus shared spawn and coordinate math. Public for GameManager. */
public final class MatchTeleportService implements LobbyTeleporter {
    private final JManhuntPlugin plugin;
    private final LobbyWorldService lobbyWorlds;

    public MatchTeleportService(JManhuntPlugin plugin, LobbyWorldService lobbyWorlds) {
        this.plugin = plugin;
        this.lobbyWorlds = lobbyWorlds;
    }

    @Override
    public boolean teleportToLobby(List<Player> targets, int lobbyId) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled()) {
            return false;
        }

        Location lobby = lobbyWorlds.resolveLobbyTeleport(lobbyId, targets);
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
        if (!config.enabled()) {
            return false;
        }

        Location lobby = lobbyWorlds.resolveLobbyTeleport(lobbyId, targets);
        if (lobby == null) {
            return false;
        }

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
        return config.enabled() && lobbyWorlds.resolveLobby(lobbyId, false) != null;
    }

    /**
     * Random safe spawn near a center: a square scatter within radius,
     * landed on the highest motion-blocking block. Shared by cell
     * spawns and the engine-off surround.
     */
    public static Location spreadSpawn(World world, int centerX, int centerZ, int radius, float yaw,
            float pitch) {
        int offsetX = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
        int offsetZ = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
        int x = centerX + offsetX;
        int z = centerZ + offsetZ;
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) + 1;
        return new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);
    }

    /**
     * Spread spawn honoring the spawnpoint-algorithm config: validated
     * when enabled, plain otherwise.
     */
    public static Location spreadSpawnForConfig(World world, int centerX, int centerZ, int radius,
            float yaw, float pitch, WorldEngineConfig config) {
        if (config.spawnpointAlgorithmEnabled()) {
            return spreadSpawnValidated(world, centerX, centerZ, radius, yaw, pitch,
                    config.spawnpointMaxRetries());
        }
        return spreadSpawn(world, centerX, centerZ, radius, yaw, pitch);
    }

    /**
     * Validated spread spawn: lands below tree leaves and requires an air
     * gap at the feet and head blocks. Retries with fresh random offsets
     * up to {@code maxRetries} times after the first attempt, then falls
     * back to the plain spread. Shared by cell spawns and the engine-off
     * surround.
     */
    public static Location spreadSpawnValidated(World world, int centerX, int centerZ, int radius,
            float yaw, float pitch, int maxRetries) {
        int attempts = 1 + Math.max(0, maxRetries);
        int top = world.getMaxHeight() - 2;
        for (int attempt = 0; attempt < attempts; attempt++) {
            int offsetX = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int offsetZ = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int x = centerX + offsetX;
            int z = centerZ + offsetZ;
            int y = Math.min(world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, top);
            if (isAirLike(world.getBlockAt(x, y, z)) && isAirLike(world.getBlockAt(x, y + 1, z))) {
                return new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);
            }
        }
        return spreadSpawn(world, centerX, centerZ, radius, yaw, pitch);
    }

    /**
     * True when a block counts as air for spawn validation: transparent
     * and non-collidable, like grass or a torch. Pressure plates never
     * count, even though players move through them.
     */
    static boolean isAirLike(Block block) {
        return isAirLike(block.getType().isOccluding(), block.isPassable(),
                Tag.PRESSURE_PLATES.isTagged(block.getType()));
    }

    /** Pure air-like truth table for tests. */
    static boolean isAirLike(boolean occluding, boolean passable, boolean pressurePlate) {
        return !occluding && passable && !pressurePlate;
    }

    public static int toBlockCoordinate(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }
}
