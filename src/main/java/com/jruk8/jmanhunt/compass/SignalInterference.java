package com.jruk8.jmanhunt.compass;

import java.util.HashSet;
import java.util.Locale;
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

    /**
     * One evaluated spot: light levels, whether the world is an overworld,
     * solid blocks strictly above the feet, feet Y, weather, and the block
     * biome key such as "minecraft:desert" (lowercased).
     */
    public record Snapshot(
            int skyLight,
            int blockLight,
            boolean normalEnvironment,
            int solidBlocksAbove,
            int blockY,
            Weather weather,
            String biomeKey) {
        public Snapshot {
            biomeKey = biomeKey == null ? "" : biomeKey.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Resolved interference config values. The compact constructor clamps
     * every range (light 0-15, cover 1-380, Y -64-319, bypass 0-1),
     * normalizes the altitude endpoints so min <= max, defaults a missing
     * interfere-when to ONE_UNMET, and lowercases the biome list.
     * Required-to-fail is clamped against the enabled count at verdict
     * time instead, since this record does not count enablers.
     */
    public record Config(
            boolean lightEnabled,
            int minSkyLight,
            int minBlockLight,
            InterfereWhen interfereWhen,
            boolean undergroundEnabled,
            int maxBlocksAbove,
            boolean altitudeEnabled,
            int minY,
            int maxY,
            boolean weatherEnabled,
            Set<Weather> interfereDuring,
            boolean biomeEnabled,
            Set<String> interfereIn,
            int requiredToFail,
            boolean twoWay,
            double chanceToBypass) {
        public Config {
            minSkyLight = clamp(minSkyLight, 0, 15);
            minBlockLight = clamp(minBlockLight, 0, 15);
            interfereWhen = interfereWhen == null ? InterfereWhen.ONE_UNMET : interfereWhen;
            maxBlocksAbove = clamp(maxBlocksAbove, 1, 380);
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
            chanceToBypass = Math.min(1.0, Math.max(0.0, chanceToBypass));
        }

        private static int clamp(int value, int min, int max) {
            return Math.min(max, Math.max(min, value));
        }
    }

    /** True when the holder's tracking fails with a bad signal. Pure. */
    public static boolean badSignal(Snapshot holder, Snapshot target, Config config, double roll) {
        int enabled = enabledCount(config);
        if (enabled == 0) {
            return false;
        }
        int required = Math.min(enabled, Math.max(1, config.requiredToFail()));
        boolean bad = interferingCount(holder, config) >= required
                || (config.twoWay() && target != null && interferingCount(target, config) >= required);
        if (!bad) {
            return false;
        }
        return !(roll < config.chanceToBypass());
    }

    /** Enabled sub-options reporting interference at one spot. Pure. */
    static int interferingCount(Snapshot snapshot, Config config) {
        int count = 0;
        if (config.lightEnabled()
                && lightInterferes(snapshot, config.minSkyLight(),
                        config.minBlockLight(), config.interfereWhen())) {
            count++;
        }
        if (config.undergroundEnabled()
                && undergroundInterferes(snapshot.solidBlocksAbove(), config.maxBlocksAbove())) {
            count++;
        }
        if (config.altitudeEnabled()
                && altitudeInterferes(snapshot.blockY(), config.minY(), config.maxY())) {
            count++;
        }
        if (config.weatherEnabled() && weatherInterferes(snapshot.weather(), config.interfereDuring())) {
            count++;
        }
        if (config.biomeEnabled() && biomeInterferes(snapshot.biomeKey(), config.interfereIn())) {
            count++;
        }
        return count;
    }

    private static int enabledCount(Config config) {
        int count = 0;
        if (config.lightEnabled()) {
            count++;
        }
        if (config.undergroundEnabled()) {
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
