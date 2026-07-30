package com.jruk8.jmanhunt.extras.world_engine;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

public record WorldEngineConfig(
        boolean enabled,
        String worldName,
        int cellSize,
        int tpSpreadRadius,
        Location lobbyLocation) {
    private static final int DEFAULT_CELL_SIZE = 10_000;
    private static final int MAX_CELL_SIZE = 50_000;

    public static WorldEngineConfig fromConfig(FileConfiguration config) {
        String base = "extras.world-engine.";
        int configuredCellSize = config.getInt(base + "cell-size", DEFAULT_CELL_SIZE);
        int cellSize = Math.clamp(configuredCellSize, 1, MAX_CELL_SIZE);
        int spreadRadius = Math.max(0, config.getInt(base + "tp-spread-radius", 5));
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
        return new WorldEngineConfig(
                config.getBoolean(base + "enabled", false),
                worldName,
                cellSize,
                spreadRadius,
                lobby
        );
    }
}
