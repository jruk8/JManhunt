package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Executes built-in and configured actions at match state transitions. */
public final class GameStateCommandManager {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final ConfigService configService;
    private final LobbyTeleporter lobbyTeleporter;
    private final List<BukkitTask> intervalTasks = new ArrayList<>();
    private final List<BukkitTask> pendingDelayed = new ArrayList<>();

    public GameStateCommandManager(JManhuntPlugin plugin, PlayerStateStore playerStates,
                                   ConfigService configService, LobbyTeleporter lobbyTeleporter) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.configService = configService;
        this.lobbyTeleporter = lobbyTeleporter;
    }

    public void runStart() {
        cancelPendingDelayed();
        runDefault("start");
        runConfigured("start");
    }

    public void runEnd() {
        cancelPendingDelayed();
        runConfigured("end");
        runDefault("end");
    }

    /** Drops delayed modifier commands that never fired, e.g. at match end. */
    private void cancelPendingDelayed() {
        for (BukkitTask task : pendingDelayed) {
            task.cancel();
        }
        pendingDelayed.clear();
    }

    public void runConsoleCleanup() {
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            runCommands("custom-modifiers." + name + ".commands.console-cleanup", null);
        }
    }

    public void runPlayerCleanup() {
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            for (Player player : participatingPlayers()) {
                runCommands("custom-modifiers." + name + ".commands.player-cleanup", player);
            }
        }
    }

    /**
     * Starts interval-based custom modifiers. Should be called when the game
     * begins (via {@link GameManager#beginGame()}). Modifiers whose
     * {@code runs-on} list contains INTERVAL are scheduled on a repeating task.
     * Supports decimal intervals: 0-0.05 seconds executes every tick, rounds to
     * the nearest tick.
     */
    public void startIntervalModifiers() {
        cancelIntervalModifiers();
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            String base = "custom-modifiers." + name + ".";
            if (!runsOnContains(name, "INTERVAL")) continue;

            double intervalSeconds = plugin.getConfig().getDouble(base + "interval-settings.interval", 60.0);
            if (intervalSeconds < 0) continue;
            // Round to nearest tick, with 0-0.05 seconds executing every tick (1 tick)
            long intervalTicks = Math.max(1, (long) Math.round(intervalSeconds * 20D));
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> runModifierCommands(name), intervalTicks, intervalTicks);
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

    /**
     * Runs modifiers whose {@code runs-on} list contains the given event for
     * the specific player involved in the event. The player's own
     * {@code player}/{@code hunter}/{@code speedrunner} commands run, plus the
     * modifier's console commands.
     *
     * @param event  the event name (e.g. ON_EVERY_KILL)
     * @param player the player involved in the event
     */
    public void runEventModifiers(String event, Player player) {
        if (player == null) return;
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            if (!runsOnContains(name, event)) continue;
            runModifierWithDelay(name, () -> {
                String modifier = "custom-modifiers." + name + ".commands.";
                runCommands(modifier + "console", null);
                runCommands(modifier + "player", player);
                String roleCommands = playerStates.role(player) == Role.HUNTER
                        ? "hunter" : "speedrunner";
                runCommands(modifier + roleCommands, player);
            });
        }
    }

    /**
     * Runs a modifier's trigger dispatch after its configured {@code delay}
     * in ticks. Positions and roles resolve when delayed commands fire, not
     * when they trigger. Cleanup commands never go through here.
     */
    private void runModifierWithDelay(String name, Runnable dispatch) {
        long delay = Math.max(0L, plugin.getConfig().getLong("custom-modifiers." + name + ".delay", 0L));
        if (delay <= 0L) {
            dispatch.run();
            return;
        }
        AtomicReference<BukkitTask> ref = new AtomicReference<>();
        ref.set(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                dispatch.run();
            } finally {
                pendingDelayed.remove(ref.get());
            }
        }, delay));
        pendingDelayed.add(ref.get());
    }

    /**
     * Canonicalizes a {@code runs-on} trigger name. Legacy dimension-enter
     * keys ({@code ON_FIRST_ENTER_NETHER}, {@code ON_FIRST_ENTER_END}) behave
     * as once-per-player triggers and map to their canonical names.
     */
    static String normalizeTrigger(String trigger) {
        if (trigger == null) {
            return null;
        }
        String key = trigger.trim();
        if (key.equalsIgnoreCase("ON_FIRST_ENTER_NETHER")) {
            return "ON_NETHER_ENTER";
        }
        if (key.equalsIgnoreCase("ON_FIRST_ENTER_END")) {
            return "ON_END_ENTER";
        }
        return key;
    }

    private boolean runsOnContains(String name, String event) {
        List<String> runsOn = plugin.getConfig().getStringList("custom-modifiers." + name + ".runs-on");
        String canonical = normalizeTrigger(event);
        // When runs-on is omitted, default to ON_START.
        if (runsOn.isEmpty()) {
            return "ON_START".equalsIgnoreCase(canonical);
        }
        return runsOn.stream().map(GameStateCommandManager::normalizeTrigger).anyMatch(canonical::equalsIgnoreCase);
    }

    private void runModifierCommands(String name) {
        runModifierWithDelay(name, () -> {
            String modifier = "custom-modifiers." + name + ".commands.";
            runCommands(modifier + "console", null);
            for (Player player : participatingPlayers()) {
                runCommands(modifier + "player", player);
                String roleCommands = playerStates.role(player) == Role.HUNTER
                        ? "hunter" : "speedrunner";
                runCommands(modifier + roleCommands, player);
            }
        });
    }

    private void runDefault(String phase) {
        if (!plugin.getConfig().getBoolean("gamestate-commands.default-commands.enabled", true)) return;
        String path = "gamestate-commands.default-commands.";
        if (plugin.getConfig().getBoolean(path + "reset-players-stats", false)) {
            participatingPlayers().forEach(this::resetPlayerStats);
        }
        if (plugin.getConfig().getBoolean(path + "auto-set-gamemode", false)) {
            boolean setNoneSpectator = plugin.getConfig().getBoolean("settings.roles.none-gamemode-spectator.enabled", true);
            List<Player> nonePlayers = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> {
                Role role = playerStates.role(player);
                if (role == Role.AFK) return; // AFK players are left alone
                if (phase.equals("start") && !role.isParticipant()) {
                    if (setNoneSpectator) player.setGameMode(GameMode.SPECTATOR);
                    nonePlayers.add(player);
                    return;
                }
                player.setGameMode(GameMode.SURVIVAL);
            });
            if (!nonePlayers.isEmpty()) {
                lobbyTeleporter.teleportToLobby(nonePlayers);
                lobbyTeleporter.setSpawnToLobby(nonePlayers);
            }
        }
        var worlds = Bukkit.getWorlds();
        boolean disableLocatorBar = plugin.getConfig().getBoolean(path + "disable-locator-bar", false);
        worlds.forEach(world -> world.setGameRule(GameRules.LOCATOR_BAR, !disableLocatorBar));
        // Disable phantom spawning while a match runs and restore it when the
        // match ends. The gamerule is re-enabled on the end phase.
        boolean disablePhantoms = plugin.getConfig().getBoolean(path + "disable-phantoms", false);
        GameRule doInsomnia = Registry.GAME_RULE.get(NamespacedKey.minecraft("do_insomnia"));
        if (doInsomnia != null) {
            boolean phantomsEnabled = phase.equals("end") || !disablePhantoms;
            worlds.forEach(world -> world.setGameRule(doInsomnia, phantomsEnabled));
        }
        worlds.forEach(world -> world.setGameRule(GameRules.IMMEDIATE_RESPAWN,
                plugin.getConfig().getBoolean(path + "set-respawn-immediate", false)));
        // Prevent spectators from generating chunks while the match is active.
        // This is the native gamerule equivalent of the old spectator chunk
        // generation toggle and avoids lag from spectators exploring.
        worlds.forEach(world -> world.setGameRule(GameRules.SPECTATORS_GENERATE_CHUNKS, false));
        if (plugin.getConfig().getBoolean(path + "set-daytime", false)) {
            Bukkit.getWorlds().forEach(this::setDaytime);
        }
    }

    private void setDaytime(World world) {
        try {
            world.setTime(0L);
            // Clear any ongoing rain/storm so a fresh match starts with clear
            // skies, matching the behaviour of a newly generated world.
            world.setStorm(false);
            world.setWeatherDuration(0);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().fine("Skipping daytime reset in world without a world clock: " + world.getName());
        }
    }

    private void runConfigured(String phase) {
        String base = "gamestate-commands.";
        if (plugin.getConfig().getBoolean(base + "console-commands.enabled", false)) {
            runCommands(base + "console-commands." + phase, null);
        }
        if (plugin.getConfig().getBoolean(base + "player-commands.enabled", false)) {
            for (Player player : participatingPlayers()) {
                runCommands(base + "player-commands." + phase, player);
            }
        }
        for (String name : configService.modifierNames()) {
            if (!configService.modifierEnabled(name)) continue;
            if (phase.equals("start") && runsOnContains(name, "ON_START")) {
                runModifierCommands(name);
            }
        }
    }

    private void runCommands(String path, Player player) {
        for (String command : plugin.getConfig().getStringList(path)) {
            if (command.isBlank()) continue;
            try {
                String playerName = player != null ? player.getName() : null;
                double x = player != null ? player.getLocation().getX() : 0.0;
                double y = player != null ? player.getLocation().getY() : 0.0;
                double z = player != null ? player.getLocation().getZ() : 0.0;
                String parsed = CommandPlaceholders.replace(command, playerName, x, y, z);
                if (parsed.startsWith("/")) parsed = parsed.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to run command '%s'. Skipping..".formatted(command));
                e.printStackTrace();
            }
        }
    }

    private List<Player> participatingPlayers() {
        return Bukkit.getOnlinePlayers().stream().filter(player -> playerStates.role(player).isParticipant())
                .map(player -> (Player) player).toList();
    }

    private void resetPlayerStats(Player player) {
        player.getInventory().clear();
        player.setLevel(0);
        player.setExp(0.0f);
        player.clearActivePotionEffects();
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        clearAdvancements(player);
    }

    private void clearAdvancements(Player player) {
        Bukkit.advancementIterator().forEachRemaining(advancement ->
                player.getAdvancementProgress(advancement).getAwardedCriteria().forEach(criteria ->
                        player.getAdvancementProgress(advancement).revokeCriteria(criteria)));
    }
}