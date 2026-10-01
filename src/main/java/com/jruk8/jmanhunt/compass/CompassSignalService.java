package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.lobby.config.CompassSettingsFacade;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Signal interference verdicts for tracking attempts and scrolls. */
final class CompassSignalService {
    private final CompassSettingsFacade settings;
    private final PlayerStateStore playerStates;
    private GameManager game;

    CompassSignalService(CompassSettingsFacade settings, PlayerStateStore playerStates) {
        this.settings = settings;
        this.playerStates = playerStates;
    }

    /** Wires the game after construction; verdicts need matches. */
    void setGameManager(GameManager game) {
        this.game = game;
    }

    /** Origin lobby of the holder's match, or null outside matches. */
    private Integer lobbyOf(Player holder) {
        return game == null ? null : game.lobbyOfPlayer(holder.getUniqueId());
    }

    /**
     * Interference verdict for a resolved pick. Sightings use their
     * recorded location; every live kind uses the player's location, or
     * none when they logged out between selection and this check. Only
     * live targets get a line-of-sight reading.
     */
    boolean badSignalForPick(Player holder, CompassPick pick) {
        return reasonForPick(holder, holder.getLocation(), null, pick).isPresent();
    }

    /** Spot-aware verdict: the holder reads from their resolution spot. */
    boolean badSignalForPick(Player holder, Location holderSpot, CompassPick pick) {
        return reasonForPick(holder, holderSpot, null, pick).isPresent();
    }

    /**
     * Failing interference option for a resolved pick, or empty when
     * the signal is good. Sightings use their recorded location; every
     * live kind uses the player's location, or none when they logged out
     * between selection and this check. Only live targets get a
     * line-of-sight reading.
     */
    Optional<SignalInterference.Reason> reasonForPick(Player holder, CompassPick pick) {
        return reasonForPick(holder, holder.getLocation(), null, pick);
    }

    /**
     * Spot-aware verdict: interference reads the holder's resolution
     * spot (press-time while analyzing, live otherwise) instead of
     * their current location.
     */
    Optional<SignalInterference.Reason> reasonForPick(Player holder, Location holderSpot,
            CompassPick pick) {
        return reasonForPick(holder, holderSpot, null, pick);
    }

    /**
     * Full verdict with a target press spot: target-side checks compare
     * the press-time spot (when present) instead of the moved-to spot,
     * and movement measures both sides against their press spots.
     */
    Optional<SignalInterference.Reason> reasonForPick(Player holder, Location holderSpot,
            Location targetPress, CompassPick pick) {
        return reasonForPick(holder, holderSpot, targetPress, pick,
                movedBlocks(holderSpot, holder.getLocation()));
    }

    /**
     * Full verdict with an explicit holder movement reading: analysis
     * resolutions pass the longest displacement sampled during the run
     * instead of the press-to-live gap, so moving out and back cannot
     * hide. Target-side movement still compares press to live.
     */
    Optional<SignalInterference.Reason> reasonForPick(Player holder, Location holderSpot,
            Location targetPress, CompassPick pick, double holderMoved) {
        SignalInterference.Config interference = interferenceConfig(lobbyOf(holder));
        Location target = null;
        Player seen = null;
        Location press = null;
        if (pick.kind() == CompassPick.Kind.TRACK_SIGHTING) {
            target = playerStates.sightings().getOrDefault(pick.id(), Map.of())
                    .get(holderSpot.getWorld().getUID());
        } else if (pick.id() != null) {
            seen = Bukkit.getPlayer(pick.id());
            target = seen == null ? null : seen.getLocation();
            press = targetPress;
        }
        Boolean sight = interference.losEnabled()
                ? lineOfSight(holder, holderSpot, seen, interference) : null;
        return reason(holder, holderSpot, target, press, interference, sight, seen, holderMoved);
    }

