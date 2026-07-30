package com.jruk8.jmanhunt;

import com.jruk8.jmanhunt.extras.autostart.AutostartCountdownMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
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
    private BukkitTask autostartCountdownTask;
    private int autostartCountdownRemaining;
    private int autostartCountdownConfigured;

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
        cancelAutostartCountdown();
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
        }
        // Apply the configured default state and custom start commands before
        // giving role equipment. In particular, default clear-inventory must
        // not remove the hunter compass.
        stateCommands.runStart();
        for (Player player : players) {
            if (role(player) == Role.HUNTER) { compass.giveCompass(player); compass.refreshCompass(player); }
        }
        broadcast("manhunt.start-success"); sound("neutral-sound");
        showStatusToAllPlayers();
        if (getSetting("extras.start-on-speedrunner-damage")) scheduleWaitingReminder();
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

        // ran before the delay to ensure that any commands that depend on the match being completed can run immediately
        stateCommands.runConsoleCleanup(winnerName);
        stateCommands.runPlayerCleanup(winnerName);

        long delay = Math.max(0L, Math.round(plugin.getConfig().getDouble("game-end-delay", 10.0) * 20.0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> stats.showStats(winner), delay / 2);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            stateCommands.runEnd(winnerName);
            if (plugin.getConfig().getBoolean("extras.reset-roles-on-game-end", false)) {
                playerStates.resetParticipatingRoles();
            }
            active = false; ending = false; gameBegun = false; playerStates.clearMatch();
            updateAutostartState();
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

    public void updateAutostartState() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::updateAutostartState);
            return;
        }
        if (active || ending || !plugin.getConfig().getBoolean("extras.autostart.enabled", false)) {
            cancelAutostartCountdown();
            return;
        }
        if (!isEligibleToStart()) {
            cancelAutostartCountdown();
            return;
        }
        if (autostartCountdownTask != null) return;
        autostartCountdownConfigured = Math.max(0, plugin.getConfig().getInt("extras.autostart.countdown-seconds", 60));
        if (autostartCountdownConfigured == 0) {
            start();
            return;
        }
        autostartCountdownRemaining = autostartCountdownConfigured;
        broadcast("manhunt.autostart-eligible", Map.of("seconds", String.valueOf(autostartCountdownConfigured)));
        announceAutostartCheckpoint(autostartCountdownRemaining);
        autostartCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (active || ending || !isEligibleToStart()) {
                cancelAutostartCountdown();
                return;
            }
            autostartCountdownRemaining--;
            if (autostartCountdownRemaining <= 0) {
                cancelAutostartCountdown();
                start();
                return;
            }
            announceAutostartCheckpoint(autostartCountdownRemaining);
        }, 20L, 20L);
    }

    private void announceAutostartCheckpoint(int remainingSeconds) {
        if (!AutostartCountdownMessages.shouldAnnounce(remainingSeconds, autostartCountdownConfigured)) return;
        broadcast("manhunt.autostart-countdown", Map.of("seconds", String.valueOf(remainingSeconds)));
    }

    private void cancelAutostartCountdown() {
        if (autostartCountdownTask != null) {
            autostartCountdownTask.cancel();
            autostartCountdownTask = null;
            broadcast("manhunt.autostart-cancelled");
        }
    }

    private boolean isEligibleToStart() {
        boolean hasHunter = false;
        boolean hasSpeedrunner = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Role playerRole = role(player);
            if (playerRole == Role.HUNTER) hasHunter = true;
            else if (playerRole == Role.SPEEDRUNNER) hasSpeedrunner = true;
            if (hasHunter && hasSpeedrunner) return true;
        }
        return false;
    }

    private void showStatusToAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            message(player, "manhunt.status-header", Map.of("status", active ? "ACTIVE" : "INACTIVE"));
            sendRoleSection(player, Role.SPEEDRUNNER, "manhunt.speedrunners-header");
            sendRoleSection(player, Role.HUNTER, "manhunt.hunters-header");
            sendRoleSection(player, Role.NONE, "manhunt.none-header");
        }
    }

    private void sendRoleSection(Player receiver, Role role, String headerKey) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (role(player) == role) names.add(player.getName());
        }
        if (names.isEmpty()) return;
        message(receiver, headerKey, Map.of());
        names.stream().sorted().forEach(name -> message(receiver, "manhunt.status-player", Map.of("player", name)));
    }

    private void applyStartDebuffs() {
        if (!plugin.getConfig().getBoolean("extras.start-debuffs.enabled", false)) return;
        var effects = plugin.getConfig().getConfigurationSection("extras.start-debuffs.effects");
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
    private void broadcast(String key, Map<String, String> values) { Bukkit.broadcast(messages.component(key, values)); }
    private void message(Player player, String key, Map<String, String> values) { player.sendMessage(messages.component(key, values)); }
    public void playNeutralSound(Player player) { playSound(player, "neutral-sound"); }

    private void sound(String key) {
        Bukkit.getOnlinePlayers().forEach(player -> playSound(player, key));
    }

    private void playSound(Player player, String key) {
        try {
            String soundName;
            String base = "sounds." + key;
            if (plugin.getConfig().isString(base)) {
                soundName = plugin.getConfig().getString(base);
            } else {
                soundName = plugin.getConfig().getString(base + ".sound");
            }
            if (soundName == null) soundName = "block.note_block.pling";

            double configuredPitch = plugin.getConfig().getDouble(base + ".pitch", 1.0);
            float pitch = Double.isFinite(configuredPitch)
                    ? (float) Math.clamp(configuredPitch, 0.0, 2.0)
                    : 1.0f;
            String soundKey = resolveSound(soundName);
            if (soundKey == null) return;
            player.playSound(player.getLocation(), soundKey, 1, pitch);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not play configured sound '" + key + "': " + exception.getMessage());
        }
    }

    private String resolveSound(String soundName) {
        // Only namespaced Minecraft keys are supported (for example
        // "block.note_block.pling" or "minecraft:block.note_block.pling").
        // Legacy Bukkit-style enum names (BLOCK_NOTE_BLOCK_PLING) are not
        // resolved: Sound.valueOf()/OldEnum are deprecated for removal, and
        // Registry.match() (the closest replacement) is likewise deprecated
        // as unreliable, so there is no supported way left to translate them.
        NamespacedKey soundKey = NamespacedKey.fromString(soundName.toLowerCase(Locale.ROOT));
        if (soundKey == null) soundKey = NamespacedKey.minecraft(soundName.toLowerCase(Locale.ROOT));
        if (soundKey == null || Registry.SOUNDS.get(soundKey) == null) return null;
        return soundKey.asString();
    }
}
