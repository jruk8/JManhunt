package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes built-in and configured actions at match state transitions. */
public final class GameStateCommandManager {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final ConfigService configService;
    private final List<BukkitTask> intervalTasks = new ArrayList<>();

    public GameStateCommandManager(JManhuntPlugin plugin, PlayerStateStore playerStates, ConfigService configService) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.configService = configService;
    }

    public void runStart() {
        runDefault("start");
        runConfigured("start", "");
    }

    public void runEnd(String winner) {
        runConfigured("end", winner);
        runDefault("end");
    }

    public void runConsoleCleanup(String winner) {
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            runCommands("custom-modifiers." + name + ".commands.console-cleanup", null, winner);
        }
    }

    public void runPlayerCleanup(String winner) {
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            for (Player player : participatingPlayers()) {
                runCommands("custom-modifiers." + name + ".commands.player-cleanup", player, winner);
            }
        }
    }

    /**
     * Starts interval-based custom modifiers. Should be called when the game
     * begins (via {@link GameManager#beginGame()}). Modifiers with
     * {@code runs-on: INTERVAL} are scheduled on a repeating task.
     */
    public void startIntervalModifiers() {
        cancelIntervalModifiers();
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            String base = "custom-modifiers." + name + ".";
            String runsOn = plugin.getConfig().getString(base + "runs-on", "ON_START");
            if (!"INTERVAL".equals(runsOn)) continue;

            int intervalSeconds = plugin.getConfig().getInt(base + "interval-settings.interval", 60);
            if (intervalSeconds <= 0) continue;
            long intervalTicks = intervalSeconds * 20L;
            boolean runOnStart = plugin.getConfig().getBoolean(base + "interval-settings.run-on-start", false);

            if (runOnStart) {
                runModifierCommands(name, "");
            }
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> runModifierCommands(name, ""), intervalTicks, intervalTicks);
            intervalTasks.add(task);
        }
    }

    /** Cancels all running interval modifier tasks. */
    public void cancelIntervalModifiers() {
        for (BukkitTask task : intervalTasks) {
            task.cancel();
        }
        intervalTasks.clear();
    }

    private void runModifierCommands(String name, String winner) {
        String modifier = "custom-modifiers." + name + ".commands.";
        runCommands(modifier + "console", null, winner);
        for (Player player : participatingPlayers()) {
            runCommands(modifier + "player", player, winner);
            String roleCommands = playerStates.role(player) == Role.HUNTER
                    ? "hunter" : "speedrunner";
            runCommands(modifier + roleCommands, player, winner);
        }
    }

    private void runDefault(String phase) {
        if (!plugin.getConfig().getBoolean("gamestate-commands.default-commands.enabled", true)) return;
        String path = "gamestate-commands.default-commands." + phase + ".";
        if (plugin.getConfig().getBoolean(path + "reset-players-stats", false)) {
            participatingPlayers().forEach(player -> {
                player.getInventory().clear();
                player.setLevel(0);
                player.setExp(0.0f);
                player.clearActivePotionEffects();
                player.setHealth(Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue());
                player.setFoodLevel(20);
                clearAdvancements();
            });
        }
        if (plugin.getConfig().getBoolean(path + "auto-set-gamemode", false)) {
            boolean setNoneSpectator = plugin.getConfig().getBoolean("settings.set-none-gamemode-spectator.enabled", true);
            Bukkit.getOnlinePlayers().forEach(player -> {
                if (phase.equals("start") && playerStates.role(player) == Role.NONE) {
                    if (setNoneSpectator) player.setGameMode(GameMode.SPECTATOR);
                    return;
                }
                player.setGameMode(GameMode.SURVIVAL);
            });
        }
        var worlds = Bukkit.getWorlds();
        boolean disableLocatorBar = plugin.getConfig().getBoolean(path + "disable-locator-bar", false);
        worlds.forEach(world -> world.setGameRule(GameRules.LOCATOR_BAR, !disableLocatorBar));
        worlds.forEach(world -> world.setGameRule(GameRules.IMMEDIATE_RESPAWN,
                plugin.getConfig().getBoolean(path + "set-respawn-immediate", false)));
        if (phase.equals("start")) {
            if (plugin.getConfig().getBoolean(path + "set-daytime", false)) {
                Bukkit.getWorlds().forEach(this::setDaytime);
            }
        }
    }

    private void setDaytime(World world) {
        try {
            world.setTime(0L);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().fine("Skipping daytime reset in world without a world clock: " + world.getName());
        }
    }

    private void runConfigured(String phase, String winner) {
        String base = "gamestate-commands.";
        if (plugin.getConfig().getBoolean(base + "console-commands.enabled", false)) {
            runCommands(base + "console-commands." + phase, null, winner);
        }
        if (plugin.getConfig().getBoolean(base + "player-commands.enabled", false)) {
            for (Player player : participatingPlayers()) {
                runCommands(base + "player-commands." + phase, player, winner);
            }
        }
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            if (phase.equals("start")) {
                String runsOn = plugin.getConfig().getString("custom-modifiers." + name + ".runs-on", "ON_START");
                if ("INTERVAL".equals(runsOn)) continue; // interval modifiers are started by beginGame()
                runModifierCommands(name, winner);
            }
        }
    }

    private void runCommands(String path, Player player, String winner) {
        for (String command : plugin.getConfig().getStringList(path)) {
            if (command.isBlank()) continue;
            try {
                String playerName = player != null ? player.getName() : null;
                double x = player != null ? player.getLocation().getX() : 0.0;
                double y = player != null ? player.getLocation().getY() : 0.0;
                double z = player != null ? player.getLocation().getZ() : 0.0;
                String parsed = CommandPlaceholders.replace(command, playerName, winner, x, y, z);
                if (parsed.startsWith("/")) parsed = parsed.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to run command '%s'. Skipping..".formatted(command));
                e.printStackTrace();
            }
        }
    }

    private List<Player> participatingPlayers() {
        return Bukkit.getOnlinePlayers().stream().filter(player -> playerStates.role(player) != Role.NONE)
                .map(player -> (Player) player).toList();
    }

    private void clearAdvancements() {
        Bukkit.getOnlinePlayers().forEach(player -> Bukkit.advancementIterator().forEachRemaining(advancement ->
                player.getAdvancementProgress(advancement).getAwardedCriteria().forEach(criteria ->
                        player.getAdvancementProgress(advancement).revokeCriteria(criteria))));
    }
}