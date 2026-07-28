package com.jruk8.jmanhunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GameManager {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final PlayerStateStore playerStates;
    private final CompassManager compass;
    private final StatsManager stats;
    private boolean active;
    private boolean ending;
    private boolean gameBegun;
    private BukkitTask waitingReminderTask;

    public GameManager(JManhuntPlugin plugin, MessageService messages, PlayerStateStore playerStates,
                       CompassManager compass, StatsManager stats) {
        this.plugin = plugin; this.messages = messages; this.playerStates = playerStates;
        this.compass = compass; this.stats = stats;
    }

    public boolean isActive() { return active; }
    public boolean isGameBegun() { return gameBegun; }
    public boolean isEnding() { return ending; }

    public boolean start() {
        if (active) return false;
        List<Player> players = Bukkit.getOnlinePlayers().stream().filter(p -> role(p) != Role.NONE)
                .map(p -> (Player) p).toList();
        if (players.stream().noneMatch(p -> role(p) == Role.HUNTER)
                || players.stream().noneMatch(p -> role(p) == Role.SPEEDRUNNER)) return false;
        active = true; ending = false; gameBegun = false; stats.clear(); playerStates.clearMatch();
        if (plugin.getConfig().getBoolean("run-default-commands", true)) {
            clearAdvancements();
            Bukkit.getOnlinePlayers().forEach(player -> player.setGameMode(
                    role(player) == Role.NONE ? GameMode.SPECTATOR : GameMode.SURVIVAL));
        }
        for (Player player : players) {
            StatsManager.Stats playerStats = stats.getOrCreate(player.getUniqueId());
            playerStats.player = player.getName();
            playerStats.uuid = player.getUniqueId();
            playerStats.role = role(player);
            playerStats.matchStartedAt = System.currentTimeMillis();
            if (role(player) == Role.SPEEDRUNNER) {
                playerStates.setSpeedrunnerAlive(player.getUniqueId(), true);
                playerStates.recordLastSeen(player, player.getLocation());
            }
            if (role(player) == Role.HUNTER) { compass.giveCompass(player); compass.refreshCompass(player); }
        }
        runConfigured("gamestate-commands.start", ""); broadcast("manhunt.start-success"); sound("neutral-sound");
        if (plugin.getConfig().getBoolean("start-on-speedrunner-damage", true)) scheduleWaitingReminder();
        else beginGame();
        return true;
    }

    public void finish(Role winner) {
        if (ending) return;
        ending = true;
        if (waitingReminderTask != null) waitingReminderTask.cancel();
        String winnerName = winner == Role.HUNTER ? "Hunters" : "Speedrunners";
        String winMessage = getWinMessage(winner);
        String title = winner == Role.HUNTER ? "game.hunters-title" : "game.speedrunners-title";
        broadcast(winMessage);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(Title.title(component(title), Component.empty(),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        sound(winner == Role.HUNTER ? "fail-sound" : "win-sound");
        stats.completeMatch(winner);
        long delay = Math.max(0L, Math.round(plugin.getConfig().getDouble("game-end-delay", 10.0) * 20.0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> stats.showStats(winner), delay / 2);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            runConfigured("gamestate-commands.end", winnerName);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getInventory().clear();
                if (plugin.getConfig().getBoolean("run-default-commands", true)) player.setGameMode(GameMode.SURVIVAL);
            }
            if (plugin.getConfig().getBoolean("run-default-commands", true)) clearAdvancements();
            active = false; ending = false; gameBegun = false; playerStates.clearMatch();
        }, delay);
    }

    public void end() { finish(Role.HUNTER); }

    public void finishLater(Role winner) { Bukkit.getScheduler().runTask(plugin, () -> finish(winner)); }

    public void beginGame() {
        if (gameBegun) return;
        gameBegun = true;
        if (waitingReminderTask != null) waitingReminderTask.cancel();
        broadcast("manhunt.started-by-damage"); sound("neutral-sound"); applyStartDebuffs();
    }

    private String getWinMessage(Role winner) {
        String text = winner == Role.HUNTER ?
                messages.string("game.hunters-win", "Hunters Win!") :
                messages.string("game.speedrunners-win", "Speedrunners Win!");
        return messages.addSeparators(text);
    }

    private void scheduleWaitingReminder() {
        double interval = plugin.getConfig().getDouble("start-reminder-interval", 10.0);
        if (interval == -1.0) return;
        long delay = Math.max(1L, Math.round(interval * 20.0));
        broadcast("manhunt.waiting-for-damage");
        waitingReminderTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> { if (active && !gameBegun) broadcast("manhunt.waiting-for-damage"); }, delay, delay);
    }

    private void applyStartDebuffs() {
        if (!plugin.getConfig().getBoolean("start-debuffs.enabled", false)) return;
        ConfigurationSection effects = plugin.getConfig().getConfigurationSection("start-debuffs.effects");
        if (effects == null) return;
        for (Player hunter : Bukkit.getOnlinePlayers()) {
            if (role(hunter) != Role.HUNTER) continue;
            for (String effectName : effects.getKeys(false)) {
                ConfigurationSection effect = effects.getConfigurationSection(effectName);
                if (effect == null) continue;
                PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(
                        NamespacedKey.minecraft(effectName.toLowerCase(Locale.ROOT)));
                if (type == null) continue;
                double seconds = effect.getDouble("seconds", 10.0);
                if (seconds == -1.0) continue;
                int amplifier = Math.max(0, effect.getInt("amplifier", 0));
                hunter.addPotionEffect(new PotionEffect(type, Math.max(1, (int) Math.round(seconds * 20.0)),
                        amplifier, false, false, true));
                message(hunter, "manhunt.debuff-applied", Map.of("effect", effectName,
                        "seconds", String.valueOf(seconds)));
            }
        }
    }

    private Role role(Player player) { return playerStates.role(player); }
    private Component component(String key) { return messages.component(key); }
    private void broadcast(String key) { Bukkit.broadcast(component(key)); }
    private void message(Player player, String key, Map<String, String> values) { player.sendMessage(messages.component(key, values)); }
    private void runConfigured(String key, String winner) {
        for (String command : plugin.getConfig().getStringList(key)) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{winner}", winner));
        }
    }

    private void clearAdvancements() {
        Bukkit.getOnlinePlayers().forEach(player -> Bukkit.advancementIterator().forEachRemaining(advancement ->
                player.getAdvancementProgress(advancement).getAwardedCriteria().forEach(criteria ->
                        player.getAdvancementProgress(advancement).revokeCriteria(criteria))));
    }
    public void playNeutralSound(Player player) { playSound(player, "neutral-sound"); }

    private void sound(String key) {
        Bukkit.getOnlinePlayers().forEach(player -> playSound(player, key));
    }

    private void playSound(Player player, String key) {
        try {
            String soundName = plugin.getConfig().getString("sounds." + key, "BLOCK_NOTE_BLOCK_PLING");
            Sound sound;
            try {
                sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                NamespacedKey soundKey = NamespacedKey.fromString(soundName.toLowerCase(Locale.ROOT));
                if (soundKey == null) soundKey = NamespacedKey.minecraft(soundName.toLowerCase(Locale.ROOT));
                sound = Registry.SOUNDS.get(soundKey);
            }
            if (sound == null) {
                return;
            }
            player.playSound(player.getLocation(), sound, 1, 1);
        } catch (IllegalArgumentException ignored) { }
    }
}
