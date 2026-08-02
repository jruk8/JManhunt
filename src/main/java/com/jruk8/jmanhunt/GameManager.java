package com.jruk8.jmanhunt;

import com.jruk8.jmanhunt.extras.autostart.AutostartCountdownMessages;
import com.jruk8.jmanhunt.extras.world_engine.WorldEngineService;
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
    private final ConfigService configService;
    private final SoundService sounds;
    private final WorldEngineService worldEngine;
    private boolean active;
    private boolean ending;
    private boolean gameBegun;
    private BukkitTask waitingReminderTask;
    private BukkitTask waitingExpiryTask;
    private int waitingDelayConfigured;
    private BukkitTask autostartCountdownTask;
    private int autostartCountdownRemaining;
    private int autostartCountdownConfigured;
    private long matchId;

    public GameManager(JManhuntPlugin plugin, MessageService messages, SoundService sounds,
                       PlayerStateStore playerStates, CompassManager compass, StatsManager stats,
                       ConfigService configService, WorldEngineService worldEngine) {
        this.plugin = plugin;
        this.messages = messages;
        this.sounds = sounds;
        this.playerStates = playerStates;
        this.compass = compass;
        this.stats = stats;
        this.configService = configService;
        this.worldEngine = worldEngine;
        this.stateCommands = new GameStateCommandManager(plugin, playerStates, configService);

        // assign events
        configService.onChange("extras.autostart.enabled", (oldValue, newValue) -> updateAutostartState());
        configService.onChange("extras.world-engine.enabled", (oldValue, newValue) -> worldEngine.onReload());
    }

    // TODO: push players to a match container, so that new players cannot join the match mid-game.
    public boolean isActive() { return active; }
    public boolean isGameBegun() { return gameBegun; }
    public boolean isEnding() { return ending; }
    public long matchId() { return matchId; }
    public Set<String> settingNames() { return configService.settingNames(); }
    public boolean getSetting(String setting) { return configService.getBoolean(setting, false); }
    public boolean setSetting(String setting, boolean value) { return configService.setBoolean(setting, value); }

    public boolean start() {
        if (active) return false;
        cancelAutostartCountdown();
        List<Player> players = Bukkit.getOnlinePlayers().stream().filter(p -> role(p) != Role.NONE)
                .map(p -> (Player) p).toList();
        if (players.stream().noneMatch(p -> role(p) == Role.HUNTER)
                || players.stream().noneMatch(p -> role(p) == Role.SPEEDRUNNER)) return false;
        active = true; ending = false; gameBegun = false; stats.clear(); playerStates.clearMatch();
        matchId++;
        playerStates.setMatchParticipants(players);
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
        worldEngine.onMatchStart(players);
        for (Player player : players) {
            if (role(player) == Role.HUNTER) { compass.giveCompass(player); compass.refreshCompass(player); }
        }
        messages.broadcast("manhunt.start-success");
        sounds.playNeutralSound();
        showStatusToAllPlayers();
        // load waiting delay configuration
        waitingDelayConfigured = Math.max(0, plugin.getConfig().getInt("extras.start-on-speedrunner-damage.delay-seconds", 30));
        if (getSetting("extras.start-on-speedrunner-damage.enabled")) scheduleWaitingReminder();
        else beginGame();
        return true;
    }

    public void finish(Role winner) {
        if (ending) return;
        ending = true;
        if (waitingReminderTask != null) { waitingReminderTask.cancel(); waitingReminderTask = null; }
        if (waitingExpiryTask != null) { waitingExpiryTask.cancel(); waitingExpiryTask = null; }

        String winnerName = winner == Role.HUNTER ? "Hunters" : "Speedrunners";
        String winMessage = getWinMessage(winner);
        String title = winner == Role.HUNTER ? "game.hunters-title" : "game.speedrunners-title";
        messages.broadcast(winMessage);
        var onlinePlayers = Bukkit.getOnlinePlayers();
        for (Player player : onlinePlayers) {
            player.showTitle(Title.title(messages.component(title), Component.empty(),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        playerStates.resetOfflinePlayers(onlinePlayers);
        sounds.playGlobalSound(winner == Role.HUNTER ? "game.fail-sound" : "game.win-sound");
        stats.completeMatch(winner);

        // ran before the delay to ensure that any commands that depend on the match being completed can run immediately
        stateCommands.runConsoleCleanup(winnerName);
        stateCommands.runPlayerCleanup(winnerName);

        long delay = Math.max(0L, Math.round(plugin.getConfig().getDouble("game-end-delay", 10.0) * 20.0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> stats.showStats(winner), delay / 2);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            stateCommands.runEnd(winnerName);
            List<Player> participants = onlinePlayers.stream().filter(p -> role(p) != Role.NONE)
                    .map(p -> (Player) p).toList();
            worldEngine.onMatchEnd(participants);
            if (plugin.getConfig().getBoolean("extras.reset-roles-on-game-end.enabled", false)) {
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
        if (waitingExpiryTask != null) { waitingExpiryTask.cancel(); waitingExpiryTask = null; }
        messages.broadcast("manhunt.started-by-damage"); sounds.playNeutralSound(); applyStartDebuffs();
    }

    private String getWinMessage(Role winner) {
        String text = winner == Role.HUNTER ?
                messages.string("game.hunters-win", "Hunters Win!") :
                messages.string("game.speedrunners-win", "Speedrunners Win!");
        return messages.addSeparators(text);
    }

    private void scheduleWaitingReminder() {
        double interval = configService.getFloat("start-reminder-interval", 10.0f);
        if (interval == -1.0) return;
        long delay = Math.max(1L, Math.round(interval * 20.0));
        messages.broadcast("manhunt.waiting-for-damage", Map.of("seconds", String.valueOf((int) Math.round(waitingDelayConfigured))));
        waitingReminderTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> { if (active && !gameBegun) messages.broadcast("manhunt.waiting-for-damage", Map.of("seconds", String.valueOf((int) Math.round(waitingDelayConfigured)))); }, delay, delay);

        // schedule expiry task which ends the waiting period if no damage occurs
        if (waitingDelayConfigured > 0) {
            long expiryTicks = Math.max(1L, Math.round(waitingDelayConfigured * 20.0));
            long currentMatchId = matchId;
            waitingExpiryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // only cancel if still active and game hasn't begun and match unchanged
                if (active && !gameBegun && matchId == currentMatchId) {
                    if (waitingReminderTask != null) { waitingReminderTask.cancel(); waitingReminderTask = null; }
                    messages.broadcast("manhunt.waiting-for-damage-exhausted", Map.of("seconds", String.valueOf(waitingDelayConfigured)));
                    // end match as cancelled if configured
                    if (plugin.getConfig().getBoolean("extras.start-on-speedrunner-damage.cancelled-on-expire", false)) {
                        // do not save stats
                        stats.clear();
                        stateCommands.runConsoleCleanup("Cancelled");
                        stateCommands.runPlayerCleanup("Cancelled");
                        List<Player> participants = Bukkit.getOnlinePlayers().stream().filter(p -> role(p) != Role.NONE)
                                .map(p -> (Player) p).toList();
                        worldEngine.onMatchEnd(participants);
                        active = false; ending = false; gameBegun = false; playerStates.clearMatch();
                        updateAutostartState();
                    } else {
                        // finish normally as hunters win
                        finishLater(Role.HUNTER);
                    }
                }
            }, expiryTicks);
        }
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
        messages.broadcast("manhunt.autostart-eligible", Map.of("seconds", String.valueOf(autostartCountdownConfigured)));
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
        messages.broadcast("manhunt.autostart-countdown", Map.of("seconds", String.valueOf(remainingSeconds)));
        sounds.playGlobalSound("game.autostart-countdown");
    }

    private void cancelAutostartCountdown() {
        if (autostartCountdownTask != null) {
            autostartCountdownTask.cancel();
            autostartCountdownTask = null;
            messages.broadcast("manhunt.autostart-cancelled");
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
            messages.message(player, "manhunt.status-header", Map.of("status", active ? "ACTIVE" : "INACTIVE"));
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
        messages.message(receiver, headerKey, Map.of());
        names.stream().sorted().forEach(name -> messages.message(receiver, "manhunt.status-player", Map.of("player", name)));
    }

    private void applyStartDebuffs() {
        if (!configService.getBoolean("extras.start-debuffs.enabled", false)) return;
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
}
