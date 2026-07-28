package com.jruk8.jmanhunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GameManager {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final PlayerStateStore playerStates;
    private final CompassManager compass;
    private final StatsManager stats;
    private final GameStateCommandManager stateCommands;
    private boolean active;
    private boolean ending;
    private boolean gameBegun;
    private BukkitTask waitingReminderTask;

    public GameManager(JManhuntPlugin plugin, MessageService messages, PlayerStateStore playerStates,
                       CompassManager compass, StatsManager stats) {
        this.plugin = plugin; this.messages = messages; this.playerStates = playerStates;
        this.compass = compass; this.stats = stats;
        this.stateCommands = new GameStateCommandManager(plugin, playerStates);
    }

    public boolean isActive() { return active; }
    public boolean isGameBegun() { return gameBegun; }
    public boolean isEnding() { return ending; }
    public Set<String> settingNames() { return stateCommands.settingNames(); }
    public boolean getSetting(String setting) { return stateCommands.getSetting(setting); }
    public boolean setSetting(String setting, boolean value) { return stateCommands.setSetting(setting, value); }

    public boolean start() {
        if (active) return false;
        List<Player> players = Bukkit.getOnlinePlayers().stream().filter(p -> role(p) != Role.NONE)
                .map(p -> (Player) p).toList();
        if (players.stream().noneMatch(p -> role(p) == Role.HUNTER)
                || players.stream().noneMatch(p -> role(p) == Role.SPEEDRUNNER)) return false;
        active = true; ending = false; gameBegun = false; stats.clear(); playerStates.clearMatch();
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
        stateCommands.runStart(); broadcast("manhunt.start-success"); sound("neutral-sound");
        if (plugin.getConfig().getBoolean("settings.start-on-speedrunner-damage", true)) scheduleWaitingReminder();
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
            stateCommands.runEnd(winnerName);
            stateCommands.runCleanup(winnerName);
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
        if (!plugin.getConfig().getBoolean("settings.start-debuffs", false)) return;
        var effects = plugin.getConfig().getConfigurationSection("start-debuffs.effects");
        if (effects == null) return;
        for (Player hunter : Bukkit.getOnlinePlayers()) {
            if (role(hunter) != Role.HUNTER) continue;
            boolean applied = false;
            for (String effectName : effects.getKeys(false)) {
                var effect = effects.getConfigurationSection(effectName);
                if (effect == null) continue;
                PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(
                        NamespacedKey.minecraft(effectName.toLowerCase(Locale.ROOT)));
                if (type == null) continue;
                double seconds = effect.getDouble("seconds", 10.0);
                if (seconds == -1.0) continue;
                int amplifier = Math.max(0, effect.getInt("amplifier", 0));
                hunter.addPotionEffect(new PotionEffect(type, Math.max(1, (int) Math.round(seconds * 20.0)),
                        amplifier, false, false, true));
                applied = true;
            }
            if (applied) hunter.sendMessage(messages.component("game.debuff-applied"));
        }
    }

    private Role role(Player player) { return playerStates.role(player); }
    private Component component(String key) { return messages.component(key); }
    private void broadcast(String key) { Bukkit.broadcast(component(key)); }
    private void message(Player player, String key, Map<String, String> values) { player.sendMessage(messages.component(key, values)); }
    public void playNeutralSound(Player player) { playSound(player, "neutral-sound"); }

    private void sound(String key) {
        Bukkit.getOnlinePlayers().forEach(player -> playSound(player, key));
    }

    private void playSound(Player player, String key) {
        try {
            String soundName = plugin.getConfig().getString("sounds." + key);
            if (soundName == null) soundName = plugin.getConfig().getString("sounds." + key + ".sound");
            if (soundName == null) soundName = "BLOCK_NOTE_BLOCK_PLING";
            double configuredPitch = plugin.getConfig().getDouble("sounds." + key + ".pitch", 1.0);
            // An invalid pitch can make the server reject the sound packet. Keep a
            // bad server config from disabling all sound playback.
            float pitch = Double.isFinite(configuredPitch)
                    ? (float) Math.max(0.0, Math.min(2.0, configuredPitch))
                    : 1.0f;
            Sound sound = resolveSound(soundName);
            if (sound == null) {
                return;
            }
            player.playSound(player.getLocation(), sound, 1, pitch);
        } catch (IllegalArgumentException ignored) { }
    }

    private Sound resolveSound(String soundName) {
        // Bukkit names are the most widely used config format. Resolve these
        // first, since their enum name uses underscores while the registry key
        // uses dotted names (for example BLOCK_NOTE_BLOCK_PLING).
        try {
            return Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // Continue with a namespaced Minecraft key.
        }
        NamespacedKey soundKey = NamespacedKey.fromString(soundName.toLowerCase(Locale.ROOT));
        if (soundKey == null) soundKey = NamespacedKey.minecraft(soundName.toLowerCase(Locale.ROOT));
        Sound sound = Registry.SOUNDS.get(soundKey);
        if (sound != null) return sound;
        return null;
    }
}
