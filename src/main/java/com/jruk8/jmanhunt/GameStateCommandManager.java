package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Executes built-in and configured actions at match state transitions. */
public final class GameStateCommandManager {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;

    public GameStateCommandManager(JManhuntPlugin plugin, PlayerStateStore playerStates) {
        this.plugin = plugin;
        this.playerStates = playerStates;
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
        for (String name : modifierNames()) {
            if (!modifierEnabled(name)) continue;
            runCommands("custom-modifiers." + name + ".commands.console-cleanup", null, winner);
        }
    }

    public void runPlayerCleanup(String winner) {
        for (String name : modifierNames()) {
            if (!modifierEnabled(name)) continue;
            for (Player player : participatingPlayers()) {
                runCommands("custom-modifiers." + name + ".commands.player-cleanup", player.getName(), winner);
            }
        }
    }

    public Set<String> settingNames() {
        Set<String> names = new TreeSet<>();
        var defaults = plugin.getConfig().getConfigurationSection("gamestate-commands.default-commands");
        if (defaults != null) {
            if (defaults.contains("enabled")) names.add("default-commands.enabled");
            for (String phase : List.of("start", "end")) {
                var section = defaults.getConfigurationSection(phase);
                if (section != null) for (String key : section.getKeys(false)) {
                    names.add("default-commands." + phase + "." + key);
                }
            }
        }
        for (String name : modifierNames()) names.add("custom-modifiers." + name);
        names.addAll(extraModifierNames());
        return names;
    }

    public boolean getSetting(String setting) {
        String path = settingPath(setting);
        return path != null && plugin.getConfig().getBoolean(path);
    }

    public boolean setSetting(String setting, boolean value) {
        String path = settingPath(setting);
        if (path == null) return false;
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
        return true;
    }

    private String settingPath(String setting) {
        if (setting.startsWith("default-commands.")) {
            String path = "gamestate-commands." + setting;
            return plugin.getConfig().contains(path) && !plugin.getConfig().isConfigurationSection(path) ? path : null;
        }
        if (setting.startsWith("custom-modifiers.")) {
            String path = setting.replaceFirst("^custom-modifiers\\.", "custom-modifiers.") + ".enabled";
            return plugin.getConfig().contains(path) ? path : null;
        }
        if (setting.startsWith("extras.")) {
            String enabledPath = setting + ".enabled";
            if (plugin.getConfig().contains(enabledPath)) return enabledPath;
            if (plugin.getConfig().contains(setting)) return setting;
            return null;
        }
        return null;
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
        for (String name : modifierNames()) {
            if (!modifierEnabled(name)) continue;
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

    private Set<String> modifierNames() {
        var section = plugin.getConfig().getConfigurationSection("custom-modifiers");
        return section == null ? Set.of() : section.getKeys(false);
    }

    private Set<String> extraModifierNames() {
        Set<String> names = new TreeSet<>();
        collectExtraModifierNames(plugin.getConfig().getConfigurationSection("extras"), "", names);
        return names;
    }

    private void collectExtraModifierNames(ConfigurationSection section, String prefix, Set<String> names) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null && child.contains("enabled")) {
                names.add("extras." + path);
            } else if (child != null) {
                collectExtraModifierNames(child, path, names);
            } else if (section.isBoolean(key)) {
                names.add("extras." + path);
            }
        }
    }

    private boolean modifierEnabled(String name) {
        return plugin.getConfig().getBoolean("custom-modifiers." + name + ".enabled", false);
    }

    private void clearAdvancements() {
        Bukkit.getOnlinePlayers().forEach(player -> Bukkit.advancementIterator().forEachRemaining(advancement ->
                player.getAdvancementProgress(advancement).getAwardedCriteria().forEach(criteria ->
                        player.getAdvancementProgress(advancement).revokeCriteria(criteria))));
    }
}
