package com.jruk8.jmanhunt;

import com.jruk8.jmanhunt.api.events.JGameBeginEvent;
import com.jruk8.jmanhunt.api.events.JMatchEndEvent;
import com.jruk8.jmanhunt.api.events.JMatchStartEvent;
import com.jruk8.jmanhunt.settings.autostart.AutostartCountdownMessages;
import com.jruk8.jmanhunt.settings.world_engine.WorldEngineService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
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
    private final WinConditionEngine winConditionEngine;
    private boolean active;
    private boolean ending;
    private boolean endPhaseDone;
    private boolean endStatsShown;
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
    private final List<Runnable> beginGameListeners = new ArrayList<>();
    private final List<Runnable> gameEndListeners = new ArrayList<>();
    private BukkitTask startDelayTask;
    private int startDelayRemaining;
    private boolean startDelayActive;
    private final Map<UUID, Location> startDelayReturnPoints = new HashMap<>();
    private BukkitTask surviveTimeTask;

    public GameManager(JManhuntPlugin plugin, MessageService messages, SoundService sounds,
                       PlayerStateStore playerStates, CompassManager compass, StatsManager stats,
                       ConfigService configService, WorldEngineService worldEngine,
                       WinConditionEngine winConditionEngine) {
        this.plugin = plugin;
        this.messages = messages;
        this.sounds = sounds;
        this.playerStates = playerStates;
        this.compass = compass;
        this.stats = stats;
        this.configService = configService;
        this.worldEngine = worldEngine;
        this.winConditionEngine = winConditionEngine;
        this.stateCommands = new GameStateCommandManager(plugin, playerStates, configService, worldEngine);

        // assign events
        configService.onChange("settings.autostart.enabled", (oldValue, newValue) -> updateAutostartState());
        configService.onChange("world-engine.enabled", (oldValue, newValue) -> worldEngine.onReload());
        // Structure datapacks refresh exactly like the world-engine datapack:
        // toggling in-game applies or removes the files immediately instead of
        // waiting for a restart.
        configService.onChange("settings.game-boosts.nether-structures.enabled",
                (oldValue, newValue) -> worldEngine.onReload());
        configService.onChange("settings.game-boosts.overworld-structures.enabled",
                (oldValue, newValue) -> worldEngine.onReload());
    }

    // TODO: push players to a match container, so that new players cannot join the match mid-game.
    public boolean isActive() { return active; }
    public boolean isGameBegun() { return gameBegun; }
    public boolean isEnding() { return ending; }
    public long matchId() { return matchId; }
    public GameStateCommandManager stateCommands() { return stateCommands; }
    public Set<String> settingNames() { return configService.settingNames(); }
    public boolean getSetting(String setting) { return configService.getBoolean(setting, false); }
    public Object getSettingValue(String setting) { return configService.getValue(setting); }
    /** Sets a scalar setting parsed from a raw string. Returns false on invalid input. */
    public boolean setSetting(String setting, String rawValue) { return configService.setValue(setting, rawValue); }

    /** Registers a listener invoked whenever a match starts. */
    public void addGameStartListener(Runnable listener) { gameStartListeners.add(listener); }

    /** Registers a listener invoked when the game actually begins (after pre-start window). */
    public void addBeginGameListener(Runnable listener) { beginGameListeners.add(listener); }

    /** Registers a listener invoked when a match ends. */
    public void addGameEndListener(Runnable listener) { gameEndListeners.add(listener); }

    public boolean start() {
        if (active) return false;
        cancelAutostartCountdown();
        List<Player> players = Bukkit.getOnlinePlayers().stream().filter(p -> role(p).isParticipant())
                .map(p -> (Player) p).toList();
        if (players.stream().noneMatch(p -> role(p) == Role.HUNTER)
                || players.stream().noneMatch(p -> role(p) == Role.SPEEDRUNNER)) return false;
        // Remove any lingering invulnerability from a previous game end
        Bukkit.getOnlinePlayers().forEach(p -> p.setInvulnerable(false));
        active = true; ending = false; endPhaseDone = false; endStatsShown = false; gameBegun = false; stats.clear(); playerStates.clearMatch();
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
            // Initialize per-role lives from config. -1 means unlimited.
            int lives = role(player) == Role.HUNTER
                    ? plugin.getConfig().getInt("settings.hunter-respawn.lives.hunter", -1)
                    : plugin.getConfig().getInt("settings.hunter-respawn.lives.speedrunner", 1);
            playerStates.setLives(player.getUniqueId(), lives);
        }
        // Apply the configured default state and custom start commands before
        // giving role equipment. In particular, default clear-inventory must
        // not remove the hunter compass.
        stateCommands.runStart();
        worldEngine.onMatchStart(players, noneSpectators());
        for (Player player : players) {
            if (role(player).isParticipant()) { compass.giveCompass(player); compass.refreshCompass(player); }
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
        // Start delay: hunters go to spectator for the initial delay of the
        // match, giving speedrunners a head start. Hunters are only moved to
        // spectator when the countdown actually begins; when
        // start-on-speedrunner-damage is enabled, that happens only after the
        // speedrunner first damages a hunter.
        if (plugin.getConfig().getBoolean("settings.start-delay.enabled", false)) {
            int delaySeconds = plugin.getConfig().getInt("settings.start-delay.delay-seconds", 30);
            if (delaySeconds > 0) {
                startDelayRemaining = delaySeconds;
                startDelayActive = true;
                // If start-on-speedrunner-damage is disabled, begin the
                // countdown immediately. Otherwise it starts in beginGame().
                if (!getSetting("settings.start-on-speedrunner-damage.enabled")) {
                    beginStartDelay();
                }
            }
        }
        // Survive time win condition: speedrunners win if they survive long enough.
        if (winConditionEngine.isSurviveTimeEnabled()) {
            long ticks = Math.round(winConditionEngine.surviveTimeSeconds() * 20.0);
            long currentMatchId = matchId;
            surviveTimeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (active && matchId == currentMatchId) {
                    finish(Role.SPEEDRUNNER);
                }
            }, ticks);
        }
        messages.broadcast("manhunt.start-success");
        gameStartListeners.forEach(Runnable::run);
        Bukkit.getPluginManager().callEvent(new JMatchStartEvent(matchId));
        sounds.playNeutralSound();
        showStatusToAllPlayers();
        announceRoles(players);
        // load waiting delay configuration (enforces a 5 second minimum;
        // -1 waits indefinitely)
        waitingDelayConfigured = WaitingReminder.clampDelay(
                plugin.getConfig().getInt("settings.start-on-speedrunner-damage.delay-seconds", 30));
        if (getSetting("settings.start-on-speedrunner-damage.enabled")) scheduleWaitingReminder();
        else beginGame();
        return true;
    }

    /**
     * Tells each participant their own role when a match starts. This runs
     * inside {@link #start()} after the match status is shown, but still
     * before the pre-start window opens, so it always plays before any damage
     * can occur. Non-participants are skipped. Sounds play as part of the
     * announcement: when both chat and title are disabled, nothing plays at
     * all.
     */
    private void announceRoles(List<Player> players) {
        boolean chat = configService.getBoolean("settings.announce-roles.chat.enabled", true);
        boolean title = configService.getBoolean("settings.announce-roles.title.enabled", true);
        if (!chat && !title) {
            return;
        }
        long fadeIn = toMillis(plugin.getConfig().getDouble("settings.announce-roles.title.fade-in-seconds", 0.5));
        long stay = toMillis(plugin.getConfig().getDouble("settings.announce-roles.title.stay-seconds", 3.0));
        long fadeOut = toMillis(plugin.getConfig().getDouble("settings.announce-roles.title.fade-out-seconds", 0.5));
        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn), Duration.ofMillis(stay), Duration.ofMillis(fadeOut));
        for (Player player : players) {
            Role playerRole = role(player);
            if (!playerRole.isParticipant()) {
                continue;
            }
            Map<String, String> values = Map.of("role", playerRole.name());
            if (chat) {
                player.sendMessage(messages.component("manhunt.role-announce-chat", values));
            }
            if (title) {
                String subtitleKey = playerRole == Role.HUNTER
                        ? "manhunt.role-announce-subtitle-hunter" : "manhunt.role-announce-subtitle-speedrunner";
                player.showTitle(Title.title(
                        messages.component("manhunt.role-announce-title", values),
                        messages.component(subtitleKey), times));
            }
            sounds.playSound(player, playerRole == Role.HUNTER ? "announce.hunter" : "announce.speedrunner");
        }
    }

    private static long toMillis(double seconds) {
        return Math.max(0L, Math.round(seconds * 1000.0));
    }

    public void finish(Role winner) {
        finish(winner, false);
    }

    /**
     * Ends the match. When {@code immediate} is true the configured
     * {@code match.end-delay} is skipped: statistics post instantly and the
     * final cleanup runs at once instead of after the delay. An immediate end
     * during an in-progress end delay finishes the match at once instead of
     * being blocked.
     */
    public void finish(Role winner, boolean immediate) {
        if (ending) {
            if (immediate) {
                showEndStatsOnce();
                finishEndPhase();
            }
            return;
        }
        ending = true;
        gameEndListeners.forEach(Runnable::run);
        Bukkit.getPluginManager().callEvent(new JMatchEndEvent(matchId, roleToPlayerRole(winner)));
        if (waitingReminderTask != null) { waitingReminderTask.cancel(); waitingReminderTask = null; }
        if (waitingExpiryTask != null) { waitingExpiryTask.cancel(); waitingExpiryTask = null; }
        if (startDelayTask != null) { startDelayTask.cancel(); startDelayTask = null; }
        if (surviveTimeTask != null) { surviveTimeTask.cancel(); surviveTimeTask = null; }
        startDelayActive = false;

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

        long delay = immediate
                ? 0L
                : Math.max(0L, Math.round(plugin.getConfig().getDouble("match.end-delay", 10.0) * 20.0));
        Bukkit.getScheduler().runTaskLater(plugin, this::showEndStatsOnce, delay / 2);
        Bukkit.getScheduler().runTaskLater(plugin, this::finishEndPhase, delay);
    }

    /** Broadcasts end-of-match statistics, exactly once per match. */
    private void showEndStatsOnce() {
        if (endStatsShown) return;
        endStatsShown = true;
        stats.showStats();
    }

    /** Runs end commands and deactivates the match, exactly once per match. */
    private void finishEndPhase() {
        if (endPhaseDone) return;
        endPhaseDone = true;
        List<Player> participants = Bukkit.getOnlinePlayers().stream().filter(p -> role(p).isParticipant())
                .map(p -> (Player) p).toList();
        teardownNow(participants);
    }

    /**
     * Shared immediate teardown tail: end commands, lobby teleport, role
     * reset, and deactivation. Callers run their own announcements, stat
     * handling, and cleanup commands first.
     */
    private void teardownNow(List<Player> participants) {
        stateCommands.runEnd();
        worldEngine.onMatchEnd(participants, noneSpectators());
        if (plugin.getConfig().getBoolean("settings.roles.reset-on-game-end.enabled", true)) {
            playerStates.resetParticipatingRoles();
        }
        active = false; ending = false; gameBegun = false; playerStates.clearMatch();
        worldEngine.prepareNextCell();
        updateAutostartState();
    }

    public void cancel() { cancel(false); }

    /**
     * Cancels the active match with no winner. Career statistics are not
     * saved, but the in-memory match statistics still back the end screen.
     * Runs the normal end delay intermission unless immediate skips
     * straight to teardown. No JMatchEndEvent fires: there is no winner.
     */
    public void cancel(boolean immediate) {
        if (!active) return;
        if (ending) {
            if (immediate) {
                showEndStatsOnce();
                finishEndPhase();
            }
            return;
        }
        ending = true;
        gameEndListeners.forEach(Runnable::run);
        if (waitingReminderTask != null) { waitingReminderTask.cancel(); waitingReminderTask = null; }
        if (waitingExpiryTask != null) { waitingExpiryTask.cancel(); waitingExpiryTask = null; }
        if (startDelayTask != null) { startDelayTask.cancel(); startDelayTask = null; }
        if (surviveTimeTask != null) { surviveTimeTask.cancel(); surviveTimeTask = null; }
        startDelayActive = false;

        messages.broadcast("game.cancelled");
        var onlinePlayers = Bukkit.getOnlinePlayers();
        for (Player player : onlinePlayers) {
            player.showTitle(Title.title(messages.component("game.cancelled-title"), Component.empty(),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        playerStates.resetOfflinePlayers(onlinePlayers);
        sounds.playGlobalSound("game.cancelled-sound");

        // Make all players invulnerable on cancel if configured
        if (configService.getBoolean("settings.invulnerability.on-game-end.enabled", true)) {
            onlinePlayers.forEach(p -> p.setInvulnerable(true));
        }

        // cancel interval modifiers early so they don't fire during the end delay
        stateCommands.cancelIntervalModifiers();
        // ran before the delay to ensure that any commands that depend on the match being completed can run immediately
        stateCommands.runConsoleCleanup();
        stateCommands.runPlayerCleanup();

        long delay = immediate
                ? 0L
                : Math.max(0L, Math.round(plugin.getConfig().getDouble("match.end-delay", 10.0) * 20.0));
        Bukkit.getScheduler().runTaskLater(plugin, this::showEndStatsOnce, delay / 2);
        Bukkit.getScheduler().runTaskLater(plugin, this::finishEndPhase, delay);
    }

    public void finishLater(Role winner) { Bukkit.getScheduler().runTask(plugin, () -> finish(winner)); }

    public void beginGame() {
        if (gameBegun) return;
        gameBegun = true;
        if (waitingReminderTask != null) waitingReminderTask.cancel();
        if (waitingExpiryTask != null) { waitingExpiryTask.cancel(); waitingExpiryTask = null; }
        // Restore participants to survival when the game begins if they were
        // set to adventure mode during the pre-start window. Hunters stay in
        // spectator if a start delay is active.
        if (plugin.getConfig().getBoolean("settings.start-on-speedrunner-damage.start-with-adventure-mode", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (role(player).isParticipant() && !(role(player) == Role.HUNTER && startDelayActive)) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
        }
        messages.broadcast("manhunt.started-by-damage"); sounds.playNeutralSound(); applyStartDebuffs();
        worldEngine.onBeginGame();
        for (Runnable listener : beginGameListeners) {
            listener.run();
        }
        Bukkit.getPluginManager().callEvent(new JGameBeginEvent(matchId));
        stateCommands.startIntervalModifiers();
        // If a start delay is active, begin the countdown now (the delay only
        // starts counting when the speedrunner first damages a hunter).
        if (startDelayActive) {
            beginStartDelay();
        }
    }

    /**
     * Starts the start-delay countdown. Hunters remain in spectator mode
     * until the delay expires, then are teleported back to their recorded
     * spawnpoints and restored to survival.
     */
    private void beginStartDelay() {
        if (startDelayTask != null) return;
        // Hunters stay in spectator for the duration of the delay. Each
        // hunter's current location is recorded so endStartDelay() can return
        // them to their spawnpoint even if they flew elsewhere, including
        // across dimensions since the location carries its world.
        startDelayReturnPoints.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (role(player) == Role.HUNTER) {
                startDelayReturnPoints.put(player.getUniqueId(), player.getLocation());
                player.setGameMode(GameMode.SPECTATOR);
            }
        }
        messages.broadcast("manhunt.start-delay-active", Map.of("seconds", String.valueOf(startDelayRemaining)));
        long currentMatchId = matchId;
        startDelayTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!active || matchId != currentMatchId) {
                cancelStartDelay();
                return;
            }
            startDelayRemaining--;
            if (startDelayRemaining <= 0) {
                endStartDelay();
            } else if (startDelayRemaining <= 5) {
                messages.broadcast("manhunt.start-delay-ending", Map.of("seconds", String.valueOf(startDelayRemaining)));
                sounds.playGlobalSound("game.autostart-countdown");
            }
        }, 20L, 20L);
    }

    /**
     * Ends the start delay, returning hunters to their recorded spawnpoints
     * and restoring them to survival mode.
     */
    private void endStartDelay() {
        cancelStartDelay();
        startDelayActive = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (role(player) == Role.HUNTER) {
                Location returnPoint = startDelayReturnPoints.remove(player.getUniqueId());
                if (returnPoint != null && returnPoint.getWorld() != null) {
                    player.teleport(returnPoint);
                }
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
        startDelayReturnPoints.clear();
        messages.broadcast("manhunt.start-delay-ended");
        sounds.playNeutralSound();
    }

    private void cancelStartDelay() {
        if (startDelayTask != null) {
            startDelayTask.cancel();
            startDelayTask = null;
        }
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
            double interval = configService.getFloat("match.start-reminder-interval", 10.0f);
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
                    boolean forceStart = plugin.getConfig().getString("settings.start-on-speedrunner-damage.on-expire", "CANCEL").equals("FORCE_START");
                    if (forceStart) {
                        messages.broadcast(WaitingReminder.expiryMessageKey(true));
                    } else {
                        messages.broadcast(WaitingReminder.expiryMessageKey(false), Map.of("seconds", String.valueOf(waitingDelayConfigured)));
                    }
                    // end match as cancelled if configured
                    if (!forceStart) {
                        // do not save stats
                        stats.clear();
                        stateCommands.cancelIntervalModifiers();
                        stateCommands.runConsoleCleanup();
                        stateCommands.runPlayerCleanup();
                        List<Player> participants = Bukkit.getOnlinePlayers().stream().filter(p -> role(p).isParticipant())
                                .map(p -> (Player) p).toList();
                        teardownNow(participants);
                        if (configService.getBoolean("settings.invulnerability.on-game-end.enabled", true)) {
                            Bukkit.getOnlinePlayers().forEach(p -> p.setInvulnerable(true));
                        }
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
        worldEngine.prepareNextCell();
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

    /** Online players with role NONE: match spectators, never AFK players. */
    private List<Player> noneSpectators() {
        return Bukkit.getOnlinePlayers().stream().filter(p -> role(p) == Role.NONE)
                .map(p -> (Player) p).toList();
    }

    /** Current world-engine cell index, or empty when the store is unavailable. */
    public OptionalLong cellIndex() { return worldEngine.cellIndex(); }

    /** Current world-engine cell index cap for the live cell size. */
    public long cellIndexCap() { return worldEngine.cellIndexCap(); }

    /** Overwrites the world-engine cell index. Returns false when unavailable. */
    public boolean cellIndex(long value) { return worldEngine.cellIndex(value); }

    /** Maps an internal role to the API player role, defaulting to the winner role of NONE. */
    private static com.jruk8.jmanhunt.api.PlayerRole roleToPlayerRole(Role role) {
        if (role == null) {
            return com.jruk8.jmanhunt.api.PlayerRole.NONE;
        }
        return switch (role) {
            case HUNTER -> com.jruk8.jmanhunt.api.PlayerRole.HUNTER;
            case SPEEDRUNNER -> com.jruk8.jmanhunt.api.PlayerRole.SPEEDRUNNER;
            case AFK -> com.jruk8.jmanhunt.api.PlayerRole.AFK;
            case NONE -> com.jruk8.jmanhunt.api.PlayerRole.NONE;
        };
    }

    /**
     * Quick-starts a match by assigning eligible players to teams and
     * immediately starting the game, bypassing the autostart system.
     *
     * @param speedrunnerPercent the percentage of convertible players that
     *                           should become speedrunners (0-100), or -1 for
     *                           default (keep teams, converting only what is
     *                           missing to start)
     * @return true if the match was started successfully
     */
    public boolean quickStart(int speedrunnerPercent) {
        if (active) return false;
        // Every online non-AFK player is convertible: NONE players are
        // assigned fresh, while existing hunters and speedrunners keep their
        // roles unless conversion is needed. AFK players are never touched.
        List<Player> pool = Bukkit.getOnlinePlayers().stream()
                .filter(p -> role(p) != Role.AFK)
                .map(p -> (Player) p)
                .toList();
        // Cancel any autostart countdown silently
        if (autostartCountdownTask != null) {
            autostartCountdownTask.cancel();
            autostartCountdownTask = null;
        }
        // A match needs at least one hunter and one speedrunner, so with
        // fewer than two convertible players there is nothing to assign.
        if (pool.size() < 2) return start();
        if (speedrunnerPercent < 0) {
            ensureMinimumTeams(pool);
        } else {
            // Percentage-based assignment over the whole convertible pool:
            // random selection, the first N become speedrunners and the rest
            // become hunters.
            int speedrunnerCount = quickStartSpeedrunnerCount(pool.size(), speedrunnerPercent);
            List<Player> shuffled = new ArrayList<>(pool);
            java.util.Collections.shuffle(shuffled);
            for (int i = 0; i < shuffled.size(); i++) {
                playerStates.setRole(shuffled.get(i), i < speedrunnerCount ? Role.SPEEDRUNNER : Role.HUNTER);
            }
        }
        // Validate after assignment: start() requires at least one hunter and
        // one speedrunner, so e.g. two players online with one AFK will fail.
        return start();
    }

    /**
     * Computes how many players of a convertible pool become speedrunners for
     * a quick-start percentage. Fractional results are rounded to the nearest
     * whole player, with always at least one speedrunner.
     *
     * @param poolSize the number of convertible players (must be positive)
     * @param percent the requested speedrunner percentage (0-100)
     * @return the number of speedrunners to assign
     */
    static int quickStartSpeedrunnerCount(int poolSize, int percent) {
        return Math.max(1, (int) Math.round(poolSize * percent / 100.0));
    }

    /**
     * Guarantees at least one hunter and one speedrunner by converting random
     * pool members only where a team is missing, so an all-hunter or an
     * all-speedrunner lobby still starts. NONE players become hunters and
     * everyone else keeps their current role.
     */
    private void ensureMinimumTeams(List<Player> pool) {
        boolean hasHunter = pool.stream().anyMatch(p -> role(p) == Role.HUNTER);
        boolean hasSpeedrunner = pool.stream().anyMatch(p -> role(p) == Role.SPEEDRUNNER);
        Player converted = null;
        if (!hasSpeedrunner) {
            converted = pickConvertible(pool, null);
            if (converted != null) playerStates.setRole(converted, Role.SPEEDRUNNER);
        }
        if (!hasHunter) {
            Player hunter = pickConvertible(pool, converted);
            if (hunter != null) playerStates.setRole(hunter, Role.HUNTER);
        }
        for (Player p : pool) {
            if (role(p) == Role.NONE) playerStates.setRole(p, Role.HUNTER);
        }
    }

    /**
     * Picks a random convertible pool member, preferring NONE players so
     * queued roles are only disturbed when no unassigned player is left.
     *
     * @return the picked player, or null if the pool holds only the exclusion
     */
    private Player pickConvertible(List<Player> pool, Player exclude) {
        List<Player> candidates = pool.stream().filter(p -> !p.equals(exclude)).toList();
        List<Player> unassigned = candidates.stream().filter(p -> role(p) == Role.NONE).toList();
        List<Player> preferred = unassigned.isEmpty() ? candidates : unassigned;
        if (preferred.isEmpty()) return null;
        return preferred.get(ThreadLocalRandom.current().nextInt(preferred.size()));
    }
}
