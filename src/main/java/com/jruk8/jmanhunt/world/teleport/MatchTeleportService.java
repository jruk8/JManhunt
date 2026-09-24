package com.jruk8.jmanhunt.world.teleport;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Collections;
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
        return teleportToLobby(targets, lobbyId, true);
    }

    @Override
    public boolean teleportToLobbyQuiet(List<Player> targets, int lobbyId) {
        return teleportToLobby(targets, lobbyId, false);
    }

    private boolean teleportToLobby(List<Player> targets, int lobbyId, boolean announce) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.configService());
        if (!config.enabled()) {
            return false;
        }

        Location lobby = lobbyWorlds.resolveLobbyTeleport(lobbyId, targets, announce);
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
        return setSpawnToLobby(targets, lobbyId, true);
    }

    @Override
    public boolean setSpawnToLobbyQuiet(List<Player> targets, int lobbyId) {
        return setSpawnToLobby(targets, lobbyId, false);
    }

    private boolean setSpawnToLobby(List<Player> targets, int lobbyId, boolean announce) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.configService());
        if (!config.enabled()) {
            return false;
        }

        Location lobby = lobbyWorlds.resolveLobbyTeleport(lobbyId, targets, announce);
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
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.configService());
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
     * One spread spawn per player, in order, honoring the
     * spawnpoint-algorithm config: height-leveled when enabled, plain
     * otherwise. Yaw and pitch come from each player's current view.
     */
    public static List<Location> spreadSpawnsForConfig(World world, int centerX, int centerZ,
            int radius, List<Player> players, WorldEngineConfig config) {
        List<Location> spawns = new ArrayList<>(players.size());
        for (Player player : players) {
            spawns.add(spreadSpawn(world, centerX, centerZ, radius,
                    player.getLocation().getYaw(), player.getLocation().getPitch()));
        }
        if (!config.spawnpointAlgorithmEnabled() || players.isEmpty()) {
            return spawns;
        }
        return levelSpawns(world, centerX, centerZ, radius, players, config);
    }

    /**
     * Height-leveled spawns: one validated roll per player, then the
     * median height becomes the target band. Outliers re-roll up to
     * max-retries times; without a fitting roll the closest candidate
     * (original included) wins. Fluid or invalid rolls are discarded;
     * with zero candidates the center is the absolute fallback.
     */
    private static List<Location> levelSpawns(World world, int centerX, int centerZ, int radius,
            List<Player> players, WorldEngineConfig config) {
        int top = world.getMaxHeight() - 2;
        List<Location> first = new ArrayList<>(players.size());
        List<Integer> firstHeights = new ArrayList<>(players.size());
        for (Player player : players) {
            Location roll = rollSpot(world, centerX, centerZ, radius,
                    player.getLocation().getYaw(), player.getLocation().getPitch(), top);
            first.add(roll);
            firstHeights.add(roll.getBlockY());
        }
        int median = medianY(firstHeights);
        List<Location> spawns = new ArrayList<>(players.size());
        for (int index = 0; index < players.size(); index++) {
            Player player = players.get(index);
            spawns.add(levelOne(world, centerX, centerZ, radius, first.get(index), median,
                    player.getLocation().getYaw(), player.getLocation().getPitch(), top, config));
        }
        return spawns;
    }

    /** Leveled spawn for one player against the group median height. */
    private static Location levelOne(World world, int centerX, int centerZ, int radius,
            Location first, int median, float yaw, float pitch, int top, WorldEngineConfig config) {
        List<Location> candidates = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        if (validSpot(world, first.getBlockX(), first.getBlockY(), first.getBlockZ())) {
            candidates.add(first);
            heights.add(first.getBlockY());
        }
        if (candidates.isEmpty() || !fitsY(first.getBlockY(), median, config.spawnpointYTolerance())) {
            for (int attempt = 0; attempt < config.spawnpointMaxRetries(); attempt++) {
                Location roll = rollSpot(world, centerX, centerZ, radius, yaw, pitch, top);
                if (!validSpot(world, roll.getBlockX(), roll.getBlockY(), roll.getBlockZ())) {
                    continue;
                }
                candidates.add(roll);
                heights.add(roll.getBlockY());
                if (fitsY(roll.getBlockY(), median, config.spawnpointYTolerance())) {
                    break;
                }
            }
        }
        if (candidates.isEmpty()) {
            return cellCenterSpawn(world, centerX, centerZ, yaw, pitch, top);
        }
        return candidates.get(selectCandidate(heights, median, config.spawnpointYTolerance()));
    }

    /** One random roll below tree leaves, unvalidated. */
    private static Location rollSpot(World world, int centerX, int centerZ, int radius,
            float yaw, float pitch, int top) {
        int offsetX = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
        int offsetZ = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
        int x = centerX + offsetX;
        int z = centerZ + offsetZ;
        int y = Math.min(world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, top);
        return new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);
    }

    /** Absolute fallback: the center at a safe height, checks skipped. */
    private static Location cellCenterSpawn(World world, int centerX, int centerZ,
            float yaw, float pitch, int top) {
        int y = Math.min(world.getHighestBlockYAt(centerX, centerZ,
                HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, top);
        return new Location(world, centerX + 0.5, y, centerZ + 0.5, yaw, pitch);
    }

    /** True when both blocks are air-like and neither is a fluid spawn. */
    private static boolean validSpot(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        return isAirLike(feet) && isAirLike(head)
                && !isFluid(feet.getType()) && !isFluid(head.getType());
    }

    /** True for fluids spawns must never land in. Pure for tests. */
    static boolean isFluid(Material type) {
        return type == Material.WATER
                || type == Material.LAVA
                || type == Material.POWDER_SNOW;
    }

    /** Median of non-empty heights; even counts take the higher middle. Pure for tests. */
    static int medianY(List<Integer> heights) {
        List<Integer> sorted = new ArrayList<>(heights);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    /** True when a height sits inside the median band. Pure for tests. */
    static boolean fitsY(int y, int median, int tolerance) {
        return Math.abs(y - median) <= Math.max(0, tolerance);
    }

    /** Closest height index; first-seen wins ties. Pure for tests. */
    static int closestIndex(List<Integer> heights, int median) {
        int best = 0;
        for (int index = 1; index < heights.size(); index++) {
            if (Math.abs(heights.get(index) - median) < Math.abs(heights.get(best) - median)) {
                best = index;
            }
        }
        return best;
    }

    /**
     * Winning candidate index: the first fitting roll, else the closest.
     * Candidates arrive in roll order with the original first. Pure for
     * tests.
     */
    static int selectCandidate(List<Integer> heights, int median, int tolerance) {
        for (int index = 0; index < heights.size(); index++) {
            if (fitsY(heights.get(index), median, tolerance)) {
                return index;
            }
        }
        return closestIndex(heights, median);
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
