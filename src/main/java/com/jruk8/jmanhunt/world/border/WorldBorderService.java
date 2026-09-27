package com.jruk8.jmanhunt.world.border;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.JManhuntPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.List;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.world.cell.CellCoordinate;
import com.jruk8.jmanhunt.world.cell.CellOrigin;
import com.jruk8.jmanhunt.world.cell.SpiralCoordinateMapper;
import com.jruk8.jmanhunt.world.teleport.MatchTeleportService;

/** Real world borders: cell borders, the start border, and tracking. */
public final class WorldBorderService {
    private final JManhuntPlugin plugin;
    private final ConfigService configService;

    // Tracks the start-border state so it can be expanded when the game begins.
    private boolean startBorderActive;
    private World startBorderWorld;
    private CellOrigin startBorderOrigin;
    private WorldEngineConfig startBorderConfig;
    private BukkitTask startBorderTask;

    // Names of the worlds whose borders this match touched, so they can be
    // restored to vanilla defaults even when the engine or the border itself
    // is disabled afterwards.
    private final List<String> borderedWorldNames = new ArrayList<>();

    public WorldBorderService(JManhuntPlugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    /**
     * True when a real border may be written to the world: never null,
     * never the lobby world, and only overworld or nether game worlds.
     * The End and everything else stay vanilla. Pure for tests.
     */
    public static boolean isBorderable(World world, String lobbyWorldName) {
        if (world == null) {
            return false;
        }
        if (lobbyWorldName != null && world.getName().equalsIgnoreCase(lobbyWorldName)) {
            return false;
        }
        World.Environment environment = world.getEnvironment();
        return environment == World.Environment.NORMAL
                || environment == World.Environment.NETHER;
    }

    /** Configured lobby world name, live-read so renames apply on reload. */
    private String lobbyWorldName() {
        return configService.getString("world-engine.lobby-world-name", "jmh_lobby");
    }

    /**
     * Called when the game actually begins (via speedrunner damage or force start).
     * If the start-border is active, expands it to the full cell size.
     */
    public void onBeginGame() {
        if (!startBorderActive || startBorderWorld == null || startBorderConfig == null) {
            return;
        }

        if (startBorderConfig.skipFadeout()) {
            // Snap to cell size immediately, no animation.
            applyCellBorderSize(startBorderWorld, startBorderConfig, startBorderOrigin);
        } else {
            // Animate the expansion over the configured fadeout time.
            int fadeoutSeconds = startBorderConfig.startBorderFadeoutTime();
            applyCellBorderSize(startBorderWorld, startBorderConfig, startBorderOrigin, fadeoutSeconds);
        }

        startBorderActive = false;
    }

    /** Clears real borders and start-border state, e.g. when matches go concurrent. */
    public void clearInstanceBorders() {
        resetTrackedBorders();
        if (startBorderTask != null) {
            startBorderTask.cancel();
            startBorderTask = null;
        }
        startBorderActive = false;
    }

    /**
     * Applies the real world border for one cell, e.g. when concurrency
     * drops back to a single match. Honors the start-border phase for
     * matches that have not begun yet.
     */
    public void applyInstanceBorder(long cellIndex, boolean begun) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(configService);
        if (!config.enabled() || !config.worldBorderEnabled()) {
            return;
        }
        World world = Bukkit.getWorld(config.worldName());
        if (world == null || !isBorderable(world, lobbyWorldName())) {
            return;
        }
        CellCoordinate grid = SpiralCoordinateMapper.toCoordinate(cellIndex);
        CellOrigin origin = new CellOrigin(
                MatchTeleportService.toBlockCoordinate(grid.x() * config.cellSize()),
                MatchTeleportService.toBlockCoordinate(grid.z() * config.cellSize()),
                cellIndex);
        if (begun) {
            trackBorderedWorlds(world);
            applyCellBorderSize(world, config, origin);
        } else {
            setWorldBorder(world, config, origin);
        }
    }

    /**
     * Sets the world border for the overworld and its corresponding Nether world.
     * If start-border is active, sets the initial smaller border; otherwise sets
     * the full cell size border directly.
     */
    public void setWorldBorder(World overworld, WorldEngineConfig config, CellOrigin origin) {
        if (!config.worldBorderEnabled()) {
            // A border from an earlier match must not survive being disabled.
            resetTrackedBorders();
            return;
        }
        if (!isBorderable(overworld, lobbyWorldName())) {
            plugin.logger().warning("Refusing to set a world border on '"
                    + overworld.getName() + "': borders apply to overworld and nether "
                    + "game worlds only, never the lobby world.");
            return;
        }
        trackBorderedWorlds(overworld);

        // Check if start-border should be used (requires start-on-speedrunner-damage enabled).
        boolean startOnDamage = configService.getBoolean("settings.match.start-on-speedrunner-damage.enabled", false);
        boolean useStartBorder = config.startBorderActive() && startOnDamage;

        if (useStartBorder) {
            // Set the initial smaller start border.
            int startDiameter = config.startBorderDiameter();
            WorldBorder border = overworld.getWorldBorder();
            border.setCenter(origin.x(), origin.z());
            border.setSize(startDiameter);
            border.setWarningDistance(warningDistance(startDiameter));
            border.setDamageBuffer(config.damageBuffer());
            border.setDamageAmount(config.damageAmount());

            World nether = writableNether(overworld);
            if (nether != null) {
                WorldBorder netherBorder = nether.getWorldBorder();
                netherBorder.setCenter(origin.x() / 8.0, origin.z() / 8.0);
                netherBorder.setSize(startDiameter / 8.0);
                netherBorder.setWarningDistance(warningDistance(startDiameter / 8.0));
                netherBorder.setDamageBuffer(config.damageBuffer());
                netherBorder.setDamageAmount(config.damageAmount());
            }

            // Store state for onBeginGame() to expand the border later.
            startBorderActive = true;
            startBorderWorld = overworld;
            startBorderOrigin = origin;
            startBorderConfig = config;
        } else {
            // Set the full cell size border directly.
            applyCellBorderSize(overworld, config, origin);
        }
    }

