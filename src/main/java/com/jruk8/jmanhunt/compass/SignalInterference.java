package com.jruk8.jmanhunt.compass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Pure signal-interference verdicts for compass tracking. Bukkit-free:
 * callers gather a snapshot per evaluated spot, resolve the config
 * values, and get a good/bad answer. Never returns interference when
 * no sub-option is enabled.
 */
public final class SignalInterference {

    private SignalInterference() {
    }

    /** Weather buckets: thundering, storming without thunder, or neither. */
    public enum Weather {
        STORM,
        RAIN,
        CLEAR
    }

    /** When the light option interferes: either minimum unmet, or both. */
    public enum InterfereWhen {
        ONE_UNMET,
        BOTH_UNMET
    }

    /** When the line-of-sight option interferes: target visible, or hidden. */
    public enum InterfereWhenVisible {
        VISIBLE,
        NOT_VISIBLE
    }

    /**
     * One evaluated spot: light levels, whether the world is an overworld,
     * solid and fluid blocks strictly above the feet, feet Y, weather, the
     * block biome key such as "minecraft:desert" (lowercased), blocks moved
     * since the refresh started, and whether the feet block is water.
     */
    public record Snapshot(
            int skyLight,
            int blockLight,
            boolean normalEnvironment,
            int solidBlocksAbove,
            int fluidBlocksAbove,
            int blockY,
            Weather weather,
            String biomeKey,
            double movedBlocks,
            boolean feetInWater) {
        public Snapshot {
            biomeKey = biomeKey == null ? "" : biomeKey.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Display reason for a bad signal: the failing option id plus
     * whether the target side (rather than the holder side) failed.
     */
    public record Reason(String id, boolean targetSide) {
    }

    /**
     * Resolved interference config values. The compact constructor clamps
     * every range (light 0-15, cover and fluid 1-380, Y -64-319, ray
     * 1-1000, bypass 0-1, movement threshold at 0), normalizes the
     * altitude endpoints so min <= max, defaults a missing interfere-when
     * to BOTH_UNMET and a missing line-of-sight mode to NOT_VISIBLE, and
     * lowercases the biome list. Required-to-fail is clamped against the
     * enabled count at verdict time instead, since this record does not
     * count enablers.
     */
    public record Config(
            boolean lightEnabled,
            int minSkyLight,
            int minBlockLight,
            InterfereWhen interfereWhen,
            boolean lightTwoWay,
            boolean undergroundEnabled,
            int maxBlocksAbove,
            boolean undergroundTwoWay,
            boolean underwaterEnabled,
            int maxFluidAbove,
            boolean underwaterTwoWay,
            boolean altitudeEnabled,
            int minY,
            int maxY,
            boolean altitudeTwoWay,
            boolean weatherEnabled,
            Set<Weather> interfereDuring,
            boolean weatherTwoWay,
            boolean biomeEnabled,
            Set<String> interfereIn,
            boolean biomeTwoWay,
            boolean movementEnabled,
            double thresholdBlocks,
            boolean movementTwoWay,
            boolean losEnabled,
            InterfereWhenVisible losWhen,
            int losMaxDistance,
            int requiredToFail,
            double chanceToBypass) {
        public Config {
            minSkyLight = clamp(minSkyLight, 0, 15);
            minBlockLight = clamp(minBlockLight, 0, 15);
            interfereWhen = interfereWhen == null ? InterfereWhen.BOTH_UNMET : interfereWhen;
            maxBlocksAbove = clamp(maxBlocksAbove, 1, 380);
            maxFluidAbove = clamp(maxFluidAbove, 1, 380);
            losWhen = losWhen == null ? InterfereWhenVisible.NOT_VISIBLE : losWhen;
            losMaxDistance = clamp(losMaxDistance, 1, 1000);
            minY = clamp(minY, -64, 319);
            maxY = clamp(maxY, -64, 319);
            if (minY > maxY) {
                int swap = minY;
                minY = maxY;
                maxY = swap;
            }
            interfereDuring = interfereDuring == null ? Set.of() : Set.copyOf(interfereDuring);
            Set<String> biomes = new HashSet<>();
            if (interfereIn != null) {
                for (String key : interfereIn) {
                    if (key != null) {
                        biomes.add(key.toLowerCase(Locale.ROOT));
                    }
                }
            }
            interfereIn = Set.copyOf(biomes);
            thresholdBlocks = Math.max(0.0, thresholdBlocks);
            chanceToBypass = Math.min(1.0, Math.max(0.0, chanceToBypass));
        }

        private static int clamp(int value, int min, int max) {
            return Math.min(max, Math.max(min, value));
        }
    }

    /**
     * True when the holder's tracking fails with a bad signal. The
     * line-of-sight verdict is relational, so the caller raycasts it and
     * passes it in; null skips the option. Each side counts its own
     * failures against required-to-fail: the holder side across every
     * enabled option, the target side across options with two-way on. Pure.
     */
    public static boolean badSignal(Snapshot holder, Snapshot target, Config config, double roll,
            Boolean hasLineOfSight) {
        int enabled = enabledCount(config);
        if (enabled == 0) {
            return false;
        }
        int required = Math.min(enabled, Math.max(1, config.requiredToFail()));
        boolean bad = interferingCount(holder, config, hasLineOfSight, false) >= required
                || (target != null
                        && interferingCount(target, config, hasLineOfSight, true) >= required);
        if (!bad) {
            return false;
        }
        return !(roll < config.chanceToBypass());
    }

    /** Same verdict with no line-of-sight reading; the option is skipped. Pure. */
    public static boolean badSignal(Snapshot holder, Snapshot target, Config config, double roll) {
        return badSignal(holder, target, config, roll, null);
    }

    /**
     * Display reason for a bad signal: the most recently found failing
     * option, or empty when the signal is good. The holder side wins
     * ties, matching the verdict short-circuit. Pure.
     */
    public static Optional<Reason> lastReason(Snapshot holder, Snapshot target,
            Config config, double roll, Boolean hasLineOfSight) {
        int enabled = enabledCount(config);
        if (enabled == 0) {
            return Optional.empty();
        }
        int required = Math.min(enabled, Math.max(1, config.requiredToFail()));
        List<String> holderReasons =
                interferingReasons(holder, config, hasLineOfSight, false);
        List<String> targetReasons = target != null
                ? interferingReasons(target, config, hasLineOfSight, true)
                : List.of();
        boolean bad = holderReasons.size() >= required || targetReasons.size() >= required;
        if (!bad || roll < config.chanceToBypass()) {
            return Optional.empty();
        }
        if (holderReasons.size() >= required) {
            return Optional.of(new Reason(holderReasons.get(holderReasons.size() - 1), false));
        }
        return Optional.of(new Reason(targetReasons.get(targetReasons.size() - 1), true));
    }

    /** Enabled sub-options reporting interference at one spot. Pure. */
    static int interferingCount(Snapshot snapshot, Config config, Boolean hasLineOfSight,
            boolean targetSide) {
        return interferingReasons(snapshot, config, hasLineOfSight, targetSide).size();
    }

    /**
     * Ids of the enabled sub-options interfering at one spot, in check
     * order. Target-side evaluation only considers options with two-way
     * on; line of sight has no two-way flag and never counts there. Pure.
     */
    static List<String> interferingReasons(Snapshot snapshot, Config config,
            Boolean hasLineOfSight, boolean targetSide) {
        List<String> reasons = new ArrayList<>();
        if (config.lightEnabled() && (!targetSide || config.lightTwoWay())
                && lightInterferes(snapshot, config.minSkyLight(),
                        config.minBlockLight(), config.interfereWhen())) {
            reasons.add("light-level");
        }
        if (config.undergroundEnabled() && (!targetSide || config.undergroundTwoWay())
                && undergroundInterferes(snapshot.solidBlocksAbove(), config.maxBlocksAbove())) {
            reasons.add("underground");
        }
        if (config.underwaterEnabled() && (!targetSide || config.underwaterTwoWay())
                && underwaterInterferes(snapshot, config.maxFluidAbove())) {
            reasons.add("underwater");
        }
        if (config.altitudeEnabled() && (!targetSide || config.altitudeTwoWay())
                && altitudeInterferes(snapshot.blockY(), config.minY(), config.maxY())) {
            reasons.add("altitude");
        }
        if (config.weatherEnabled() && (!targetSide || config.weatherTwoWay())
                && weatherInterferes(snapshot.weather(), config.interfereDuring())) {
            reasons.add("weather");
        }
        if (config.biomeEnabled() && (!targetSide || config.biomeTwoWay())
                && biomeInterferes(snapshot.biomeKey(), config.interfereIn())) {
            reasons.add("biome");
        }
        if (config.movementEnabled() && (!targetSide || config.movementTwoWay())
                && movementInterferes(snapshot.movedBlocks(), config.thresholdBlocks())) {
            reasons.add("moved");
        }
        if (!targetSide && config.losEnabled() && hasLineOfSight != null
                && losInterferes(hasLineOfSight, config.losWhen())) {
            reasons.add(config.losWhen() == InterfereWhenVisible.VISIBLE
                    ? "line-of-sight" : "line-of-sight-hidden");
        }
        return reasons;
    }

    private static int enabledCount(Config config) {
        int count = 0;
        if (config.lightEnabled()) {
            count++;
        }
        if (config.undergroundEnabled()) {
            count++;
        }
        if (config.underwaterEnabled()) {
            count++;
        }
        if (config.altitudeEnabled()) {
            count++;
        }
        if (config.weatherEnabled()) {
            count++;
        }
        if (config.biomeEnabled()) {
            count++;
        }
        if (config.movementEnabled()) {
            count++;
        }
        if (config.losEnabled()) {
            count++;
        }
        return count;
    }

    /**
     * Light interferes when a reading drops below its minimum: either
     * reading for ONE_UNMET, both for BOTH_UNMET. Outside the overworld
     * the option is skipped and never interferes. Pure.
     */
    public static boolean lightInterferes(Snapshot snapshot, int minSky, int minBlock,
            InterfereWhen when) {
        if (!snapshot.normalEnvironment()) {
            return false;
        }
        boolean skyUnmet = snapshot.skyLight() < minSky;
        boolean blockUnmet = snapshot.blockLight() < minBlock;
        return when == InterfereWhen.BOTH_UNMET ? skyUnmet && blockUnmet : skyUnmet || blockUnmet;
    }

    /** Cover interferes when solid blocks above exceed the maximum. Pure. */
    public static boolean undergroundInterferes(int solidBlocksAbove, int maxBlocksAbove) {
        return solidBlocksAbove > maxBlocksAbove;
    }

    /**
     * Water interferes only with water feet: the feet block must be
     * water, and the fluid blocks above must exceed the maximum. Pure.
     */
    public static boolean underwaterInterferes(Snapshot snapshot, int maxFluidAbove) {
        return snapshot.feetInWater() && snapshot.fluidBlocksAbove() > maxFluidAbove;
    }

    /** Movement interferes past the threshold, in blocks. Pure. */
    public static boolean movementInterferes(double movedBlocks, double thresholdBlocks) {
        return movedBlocks > thresholdBlocks;
    }

    /**
     * Sight interferes when the reading matches the mode: a clear ray for
     * VISIBLE, a blocked or over-range ray for NOT_VISIBLE. Pure.
     */
    public static boolean losInterferes(boolean hasLineOfSight, InterfereWhenVisible when) {
        return when == InterfereWhenVisible.NOT_VISIBLE ? !hasLineOfSight : hasLineOfSight;
    }

    /** Height interferes outside the inclusive Y range. Pure. */
    public static boolean altitudeInterferes(int blockY, int minY, int maxY) {
        return blockY < minY || blockY > maxY;
    }

    /** Weather interferes when the current bucket is listed. Pure. */
    public static boolean weatherInterferes(Weather weather, Set<Weather> interfereDuring) {
        return weather != null && interfereDuring.contains(weather);
    }

    /** Biome interferes when the spot's key is listed. Pure. */
    public static boolean biomeInterferes(String biomeKey, Set<String> interfereIn) {
        return biomeKey != null && interfereIn.contains(biomeKey.toLowerCase(Locale.ROOT));
    }
}
