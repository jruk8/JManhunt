package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Signal interference verdicts for tracking attempts and scrolls. */
final class CompassSignalService {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;

    CompassSignalService(JManhuntPlugin plugin, PlayerStateStore playerStates) {
        this.plugin = plugin;
        this.playerStates = playerStates;
    }

    /**
     * Interference verdict for a resolved pick. Sightings use their
     * recorded location; every live kind uses the player's location, or
     * none when they logged out between selection and this check. Only
     * live targets get a line-of-sight reading.
     */
    boolean badSignalForPick(Player holder, CompassPick pick) {
        SignalInterference.Config interference = interferenceConfig();
        Location target = null;
        Player seen = null;
        if (pick.kind() == CompassPick.Kind.TRACK_SIGHTING) {
            target = playerStates.sightings().getOrDefault(pick.id(), Map.of())
                    .get(holder.getWorld().getUID());
        } else if (pick.id() != null) {
            seen = Bukkit.getPlayer(pick.id());
            target = seen == null ? null : seen.getLocation();
        }
        Boolean sight = interference.losEnabled() ? lineOfSight(holder, seen, interference) : null;
        return badSignal(holder, target, interference, sight);
    }

    /**
     * Interference verdict for a scroll attempt, evaluated against the
     * nearest candidate: live opponents first, then sightings.
     */
    boolean badSignalForScroll(Player holder, List<CompassCandidate> opponents,
            List<CompassSighting> sightings) {
        SignalInterference.Config interference = interferenceConfig();
        Location target = null;
        Player seen = null;
        if (!opponents.isEmpty()) {
            seen = Bukkit.getPlayer(opponents.get(0).id());
            target = seen == null ? null : seen.getLocation();
        }
        if (target == null && !sightings.isEmpty()) {
            target = playerStates.sightings().getOrDefault(sightings.get(0).ownerId(), Map.of())
                    .get(holder.getWorld().getUID());
        }
        Boolean sight = interference.losEnabled() ? lineOfSight(holder, seen, interference) : null;
        return badSignal(holder, target, interference, sight);
    }

    /**
     * True when signal interference fails this tracking attempt: the
     * holder's spot (and, with two-way, the target's spot) resolves to a
     * bad signal that the bypass roll does not save.
     */
    private boolean badSignal(Player holder, Location target, SignalInterference.Config interference,
            Boolean hasLineOfSight) {
        if (!plugin.getConfig().getBoolean("settings.compass.signal-interference.enabled", false)) {
            return false;
        }
        boolean ignoreTransparent = plugin.getConfig().getBoolean(
                "settings.compass.signal-interference.underground.ignore-transparent", true);
        SignalInterference.Snapshot targetSnapshot = interference.twoWay()
                ? targetSnapshot(target, ignoreTransparent) : null;
        return SignalInterference.badSignal(signalSnapshot(holder.getLocation(), ignoreTransparent),
                targetSnapshot, interference, ThreadLocalRandom.current().nextDouble(), hasLineOfSight);
    }

    /**
     * Eye-to-eye sight from the holder to a live target, or null when no
     * ray applies: no target, another world, or a target the holder
     * cannot share a world with.
     */
    private Boolean lineOfSight(Player holder, Player target, SignalInterference.Config interference) {
        if (target == null || !target.getWorld().equals(holder.getWorld())) {
            return null;
        }
        Location from = holder.getEyeLocation();
        Location to = target.getEyeLocation();
        World world = holder.getWorld();
        boolean clear = rayClear(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(),
                interference.losMaxDistance(),
                (x, y, z) -> y >= world.getMinHeight() && y < world.getMaxHeight()
                        && world.isChunkLoaded(x >> 4, z >> 4)
                        && world.getBlockAt(x, y, z).getType().isOccluding());
        return clear;
    }

    private SignalInterference.Config interferenceConfig() {
        String base = "settings.compass.signal-interference.";
        Set<SignalInterference.Weather> during = interfereDuring(base);
        SignalInterference.InterfereWhen when = lightInterfereWhen(base);
        SignalInterference.InterfereWhenVisible losWhen = losInterfereWhen(base);
        return new SignalInterference.Config(
                plugin.getConfig().getBoolean(base + "light-level.enabled", false),
                plugin.getConfig().getInt(base + "light-level.min-sky-light", 10),
                plugin.getConfig().getInt(base + "light-level.min-block-light", 5),
                when,
                plugin.getConfig().getBoolean(base + "underground.enabled", false),
                plugin.getConfig().getInt(base + "underground.max-blocks-above", 3),
                plugin.getConfig().getBoolean(base + "underwater.enabled", true),
                plugin.getConfig().getInt(base + "underwater.max-blocks-above", 2),
                plugin.getConfig().getBoolean(base + "altitude.enabled", false),
                plugin.getConfig().getInt(base + "altitude.min-y", -20),
                plugin.getConfig().getInt(base + "altitude.max-y", 120),
                plugin.getConfig().getBoolean(base + "weather.enabled", false),
                during,
                plugin.getConfig().getBoolean(base + "biome.enabled", false),
                new HashSet<>(plugin.getConfig().getStringList(base + "biome.interfere-in")),
                plugin.getConfig().getBoolean(base + "line-of-sight.enabled", false),
                losWhen,
                plugin.getConfig().getInt(base + "line-of-sight.max-ray-distance", 300),
                plugin.getConfig().getInt(base + "required-to-fail", 1),
                plugin.getConfig().getBoolean(base + "two-way", false),
                plugin.getConfig().getDouble(base + "chance-to-bypass", 0.0));
    }

