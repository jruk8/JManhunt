package com.jruk8.jmanhunt.world;

import com.jruk8.jmanhunt.config.WorldEngineConfig.SpawnpointAlgorithm;
import com.jruk8.jmanhunt.config.WorldEngineConfig.WorldBorder;
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
        String endBaseName,
        int endBuffer) {
    private static final int MAX_CELL_SIZE = 50_000;
    private static final String DEFAULT_END_BASE_NAME = "jmh_end";
    private static final List<String> DEFAULT_STRUCTURE_WORDS =
            List.of("VILLAGE", "TEMPLE", "SHIPWRECK", "RUINED_PORTAL");

    private record SpawnpointSettings(boolean enabled, int maxRetries, int yTolerance,
            boolean closeToEnabled, List<String> closeToWords, int closeToAttempts,
            int closeToMaxDistance) {
    }

    private static SpawnpointSettings spawnpointSettings(SpawnpointAlgorithm spawnpoint) {
        var closeTo = spawnpoint.getSpawnCloseToStructure();
        List<String> closeToWords = closeTo.getStructures();
        if (closeToWords.isEmpty()) {
            closeToWords = DEFAULT_STRUCTURE_WORDS;
        }
        return new SpawnpointSettings(spawnpoint.isEnabled(),
                Math.max(0, spawnpoint.getMaxRetries()),
                Math.max(0, spawnpoint.getYTolerance()),
                closeTo.isEnabled(),
                closeToWords,
                Math.clamp(closeTo.getAttempts(), 1, 5),
                Math.clamp(closeTo.getMaxDistance(), 50, 200));
    }

    public static WorldEngineConfig fromSettings(
            com.jruk8.jmanhunt.config.WorldEngineConfig settings) {
        int cellSize = Math.clamp(settings.getCellSize(), 1, MAX_CELL_SIZE);
        int spreadRadius = Math.clamp(settings.getTpSpreadRadius(), 0, cellSize / 2);
        String worldName = settings.getWorldName();
        if (worldName.isBlank()) {
            worldName = "world";
        }
        SpawnpointSettings spawnpoint = spawnpointSettings(settings.getSpawnpointAlgorithm());
        WorldBorder border = settings.getWorldBorder();
        var damage = border.getDamage();
        var startBorder = border.getStartBorder();
        var end = settings.getEnd();
        String baseName = end.getBaseName();
        if (baseName.isBlank()) {
            baseName = DEFAULT_END_BASE_NAME;
        }
        return new WorldEngineConfig(
                settings.isEnabled(),
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
                border.isEnabled(),
                Math.max(0, damage.getBuffer()),
                Math.max(0, damage.getAmount()),
                startBorder.isEnabled(),
                startBorder.getRadius(),
                baseName,
                Math.max(1, end.getBuffer())
        );
    }

    /**
     * Calculates the start border diameter in blocks.
     * <p>
     * The radius is the larger of the configured start-border radius and
     * tp-spread-radius + 1 (so players never spawn outside the border).
     * A configured radius of -1 means use tp-spread-radius + 1 only.
     * Sizes are diameters: radius * 2.
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
     * Returns true while an unbegun match should confine to the start
     * diameter: the start border must be switched on, not merely unbegun.
     * Enforcement and particles share this so both use the same box.
     *
     * @param begun whether the match has begun
     * @return true if the start diameter applies
     */
    public boolean useStartBorder(boolean begun) {
        return !begun && startBorderActive();
    }
}
