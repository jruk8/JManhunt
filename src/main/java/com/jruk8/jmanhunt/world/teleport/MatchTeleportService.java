package com.jruk8.jmanhunt.world.teleport;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
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
