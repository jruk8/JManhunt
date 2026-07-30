package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/** Executes built-in and configured actions at match state transitions. */
public final class GameStateCommandManager {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final ConfigService configService;

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
                runCommands("custom-modifiers." + name + ".commands.player-cleanup", player.getName(), winner);
            }
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
            boolean setNoneSpectator = plugin.getConfig().getBoolean("extras.set-none-gamemode-spectator", true);
            Bukkit.getOnlinePlayers().forEach(player -> {
                if (phase.equals("start") && playerStates.role(player) == Role.NONE) {
                    if (setNoneSpectator) player.setGameMode(GameMode.SPECTATOR);
                    return;
                }
                player.setGameMode(GameMode.SURVIVAL);
            });
        }
        if (phase.equals("start")) {
            if (plugin.getConfig().getBoolean(path + "disable-locator-bar", false)) {
                Bukkit.getWorlds().forEach(world -> world.setGameRule(GameRules.LOCATOR_BAR, false));
            }
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
                runCommands(base + "player-commands." + phase, player.getName(), winner);
            }
        }
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            if (phase.equals("start")) {
                String modifier = "custom-modifiers." + name + ".commands.";
                runCommands(modifier + "console", null, winner);
                for (Player player : participatingPlayers()) {
                    runCommands(modifier + "player", player.getName(), winner);
                    String roleCommands = playerStates.role(player) == Role.HUNTER
                            ? "hunter-commands" : "speedrunner-commands";
                    runCommands(modifier + roleCommands, player.getName(), winner);
                }
            }
        }
    }

    private void runCommands(String path, String playerName, String winner) {
        for (String command : plugin.getConfig().getStringList(path)) {
            if (command.isBlank()) continue;
            try {
                String parsed = command.replace("<winner>", winner == null ? "" : winner);
                if (playerName != null) parsed = parsed.replace("<p>", playerName);
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