    /**
     * Failing interference option for one tracking attempt, or empty
     * when the signal is good or the bypass roll saves it. The holder
     * movement reading arrives precomputed; target-side checks read the
     * press spot whenever one is known.
     */
    private Optional<SignalInterference.Reason> reason(Player holder, Location holderSpot,
            Location target, Location targetPress, SignalInterference.Config interference,
            Boolean hasLineOfSight, Player seen, double holderMoved) {
        Integer lobby = lobbyOf(holder);
        if (!settings.interferenceEnabled(lobby)) {
            return Optional.empty();
        }
        boolean ignoreTransparent = settings.undergroundIgnoreTransparent(lobby);
        SignalInterference.Snapshot targetSnapshot = targetSnapshot(target, targetPress,
                ignoreTransparent, seen);
        return SignalInterference.lastReason(
                signalSnapshot(holderSpot, ignoreTransparent, holderMoved, isInvisible(holder),
                        holder.getHealth(), holder.getFoodLevel(), holder.getLevel()),
                targetSnapshot, interference, ThreadLocalRandom.current().nextDouble(), hasLineOfSight);
    }

    /** True when the player carries the vanilla invisibility effect. */
    private static boolean isInvisible(Player player) {
        return player.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    /**
     * Blocks moved between two spots: 0 when either spot is missing,
     * fail-closed huge when the worlds differ or are unknown. Pure.
     */
    static double movedBlocks(Location from, Location to) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) {
            return from == null || to == null ? 0.0 : Double.MAX_VALUE;
        }
        return from.distance(to);
    }

    /**
     * Longest squared displacement from the press spot so far: folds one
     * live sample into the running maximum without a square root per
     * tick; callers sqrt once at comparison time. A missing press spot
     * keeps the running max, while a world change or unknown world voids
     * fail-closed huge. Pure.
     */
    static double maxSoFar(double currentMaxSq, Location press, Location now) {
        if (press == null) {
            return currentMaxSq;
        }
        if (now == null || now.getWorld() == null || press.getWorld() == null
                || !press.getWorld().equals(now.getWorld())) {
            return Double.MAX_VALUE;
        }
        return Math.max(currentMaxSq, press.distanceSquared(now));
    }

    /**
     * Eye-to-eye sight from the holder's resolution spot to a live
     * target, or null when no ray applies: no target, another world,
     * or a target the spot cannot share a world with.
     */
    private Boolean lineOfSight(Player holder, Location holderSpot, Player target,
            SignalInterference.Config interference) {
        if (target == null || holderSpot.getWorld() == null
                || !target.getWorld().equals(holderSpot.getWorld())) {
            return null;
        }
        Location liveFeet = holder.getLocation();
        Location liveEye = holder.getEyeLocation();
        Location from = holderSpot.clone().add(liveEye.getX() - liveFeet.getX(),
                liveEye.getY() - liveFeet.getY(), liveEye.getZ() - liveFeet.getZ());
        Location to = target.getEyeLocation();
        World world = holderSpot.getWorld();
        boolean clear = rayClear(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(),
                interference.losMaxDistance(),
                (x, y, z) -> y >= world.getMinHeight() && y < world.getMaxHeight()
                        && world.isChunkLoaded(x >> 4, z >> 4)
                        && world.getBlockAt(x, y, z).getType().isOccluding());
        return clear;
    }

    private SignalInterference.Config interferenceConfig(Integer lobby) {
        Set<SignalInterference.Weather> during = interfereDuring(lobby);
        SignalInterference.StatThresholds stats = statThresholds(lobby);
        return new SignalInterference.Config(
                settings.lightLevelEnabled(lobby),
                settings.lightLevelMinSkyLight(lobby),
                settings.lightLevelMinBlockLight(lobby),
                settings.lightLevelInterfereWhen(lobby),
                settings.lightLevelCheckOn(lobby),
                settings.undergroundEnabled(lobby),
                settings.undergroundMaxBlocksAbove(lobby),
                settings.undergroundCheckOn(lobby),
                settings.underwaterEnabled(lobby),
                settings.underwaterMaxBlocksAbove(lobby),
                settings.underwaterCheckOn(lobby),
                settings.altitudeEnabled(lobby),
                settings.altitudeMinY(lobby),
                settings.altitudeMaxY(lobby),
                settings.altitudeCheckOn(lobby),
                settings.weatherEnabled(lobby),
                during,
                settings.biomeEnabled(lobby),
                new HashSet<>(settings.biomeInterfereIn(lobby)),
                settings.biomeCheckOn(lobby),
                settings.movementEnabled(lobby),
                settings.movementThresholdBlocks(lobby),
                settings.movementCheckOn(lobby),
                settings.lineOfSightEnabled(lobby),
                settings.lineOfSightInterfereWhen(lobby),
                settings.lineOfSightMaxRayDistance(lobby),
                settings.invisibleEnabled(lobby),
                settings.invisibleCheckOn(lobby),
                stats.healthEnabled(), stats.minHealth(), stats.healthCheckOn(),
                stats.hungerEnabled(), stats.minHunger(), stats.hungerCheckOn(),
                stats.expEnabled(), stats.minExpLevel(), stats.expCheckOn(),
                settings.requiredToFail(lobby),
                settings.chanceToBypass(lobby));
    }

    /** Resolved player-stats thresholds for one lobby. */
    private SignalInterference.StatThresholds statThresholds(Integer lobby) {
        return new SignalInterference.StatThresholds(
                settings.statsHealthEnabled(lobby),
                settings.statsMinHealth(lobby),
                settings.statsHealthCheckOn(lobby),
                settings.statsHungerEnabled(lobby),
                settings.statsMinHunger(lobby),
                settings.statsHungerCheckOn(lobby),
                settings.statsExperienceEnabled(lobby),
                settings.statsMinExpLevel(lobby),
                settings.statsExperienceCheckOn(lobby));
    }

    /** Parses the weather buckets that interfere, ignoring unknown values. */
    private Set<SignalInterference.Weather> interfereDuring(Integer lobby) {
        Set<SignalInterference.Weather> during = new HashSet<>();
        for (String raw : settings.weatherInterfereDuring(lobby)) {
            try {
                during.add(SignalInterference.Weather.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                // Unknown buckets are ignored, so one typo cannot break refreshes.
            }
        }
        return during;
    }

    /** Signal snapshot for a spot, read from its feet block plus side stats. */
    private SignalInterference.Snapshot signalSnapshot(Location location, boolean ignoreTransparent,
            double movedBlocks, boolean invisible, double health, int hunger, int expLevel) {
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
                block.getBiome().getKey().toString(),
                movedBlocks,
                block.getType() == Material.WATER,
                invisible,
                health,
                hunger,
                expLevel);
    }

    /**
     * Target-side snapshot for TARGET and BOTH check-on evaluation:
     * the press-time spot when one is known, else the live spot. Null
     * when no spot is known or
     * its chunk is not loaded, so unloaded sightings never fail the
     * target side and refreshes never force chunk loads. Offline and
     * sighting targets read passing stats, so stats only fail live
     * watched players.
     */
    private SignalInterference.Snapshot targetSnapshot(Location live, Location press,
            boolean ignoreTransparent, Player seen) {
        Location spot = press != null ? press : live;
        if (spot == null || spot.getWorld() == null
                || !spot.getWorld().isChunkLoaded(spot.getBlockX() >> 4, spot.getBlockZ() >> 4)) {
            return null;
        }
        double health = seen == null ? Double.MAX_VALUE : seen.getHealth();
        int hunger = seen == null ? 20 : seen.getFoodLevel();
        int expLevel = seen == null ? Integer.MAX_VALUE : seen.getLevel();
        return signalSnapshot(spot, ignoreTransparent, movedBlocks(press, live),
                seen != null && isInvisible(seen), health, hunger, expLevel);
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
