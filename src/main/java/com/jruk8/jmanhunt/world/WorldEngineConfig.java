package com.jruk8.jmanhunt.world;

import com.jruk8.jmanhunt.config.ConfigService;
import java.util.List;

public record WorldEngineConfig(
        boolean enabled,
        String worldName,
        int cellSize,
        int tpSpreadRadius,
        boolean spawnpointAlgorithmEnabled,
        int spawnpointMaxRetries,
        int spawnpointYTolerance,
        boolean spawnCloseToStructureEnabled,
        List<String> spawnCloseToStructureWords,
        int spawnCloseToStructureAttempts,
        int spawnCloseToStructureMaxDistance,
        boolean worldBorderEnabled,
        double damageBuffer,
        double damageAmount,
        boolean startBorderEnabled,
        int startBorderRadius,
        int startBorderFadeoutTime,
        String endBaseName,
        int endBuffer) {
    private static final int DEFAULT_CELL_SIZE = 10_000;
    private static final int MAX_CELL_SIZE = 50_000;
    private static final int DEFAULT_START_BORDER_RADIUS = 10;
    private static final int DEFAULT_START_BORDER_FADEOUT_TIME = 5;
    private static final double DEFAULT_DAMAGE_BUFFER = 5.0;
    private static final double DEFAULT_DAMAGE_AMOUNT = 1.0;
    private static final String DEFAULT_END_BASE_NAME = "jmh_end";
    private static final int DEFAULT_END_BUFFER = 3;

    private record SpawnpointSettings(boolean enabled, int maxRetries, int yTolerance,
            boolean closeToEnabled, List<String> closeToWords, int closeToAttempts,
            int closeToMaxDistance) {
    }

    private static SpawnpointSettings spawnpointSettings(ConfigService config, String spawnpointBase) {
        boolean enabled = config.getBoolean(spawnpointBase + "enabled", true);
        int maxRetries = Math.max(0, config.getInt(spawnpointBase + "max-retries", 8));
        int yTolerance = Math.max(0, config.getInt(spawnpointBase + "y-tolerance", 7));
        String closeToBase = spawnpointBase + "spawn-close-to-structure.";
        boolean closeToEnabled = config.getBoolean(closeToBase + "enabled", false);
        List<String> closeToWords = config.getStringList(closeToBase + "structures");
        if (closeToWords.isEmpty()) {
            closeToWords = List.of("VILLAGE", "TEMPLE", "SHIPWRECK", "RUINED_PORTAL");
        }
        int closeToAttempts = Math.clamp(config.getInt(closeToBase + "attempts", 3), 1, 5);
        int closeToMaxDistance =
                Math.clamp(config.getInt(closeToBase + "max-distance", 125), 50, 200);
        return new SpawnpointSettings(enabled, maxRetries, yTolerance, closeToEnabled,
                closeToWords, closeToAttempts, closeToMaxDistance);
    }

    private record EndSettings(String baseName, int buffer) {
    }

    private static EndSettings endSettings(ConfigService config, String base) {
        String endBase = base + "end.";
        String baseName = config.getString(endBase + "base-name", DEFAULT_END_BASE_NAME);
        if (baseName.isBlank()) {
            baseName = DEFAULT_END_BASE_NAME;
        }
        int buffer = Math.max(1, config.getInt(endBase + "buffer", DEFAULT_END_BUFFER));
        return new EndSettings(baseName, buffer);
    }

    public static WorldEngineConfig fromConfig(ConfigService config) {
        String base = "world-engine.";
        int configuredCellSize = config.getInt(base + "cell-size", DEFAULT_CELL_SIZE);
        int cellSize = Math.clamp(configuredCellSize, 1, MAX_CELL_SIZE);
        int spreadRadius = Math.clamp(config.getInt(base + "tp-spread-radius", 5), 0, cellSize / 2);
        String worldName = config.getString(base + "world-name", "world");
        if (worldName.isBlank()) {
            worldName = "world";
        }
        SpawnpointSettings spawnpoint = spawnpointSettings(config, base + "spawnpoint-algorithm.");
        String borderBase = base + "world-border.";
        boolean worldBorderEnabled = config.getBoolean(borderBase + "enabled", false);
        double damageBuffer = Math.max(0, config.getDouble(borderBase + "damage.buffer", DEFAULT_DAMAGE_BUFFER));
        double damageAmount = Math.max(0, config.getDouble(borderBase + "damage.amount", DEFAULT_DAMAGE_AMOUNT));
        String startBorderBase = borderBase + "start-border.";
        boolean startBorderEnabled = config.getBoolean(startBorderBase + "enabled", false);
        int startBorderRadius = config.getInt(startBorderBase + "radius", DEFAULT_START_BORDER_RADIUS);
        int startBorderFadeoutTime = config.getInt(startBorderBase + "fadeout-time", DEFAULT_START_BORDER_FADEOUT_TIME);
        EndSettings end = endSettings(config, base);
        return new WorldEngineConfig(
                config.getBoolean(base + "enabled", false),
                worldName,
                cellSize,
                spreadRadius,
                spawnpoint.enabled(),
                spawnpoint.maxRetries(),
                spawnpoint.yTolerance(),
                spawnpoint.closeToEnabled(),
                spawnpoint.closeToWords(),
                spawnpoint.closeToAttempts(),
                spawnpoint.closeToMaxDistance(),
                worldBorderEnabled,
                damageBuffer,
                damageAmount,
                startBorderEnabled,
                startBorderRadius,
                startBorderFadeoutTime,
                end.baseName(),
                end.buffer()
        );
    }

    /**
     * Calculates the start border diameter in blocks.
     * <p>
     * The radius is the larger of the configured start-border radius and
     * tp-spread-radius + 1 (so players never spawn outside the border).
     * A configured radius of -1 means use tp-spread-radius + 1 only.
     * The diameter is radius * 2 (Bukkit's WorldBorder.setSize takes diameter).
     *
     * @param tpSpreadRadius the tp-spread-radius value
     * @param startBorderRadius the configured start-border radius (-1 = use spread only)
     * @return the start border diameter in blocks
     */
    public static int calculateStartBorderDiameter(int tpSpreadRadius, int startBorderRadius) {
        int spreadBasedRadius = tpSpreadRadius + 1;
        int effectiveRadius = startBorderRadius == -1
                ? spreadBasedRadius
                : Math.max(startBorderRadius, spreadBasedRadius);
        return effectiveRadius * 2;
    }

    /**
     * Returns the start border diameter for this config instance.
     *
     * @return the start border diameter in blocks
     */
    public int startBorderDiameter() {
        return calculateStartBorderDiameter(tpSpreadRadius, startBorderRadius);
    }

    /**
     * Returns true if the start border should be used.
     * Requires world-border enabled, start-border enabled, and
     * start-on-speedrunner-damage enabled (checked by caller).
     *
     * @return true if start border is active
     */
    public boolean startBorderActive() {
        return worldBorderEnabled && startBorderEnabled;
    }

    /**
     * Returns true if the fadeout animation should be skipped (instant snap).
     * Both 0 and -1 mean no animation.
     *
     * @return true if the border should snap to cell size immediately
     */
    public boolean skipFadeout() {
        return startBorderFadeoutTime <= 0;
    }
}