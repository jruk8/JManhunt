package com.jruk8.jmanhunt.settings.world_engine;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

public record WorldEngineConfig(
        boolean enabled,
        String worldName,
        int cellSize,
        int tpSpreadRadius,
        Location lobbyLocation,
        boolean worldBorderEnabled,
        double damageBuffer,
        double damageAmount,
        boolean startBorderEnabled,
        int startBorderRadius,
        int startBorderFadeoutTime) {
    private static final int DEFAULT_CELL_SIZE = 10_000;
    private static final int MAX_CELL_SIZE = 50_000;
    private static final int DEFAULT_START_BORDER_RADIUS = 10;
    private static final int DEFAULT_START_BORDER_FADEOUT_TIME = 5;
    private static final double DEFAULT_DAMAGE_BUFFER = 5.0;
    private static final double DEFAULT_DAMAGE_AMOUNT = 1.0;

    public static WorldEngineConfig fromConfig(FileConfiguration config) {
        String base = "settings.world-engine.";
        int configuredCellSize = config.getInt(base + "cell-size", DEFAULT_CELL_SIZE);
        int cellSize = Math.clamp(configuredCellSize, 1, MAX_CELL_SIZE);
        int spreadRadius = Math.clamp(config.getInt(base + "tp-spread-radius", 5), 0, cellSize / 2);
        String worldName = config.getString(base + "world-name", "world");
        if (worldName.isBlank()) worldName = "world";
        String lobbyWorld = config.getString(base + "lobby-location.world", worldName);
        Location lobby = new Location(
                org.bukkit.Bukkit.getWorld(lobbyWorld),
                config.getDouble(base + "lobby-location.x", 0.5),
                config.getDouble(base + "lobby-location.y", 100.0),
                config.getDouble(base + "lobby-location.z", 0.5),
                (float) config.getDouble(base + "lobby-location.yaw", 0.0),
                (float) config.getDouble(base + "lobby-location.pitch", 0.0)
        );
        String borderBase = base + "world-border.";
        boolean worldBorderEnabled = config.getBoolean(borderBase + "enabled", false);
        double damageBuffer = Math.max(0, config.getDouble(borderBase + "damage.buffer", DEFAULT_DAMAGE_BUFFER));
        double damageAmount = Math.max(0, config.getDouble(borderBase + "damage.amount", DEFAULT_DAMAGE_AMOUNT));
        String startBorderBase = borderBase + "start-border.";
        boolean startBorderEnabled = config.getBoolean(startBorderBase + "enabled", false);
        int startBorderRadius = config.getInt(startBorderBase + "radius", DEFAULT_START_BORDER_RADIUS);
        int startBorderFadeoutTime = config.getInt(startBorderBase + "fadeout-time", DEFAULT_START_BORDER_FADEOUT_TIME);
        return new WorldEngineConfig(
                config.getBoolean(base + "enabled", false),
                worldName,
                cellSize,
                spreadRadius,
                lobby,
                worldBorderEnabled,
                damageBuffer,
                damageAmount,
                startBorderEnabled,
                startBorderRadius,
                startBorderFadeoutTime
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