    /** Parses the weather buckets that interfere, ignoring unknown values. */
    private Set<SignalInterference.Weather> interfereDuring(String base) {
        Set<SignalInterference.Weather> during = new HashSet<>();
        for (String raw : plugin.getConfig().getStringList(base + "weather.interfere-during")) {
            try {
                during.add(SignalInterference.Weather.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                // Unknown buckets are ignored, so one typo cannot break refreshes.
            }
        }
        return during;
    }

    /** Parses the light-level interfere-when mode, defaulting to ONE_UNMET. */
    private SignalInterference.InterfereWhen lightInterfereWhen(String base) {
        try {
            String raw = plugin.getConfig().getString(base + "light-level.interfere-when", "ONE_UNMET");
            return SignalInterference.InterfereWhen.valueOf(
                    (raw == null ? "ONE_UNMET" : raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SignalInterference.InterfereWhen.ONE_UNMET;
        }
    }

    /** Parses the line-of-sight interfere-when mode, defaulting to VISIBLE. */
    private SignalInterference.InterfereWhenVisible losInterfereWhen(String base) {
        try {
            String raw = plugin.getConfig().getString(base + "line-of-sight.interfere-when", "VISIBLE");
            return SignalInterference.InterfereWhenVisible.valueOf(
                    (raw == null ? "VISIBLE" : raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SignalInterference.InterfereWhenVisible.VISIBLE;
        }
    }

    /** Signal snapshot for a spot, read from its feet block. */
    private SignalInterference.Snapshot signalSnapshot(Location location, boolean ignoreTransparent) {
        Block block = location.getBlock();
        World world = block.getWorld();
        SignalInterference.Weather weather;
        if (world.isThundering()) {
            weather = SignalInterference.Weather.STORM;
        } else if (world.hasStorm()) {
            weather = SignalInterference.Weather.RAIN;
        } else {
            weather = SignalInterference.Weather.CLEAR;
        }
        return new SignalInterference.Snapshot(
                block.getLightFromSky(),
                block.getLightFromBlocks(),
                world.getEnvironment() == World.Environment.NORMAL,
                solidBlocksAbove(block, ignoreTransparent),
                fluidBlocksAbove(block),
                block.getY(),
                weather,
                block.getBiome().getKey().toString());
    }

    /**
     * Target-side snapshot for two-way checks, or null when the spot's
     * chunk is not loaded. An unloaded sighting never fails the target
     * side, so refreshes never force chunk loads.
     */
    private SignalInterference.Snapshot targetSnapshot(Location target, boolean ignoreTransparent) {
        if (target == null || target.getWorld() == null
                || !target.getWorld().isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) {
            return null;
        }
        return signalSnapshot(target, ignoreTransparent);
    }

    /** Blocks strictly above the feet block, capped for cheap reads. */
    private static final int MAX_ABOVE_COUNT = 380;

    private int solidBlocksAbove(Block feet, boolean ignoreTransparent) {
        World world = feet.getWorld();
        int count = 0;
        for (int y = feet.getY() + 1; y < world.getMaxHeight(); y++) {
            Block block = world.getBlockAt(feet.getX(), y, feet.getZ());
            if (countsAsCover(block.isSolid(), block.getType().isOccluding(), ignoreTransparent)) {
                count++;
                if (count > MAX_ABOVE_COUNT) {
                    break;
                }
            }
        }
        return count;
    }

    /**
     * True when a block above the feet counts as cover: any solid block,
     * or only whole occluding ones when transparent blocks are ignored.
     * Pure for tests.
     */
    static boolean countsAsCover(boolean solid, boolean occluding, boolean ignoreTransparent) {
        if (!solid) {
            return false;
        }
        return !ignoreTransparent || occluding;
    }

    /** Water or lava blocks strictly above the feet block. */
    private int fluidBlocksAbove(Block feet) {
        World world = feet.getWorld();
        int count = 0;
        for (int y = feet.getY() + 1; y < world.getMaxHeight(); y++) {
            Material type = world.getBlockAt(feet.getX(), y, feet.getZ()).getType();
            if (type == Material.WATER || type == Material.LAVA) {
                count++;
                if (count > MAX_ABOVE_COUNT) {
                    break;
                }
            }
        }
        return count;
    }

    /** Answers whether one block coordinate blocks sight. */
    interface SightProbe {
        boolean blocks(int x, int y, int z);
    }

    /** Ray sampling step in blocks; whole cubes cannot hide between samples. */
    static final double LOS_STEP = 0.5;

    /**
     * Eye-to-eye sight along one ray: false past maxDistance or when any
     * sampled block between the endpoints blocks sight. The start block
     * is skipped, the end block counts. Pure for tests.
     */
    static boolean rayClear(double x0, double y0, double z0, double x1, double y1, double z1,
            double maxDistance, SightProbe probe) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > maxDistance) {
            return false;
        }
        int steps = Math.max(1, (int) Math.ceil(distance / LOS_STEP));
        for (int step = 1; step <= steps; step++) {
            double fraction = (double) step / steps;
            int x = (int) Math.floor(x0 + dx * fraction);
            int y = (int) Math.floor(y0 + dy * fraction);
            int z = (int) Math.floor(z0 + dz * fraction);
            if (probe.blocks(x, y, z)) {
                return false;
            }
        }
        return true;
    }
}