    /**
     * Applies the full cell size border to the overworld and Nether.
     */
    private void applyCellBorderSize(World overworld, WorldEngineConfig config, CellOrigin origin) {
        if (!isBorderable(overworld, lobbyWorldName())) {
            return;
        }
        WorldBorder border = overworld.getWorldBorder();
        border.setCenter(origin.x(), origin.z());
        border.setSize(config.cellSize());
        border.setWarningDistance(warningDistance(config.cellSize()));
        border.setDamageBuffer(config.damageBuffer());
        border.setDamageAmount(config.damageAmount());

        World nether = writableNether(overworld);
        if (nether == null) {
            return;
        }

        WorldBorder netherBorder = nether.getWorldBorder();
        netherBorder.setCenter(origin.x() / 8.0, origin.z() / 8.0);
        netherBorder.setSize(config.cellSize() / 8.0);
        netherBorder.setWarningDistance(warningDistance(config.cellSize() / 8.0));
        netherBorder.setDamageBuffer(config.damageBuffer());
        netherBorder.setDamageAmount(config.damageAmount());
    }

    /**
     * Border warning distance for an applied border size: a hundredth of
     * the size, at least one block. Each world scales by its own applied
     * size, so the Nether (1/8 scale) warns the same effective distance.
     * Pure for tests.
     */
    public static int warningDistance(double borderSize) {
        return Math.max(1, (int) (borderSize / 100.0));
    }

    /**
     * Applies the full cell size border to the overworld and Nether with
     * an animated transition over the given duration in seconds.
     */
    @SuppressWarnings("removal") // setSize(double, long) is the only animated overload available
    private void applyCellBorderSize(World overworld, WorldEngineConfig config, CellOrigin origin, int seconds) {
        if (!isBorderable(overworld, lobbyWorldName())) {
            return;
        }
        WorldBorder border = overworld.getWorldBorder();
        border.setCenter(origin.x(), origin.z());
        border.setSize(config.cellSize(), seconds);
        border.setWarningDistance(warningDistance(config.cellSize()));
        border.setDamageBuffer(config.damageBuffer());
        border.setDamageAmount(config.damageAmount());

        World nether = writableNether(overworld);
        if (nether == null) {
            return;
        }

        WorldBorder netherBorder = nether.getWorldBorder();
        netherBorder.setCenter(origin.x() / 8.0, origin.z() / 8.0);
        netherBorder.setSize(config.cellSize() / 8.0, seconds);
        netherBorder.setWarningDistance(warningDistance(config.cellSize() / 8.0));
        netherBorder.setDamageBuffer(config.damageBuffer());
        netherBorder.setDamageAmount(config.damageAmount());
    }

    public void clearWorldBorder(World world) {
        WorldBorder border = world.getWorldBorder();
        border.reset();
    }

    private void trackBorderedWorlds(World overworld) {
        trackBorderedWorld(overworld);
        World nether = getNetherWorld(overworld);
        if (nether != null) {
            trackBorderedWorld(nether);
        }
    }

    private void trackBorderedWorld(World world) {
        if (world != null && isBorderable(world, lobbyWorldName())
                && !borderedWorldNames.contains(world.getName())) {
            borderedWorldNames.add(world.getName());
        }
    }

    /**
     * Restores vanilla border defaults (center 0, 0, size 59999968) on every
     * world bordered during the match.
     */
    private void resetTrackedBorders() {
        for (String name : borderedWorldNames) {
            World world = Bukkit.getWorld(name);
            if (world != null && isBorderable(world, lobbyWorldName())) {
                world.getWorldBorder().reset();
            }
        }
        borderedWorldNames.clear();
    }

    /**
     * Nether companion of a bordered overworld, or null when missing or
     * unborderable. Warns either way so a misconfiguration is visible.
     */
    private World writableNether(World overworld) {
        World nether = getNetherWorld(overworld);
        if (nether == null) {
            plugin.logger().warning("Could not find matching Nether world for '"
                    + overworld.getName() + "'. Skipping Nether world border sync.");
            return null;
        }
        if (!isBorderable(nether, lobbyWorldName())) {
            plugin.logger().warning("Refusing to set a world border on '"
                    + nether.getName() + "': borders apply to overworld and nether "
                    + "game worlds only, never the lobby world.");
            return null;
        }
        return nether;
    }

    private World getNetherWorld(World overworld) {
        if (overworld.getEnvironment() == World.Environment.NETHER) {
            return overworld; // already the Nether, guard against double-wrapping
        }
        return Bukkit.getWorld(overworld.getName() + "_nether");
    }
}
