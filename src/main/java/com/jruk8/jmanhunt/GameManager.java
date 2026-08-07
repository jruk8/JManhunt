package com.jruk8.jmanhunt;

import com.jruk8.jmanhunt.settings.autostart.AutostartCountdownMessages;
import com.jruk8.jmanhunt.settings.world_engine.WorldEngineService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
import java.util.concurrent.ThreadLocalRandom;

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
    private long waitingStartTime;
    private BukkitTask autostartCountdownTask;
    private int autostartCountdownRemaining;
    private int autostartCountdownConfigured;
    private long matchId;
    private final List<Runnable> gameStartListeners = new ArrayList<>();

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
        configService.onChange("settings.autostart.enabled", (oldValue, newValue) -> updateAutostartState());
        configService.onChange("settings.world-engine.enabled", (oldValue, newValue) -> worldEngine.onReload());
    }

    // TODO: push players to a match container, so that new players cannot join the match mid-game.
    public boolean isActive() { return active; }
    public boolean isGameBegun() { return gameBegun; }
    public boolean isEnding() { return ending; }
    public long matchId() { return matchId; }
    public GameStateCommandManager stateCommands() { return stateCommands; }
    public Set<String> settingNames() { return configService.settingNames(); }
    public boolean getSetting(String setting) { return configService.getBoolean(setting, false); }
    public boolean setSetting(String setting, boolean value) { return configService.setBoolean(setting, value); }

    /** Registers a listener invoked whenever a match starts. */
    public void addGameStartListener(Runnable listener) { gameStartListeners.add(listener); }

    public boolean start() {
        if (active) return false;
        cancelAutostartCountdown();
        List<Player> players = Bukkit.getOnlinePlayers().stream().filter(p -> role(p).isParticipant())
                .map(p -> (Player) p).toList();
        if (players.stream().noneMatch(p -> role(p) == Role.HUNTER)
                || players.stream().noneMatch(p -> role(p) == Role.SPEEDRUNNER)) return false;
        // Remove any lingering invulnerability from a previous game end
        Bukkit.getOnlinePlayers().forEach(p -> p.setInvulnerable(false));
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
            }
            if (role(player).isParticipant()) {
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
        // Set participants to adventure mode during the pre-start window if
        // configured, preventing block breaking while waiting for the first
        // speedrunner hit.
        if (getSetting("settings.start-on-speedrunner-damage.enabled")
                && plugin.getConfig().getBoolean("settings.start-on-speedrunner-damage.start-with-adventure-mode", true)) {
            for (Player player : players) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
        messages.broadcast("manhunt.start-success");
        gameStartListeners.forEach(Runnable::run);
        sounds.playNeutralSound();
        showStatusToAllPlayers();
        // load waiting delay configuration (enforces a 5 second minimum;
        // -1 waits indefinitely)
        waitingDelayConfigured = WaitingReminder.clampDelay(
                plugin.getConfig().getInt("settings.start-on-speedrunner-damage.delay-seconds", 30));
        if (getSetting("settings.start-on-speedrunner-damage.enabled")) scheduleWaitingReminder();
        else beginGame();
        return true;
    }

    public void finish(Role winner) {
        if (ending) return;
        ending = true;
        if (waitingReminderTask != null) { waitingReminderTask.cancel(); waitingReminderTask = null; }
        if (waitingExpiryTask != null) { waitingExpiryTask.cancel(); waitingExpiryTask = null; }

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

        // Make all players invulnerable on game end if configured
        if (configService.getBoolean("settings.invulnerability.on-game-end.enabled", true)) {
            onlinePlayers.forEach(p -> p.setInvulnerable(true));
        }

        // cancel interval modifiers early so they don't fire during the end delay
        stateCommands.cancelIntervalModifiers();
        // ran before the delay to ensure that any commands that depend on the match being completed can run immediately
        stateCommands.runConsoleCleanup();
        stateCommands.runPlayerCleanup();

        long delay = Math.max(0L, Math.round(plugin.getConfig().getDouble("game-end-delay", 10.0) * 20.0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> stats.showStats(winner), delay / 2);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            stateCommands.runEnd();
            List<Player> participants = onlinePlayers.stream().filter(p -> role(p).isParticipant())
                    .map(p -> (Player) p).toList();
            worldEngine.onMatchEnd(participants);
            if (plugin.getConfig().getBoolean("settings.reset-roles-on-game-end.enabled", false)) {
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
        // Restore participants to survival when the game begins if they were
        // set to adventure mode during the pre-start window.
        if (plugin.getConfig().getBoolean("settings.start-on-speedrunner-damage.start-with-adventure-mode", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (role(player).isParticipant()) player.setGameMode(GameMode.SURVIVAL);
            }
        }
        messages.broadcast("manhunt.started-by-damage"); sounds.playNeutralSound(); applyStartDebuffs();
        worldEngine.onBeginGame();
        stateCommands.startIntervalModifiers();
    }

    private String getWinMessage(Role winner) {
        String text = winner == Role.HUNTER ?
                messages.string("game.hunters-win", "Hunters Win!") :
                messages.string("game.speedrunners-win", "Speedrunners Win!");
        return messages.addSeparators(text);
    }

    private void scheduleWaitingReminder() {
        waitingStartTime = System.currentTimeMillis();
        if (waitingDelayConfigured > 0) {
            // Finite delay: broadcast exactly three reminders at the delay and
            // two equally-sized slices (e.g. 30s -> 30, 20, 10).
            int slice = WaitingReminder.sliceSeconds(waitingDelayConfigured);
            List<Integer> checkpoints = List.of(waitingDelayConfigured,
                    Math.max(1, waitingDelayConfigured - slice),
                    Math.max(1, waitingDelayConfigured - 2 * slice));
            messages.broadcast("manhunt.waiting-for-damage", Map.of("seconds", String.valueOf(waitingDelayConfigured)));
            waitingReminderTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!active || gameBegun) return;
                long elapsedMillis = System.currentTimeMillis() - waitingStartTime;
                int remaining = (int) Math.round(waitingDelayConfigured - elapsedMillis / 1000.0);
                if (remaining > 0 && checkpoints.contains(remaining)) {
                    messages.broadcast("manhunt.waiting-for-damage", Map.of("seconds", String.valueOf(remaining)));
                }
            }, 20L, 20L);
        } else {
            // Indefinite waiting (-1): use the configured reminder interval and
            // never schedule an expiry.
            double interval = configService.getFloat("start-reminder-interval", 10.0f);
            if (interval == -1.0) return;
            long delay = Math.max(1L, Math.round(interval * 20.0));
            messages.broadcast("manhunt.waiting-for-damage-indefinite", Map.of());
            waitingReminderTask = Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> { if (active && !gameBegun) {
                        messages.broadcast("manhunt.waiting-for-damage-indefinite", Map.of());
                    } }, delay, delay);
        }

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
                    if (!plugin.getConfig().getString("settings.start-on-speedrunner-damage.on-expire", "CANCEL").equals("FORCE_START")) {
                        // do not save stats
                        stats.clear();
                        stateCommands.cancelIntervalModifiers();
                        stateCommands.runConsoleCleanup();
                        stateCommands.runPlayerCleanup();
                        List<Player> participants = Bukkit.getOnlinePlayers().stream().filter(p -> role(p).isParticipant())
                                .map(p -> (Player) p).toList();
                        worldEngine.onMatchEnd(participants);
                        if (plugin.getConfig().getBoolean("settings.reset-roles-on-game-end.enabled", false)) {
                            playerStates.resetParticipatingRoles();
                        }
                        if (configService.getBoolean("settings.invulnerability.on-game-end.enabled", true)) {
                            Bukkit.getOnlinePlayers().forEach(p -> p.setInvulnerable(true));
                        }
                        active = false; ending = false; gameBegun = false; playerStates.clearMatch();
                        updateAutostartState();
                    } else {
                        // force start the game
                        beginGame();
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
        if (active || ending || !plugin.getConfig().getBoolean("settings.autostart.enabled", false)) {
            cancelAutostartCountdown();
            return;
        }
        if (!isEligibleToStart()) {
            cancelAutostartCountdown();
            return;
        }
        if (autostartCountdownTask != null) return;
        autostartCountdownConfigured = Math.max(0, plugin.getConfig().getInt("settings.autostart.countdown-seconds", 60));
        if (autostartCountdownConfigured == 0) {
            start();
            return;
        }
        autostartCountdownRemaining = autostartCountdownConfigured;
        messages.broadcast("manhunt.autostart-eligible", Map.of("seconds", String.valueOf(autostartCountdownConfigured)));
        sounds.playGlobalSound("game.autostart-countdown");
        announceAutostartCheckpoint(autostartCountdownRemaining);
        autostartCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (active || ending || !isEligibleToStart()) {
                cancelAutostartCountdown();
                return;
            }
            autostartCountdownRemaining--;
            if (autostartCountdownRemaining <= 0) {
                cancelAutostartCountdown(false);
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
        cancelAutostartCountdown(true);
    }

    private void cancelAutostartCountdown(boolean announce) {
        if (autostartCountdownTask != null) {
            autostartCountdownTask.cancel();
            autostartCountdownTask = null;
            if (announce) messages.broadcast("manhunt.autostart-cancelled");
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
            sendRoleSection(player, Role.AFK, "manhunt.afk-header");
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
        if (!configService.getBoolean("settings.start-debuffs.enabled", false)) return;
        var effects = plugin.getConfig().getConfigurationSection("settings.start-debuffs.effects");
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

    /**
     * Quick-starts a match by assigning eligible players to teams and
     * immediately starting the game, bypassing the autostart system.
     *
     * @param speedrunnerPercent the percentage of eligible players that should
     *                           become speedrunners (0-100), or -1 for default
     *                           (all hunters, one random speedrunner)
     * @return true if the match was started successfully
     */
    public boolean quickStart(int speedrunnerPercent) {
        if (active) return false;
        // Only NONE players are assignable. Existing hunters and speedrunners
        // keep their roles, and AFK players are never touched.
        List<Player> eligible = Bukkit.getOnlinePlayers().stream()
                .filter(p -> role(p) == Role.NONE)
                .map(p -> (Player) p)
                .toList();
        // Cancel any autostart countdown silently
        if (autostartCountdownTask != null) {
            autostartCountdownTask.cancel();
            autostartCountdownTask = null;
        }
        boolean hasExistingSpeedrunner = Bukkit.getOnlinePlayers().stream()
                .anyMatch(p -> role(p) == Role.SPEEDRUNNER);
        if (eligible.isEmpty()) return start();
        if (speedrunnerPercent < 0) {
            // Default: all eligible NONE players become hunters. If no
            // speedrunner is queued yet, one random eligible player becomes
            // the speedrunner.
            for (Player p : eligible) {
                playerStates.setRole(p, Role.HUNTER);
            }
            if (!hasExistingSpeedrunner) {
                Player speedrunner = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
                playerStates.setRole(speedrunner, Role.SPEEDRUNNER);
            }
        } else {
            // Percentage-based assignment of the eligible NONE pool.
            int speedrunnerCount = Math.max(1, (int) Math.round(eligible.size() * speedrunnerPercent / 100.0));
            List<Player> shuffled = new ArrayList<>(eligible);
            java.util.Collections.shuffle(shuffled);
            for (int i = 0; i < shuffled.size(); i++) {
                playerStates.setRole(shuffled.get(i), i < speedrunnerCount ? Role.SPEEDRUNNER : Role.HUNTER);
            }
        }
        // Validate after assignment: start() requires at least one hunter and
        // one speedrunner, so e.g. two players online with one AFK will fail.
        return start();
    }
}
