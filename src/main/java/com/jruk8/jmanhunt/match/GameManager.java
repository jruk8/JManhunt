package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.GameStateCommandManager;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.DurationFormat;
import com.jruk8.jmanhunt.core.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.LobbyWorld;
import com.jruk8.jmanhunt.lobby.MidMatchPolicy;
import com.jruk8.jmanhunt.lobby.SubLobby;
import com.jruk8.jmanhunt.message.ListFormatter;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.CapLimits;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.stats.Stats;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.api.events.JGameBeginEvent;
import com.jruk8.jmanhunt.api.events.JMatchCancelEvent;
import com.jruk8.jmanhunt.api.events.JMatchEndEvent;
import com.jruk8.jmanhunt.api.events.JMatchStartEvent;
import com.jruk8.jmanhunt.api.events.JPlayerJoinMatchEvent;
import com.jruk8.jmanhunt.world.BorderMode;
import com.jruk8.jmanhunt.world.CellBounds;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.world.WorldEngineService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

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
    private final LobbyService lobbies;
    private final Map<Long, GameInstance> instances = new HashMap<>();
    /** Per-lobby autostart countdowns, keyed by lobby id. */
    private final Map<Integer, AutostartCountdown> autostartCountdowns = new HashMap<>();

    /** Mutable per-lobby countdown state; the task ticks in GameManager. */
    private static final class AutostartCountdown {
        BukkitTask task;
        int remaining;
        int configured;
    }
    private long matchId;
    private final List<Consumer<GameInstance>> gameStartListeners = new ArrayList<>();
    private final List<Consumer<GameInstance>> beginGameListeners = new ArrayList<>();
    private final List<Consumer<GameInstance>> gameEndListeners = new ArrayList<>();

    public GameManager(JManhuntPlugin plugin, MessageService messages, SoundService sounds,
                       PlayerStateStore playerStates, CompassManager compass, StatsManager stats,
                       ConfigService configService, WorldEngineService worldEngine,
                       WinConditionEngine winConditionEngine, LobbyService lobbyService) {
        this.plugin = plugin;
        this.messages = messages;
        this.sounds = sounds;
        this.playerStates = playerStates;
        this.compass = compass;
        this.stats = stats;
        this.configService = configService;
        this.worldEngine = worldEngine;
        this.winConditionEngine = winConditionEngine;
        this.lobbies = lobbyService;
        this.stateCommands = new GameStateCommandManager(plugin, playerStates, configService, worldEngine, this);

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
        // Pseudo-border guard for concurrent matches; idle unless at least two
        // matches run with the engine border on.
        Bukkit.getScheduler().runTaskTimer(plugin, this::enforcePseudoBorders, 20L, 20L);
    }

    /** True while any match runs, including end-delay phases. */
    public boolean isActive() { return instances.values().stream().anyMatch(GameInstance::active); }
    /** True once any live match has begun. */
    public boolean isGameBegun() { return instances.values().stream().anyMatch(GameInstance::begun); }
    /** True while any match is being finished. */
    public boolean isEnding() { return instances.values().stream().anyMatch(GameInstance::ending); }
    public long matchId() { return matchId; }
    /** Live instances keyed by match id; more than one only with the world engine on. */
    public Map<Long, GameInstance> instances() { return Map.copyOf(instances); }
    /** Looks up a live instance by match id. */
    public Optional<GameInstance> instance(long matchId) { return Optional.ofNullable(instances.get(matchId)); }

    /** Live instances oldest first. */
    public List<GameInstance> liveInstances() {
        return instances.values().stream().sorted(Comparator.comparingLong(GameInstance::matchId)).toList();
    }

    /** The live instance a player actively participates in, if any. */
    public Optional<GameInstance> instanceOf(UUID playerId) {
        return instances.values().stream().filter(instance -> instance.isActive(playerId)).findFirst();
    }

    /** Live instance by world-engine cell index. */
    public Optional<GameInstance> instanceByCell(long cellIndex) {
        return instances.values().stream()
                .filter(instance -> instance.cellIndex().isPresent()
                        && instance.cellIndex().getAsLong() == cellIndex)
                .findFirst();
    }

    /** Live instance started from a lobby, if that lobby has one running. */
    public Optional<GameInstance> instanceForLobby(int lobbyId) {
        return instancesForLobby(lobbyId).stream().findFirst();
    }

    /** Live instances started from one lobby, sublobbies included. */
    public List<GameInstance> instancesForLobby(int lobbyId) {
        return instances.values().stream()
                .filter(instance -> instance.originLobbyId() == lobbyId).toList();
    }

    /** True when the player actively participates in any live match. */
    public boolean isInLiveInstance(UUID playerId) {
        return instanceOf(playerId).isPresent();
    }

    /** Online active participants of a match. */
    public List<Player> onlineParticipants(long matchId) {
        GameInstance instance = instances.get(matchId);
        if (instance == null) {
            return List.of();
        }
        return onlineActivePlayers(instance);
    }

    /** Online active participants of a match. */
    public List<Player> onlineActivePlayers(GameInstance instance) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> instance.isActive(player.getUniqueId()))
                .map(player -> (Player) player).toList();
    }

    /** Online players ever assigned to a match, including the eliminated. */
    public List<Player> onlineAssignedPlayers(GameInstance instance) {
        Set<UUID> assigned = instance.assignedPlayerIds();
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> assigned.contains(player.getUniqueId()))
                .map(player -> (Player) player).toList();
    }

    public boolean isActiveInInstance(long matchId, UUID playerId) {
        GameInstance instance = instances.get(matchId);
        return instance != null && instance.isActive(playerId);
    }

    /** Live speedrunners of a match (active, alive, and holding the role). */
    public int activeRunnerCount(GameInstance instance) {
        int count = 0;
        for (UUID playerId : instance.activeIds()) {
            if (playerStates.role(playerId) == Role.SPEEDRUNNER
                    && playerStates.isActiveSpeedrunner(playerId)) {
                count++;
            }
        }
        return count;
    }

    /** Live hunters of a match holding the role. */
    public int activeHunterCount(GameInstance instance) {
        int count = 0;
        for (UUID playerId : instance.activeIds()) {
            if (playerStates.role(playerId) == Role.HUNTER) {
                count++;
            }
        }
        return count;
    }

    /**
     * Ends a begun match whose hunter or speedrunner bucket hit zero through
     * a role change. Deaths end matches on their own paths; this covers
     * setplayer, joins, leaves, and removals.
     */
    public void finishIfBucketEmpty(GameInstance instance) {
        if (!instance.begun() || instance.ending()) {
            return;
        }
        bucketWinner(activeHunterCount(instance), activeRunnerCount(instance))
                .ifPresent(winner -> finishLater(instance, winner));
    }

    /** Winner when a bucket is empty; empty when both sides stand. Pure for tests. */
    static Optional<Role> bucketWinner(int hunterCount, int runnerCount) {
        if (hunterCount == 0 && runnerCount == 0) {
            return Optional.empty();
        }
        if (hunterCount == 0) {
            return Optional.of(Role.SPEEDRUNNER);
        }
        if (runnerCount == 0) {
            return Optional.of(Role.HUNTER);
        }
        return Optional.empty();
    }

    /**
     * Countdown marks for a time-limit win, in whole seconds remaining:
     * 8h, 6h, 4h, 2h, 1h, 30m, 15m, 10m, 5m, 2m, 1m, 30s, 15s, 10s, 5-1s.
     */
    static final List<Long> TIME_ANNOUNCE_SECONDS = List.of(
            28_800L, 21_600L, 14_400L, 7_200L, 3_600L, 1_800L, 900L, 600L,
            300L, 120L, 60L, 30L, 15L, 10L, 5L, 4L, 3L, 2L, 1L);

    /**
     * Starts the time-limit win countdown for a match. With both sides'
     * limits set, the earlier expiry wins (ties favor the speedrunners)
     * and a console warning explains the pick. A single per-second task
     * announces each threshold once and ends the match at zero.
     */
    private void scheduleTimeLimit(GameInstance instance, long currentMatchId) {
        boolean runnerClock = winConditionEngine.enabled(Role.SPEEDRUNNER, WinCondition.SURVIVE_TIME);
        boolean hunterClock = winConditionEngine.enabled(Role.HUNTER, WinCondition.TIME_LIMIT);
        if (!runnerClock && !hunterClock) {
            return;
        }
        double runnerSecs = winConditionEngine.time(Role.SPEEDRUNNER);
        double hunterSecs = winConditionEngine.time(Role.HUNTER);
        double limitSecs;
        Role winner;
        if (runnerClock && hunterClock) {
            if (runnerSecs == hunterSecs) {
                plugin.logger().warning("Both time-limit win conditions are " + runnerSecs
                        + "s; favoring the speedrunners.");
            } else {
                plugin.logger().warning("Both time-limit win conditions are set (speedrunners "
                        + runnerSecs + "s, hunters " + hunterSecs + "s); the earlier expiry wins.");
            }
            winner = timeLimitWinner(runnerSecs, hunterSecs);
            limitSecs = Math.min(runnerSecs, hunterSecs);
        } else if (runnerClock) {
            winner = Role.SPEEDRUNNER;
            limitSecs = runnerSecs;
        } else {
            winner = Role.HUNTER;
            limitSecs = hunterSecs;
        }
        if (limitSecs <= 0.0) {
            return;
        }
        long limitSecsWhole = Math.round(limitSecs);
        long limitMillis = Math.round(limitSecs * 1000.0);
        String winnerName = winner.displayName() + "s";
        Role finalWinner = winner;
        instance.setTimeLimitTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (instances.get(currentMatchId) != instance || !instance.active() || instance.ending()) {
                cancelTimeLimit(instance);
                return;
            }
            long elapsedMillis = System.currentTimeMillis() - instance.startedAtMillis();
            long remainingSecs = Math.max(0L, (limitMillis - elapsedMillis) / 1000L);
            if (remainingSecs > 0L) {
                for (long mark : dueThresholds(limitSecsWhole, remainingSecs, instance.timeAnnounced())) {
                    instance.timeAnnounced().add(mark);
                    sendToInstance(instance, "game.time-left",
                            Map.of("winner", winnerName, "time", DurationFormat.format(mark)));
                }
            } else {
                cancelTimeLimit(instance);
                finish(instance, finalWinner);
            }
        }, 20L, 20L));
    }

    /** Stops a match's time-limit countdown, if any. */
    private void cancelTimeLimit(GameInstance instance) {
        if (instance.timeLimitTask() != null) {
            instance.timeLimitTask().cancel();
            instance.setTimeLimitTask(null);
        }
    }

    /**
     * Thresholds to announce now: marks strictly below the limit that the
     * remaining time has reached and that are still unannounced, highest
     * first. The limit mark itself is hit exactly on spawn and never
     * announces. Pure for tests.
     */
    static List<Long> dueThresholds(long limitSecs, long remainingSecs, Set<Long> announced) {
        List<Long> due = new ArrayList<>();
        for (long mark : TIME_ANNOUNCE_SECONDS) {
            if (mark < limitSecs && mark >= remainingSecs && !announced.contains(mark)) {
                due.add(mark);
            }
        }
        return due;
    }

    /** Winner when both time limits run: earlier expiry wins, ties favor runners. Pure for tests. */
    static Role timeLimitWinner(double runnerSecs, double hunterSecs) {
        return runnerSecs <= hunterSecs ? Role.SPEEDRUNNER : Role.HUNTER;
    }

    /** Status text for the speedrunner win conditions: base plus enabled alternates. */
    public String speedrunnerWinConditions() {
        // Hunters with infinite lives can never be eliminated, so the
        // elimination line hides instead of promising an un-winnable goal.
        List<String> conditions = new ArrayList<>();
        int hunterLives = plugin.getConfig().getInt("settings.hunter-respawn.lives.hunter", -1);
        if (hunterLives != -1) {
            conditions.add(winconFragment("eliminate-hunters", Map.of()));
        }
        if (winConditionEngine.enabled(Role.SPEEDRUNNER, WinCondition.EXIT_END)) {
            conditions.add(winconFragment("credits", Map.of()));
        }
        if (winConditionEngine.enabled(Role.SPEEDRUNNER, WinCondition.SURVIVE_TIME)) {
            conditions.add(winconFragment("survive", Map.of("time",
                    DurationFormat.format((long) winConditionEngine.time(Role.SPEEDRUNNER)))));
        }
        if (winConditionEngine.enabled(Role.SPEEDRUNNER, WinCondition.ACQUIRE_ITEM)) {
            conditions.add(winconFragment("acquired", Map.of("item",
                    WinConditionEngine.prettyKey(winConditionEngine.item(Role.SPEEDRUNNER)))));
        }
        if (winConditionEngine.enabled(Role.SPEEDRUNNER, WinCondition.REACH_ADVANCEMENT)) {
            conditions.add(winconFragment("advancement",
                    Map.of("advancement", winConditionEngine.advancement(Role.SPEEDRUNNER))));
        }
        if (winConditionEngine.enabled(Role.SPEEDRUNNER, WinCondition.KILL_MOB)) {
            conditions.add(winconFragment("killed", Map.of("mob",
                    WinConditionEngine.prettyKey(winConditionEngine.mob(Role.SPEEDRUNNER)))));
        }
        return ListFormatter.joinOxford(conditions);
    }

    /** Status text for the hunter win conditions: base plus enabled alternates. */
    public String hunterWinConditions() {
        List<String> conditions = new ArrayList<>(List.of(winconFragment("eliminate-speedrunners", Map.of())));
        if (winConditionEngine.enabled(Role.HUNTER, WinCondition.TIME_LIMIT)) {
            conditions.add(winconFragment("time-limit", Map.of("time",
                    DurationFormat.format((long) winConditionEngine.time(Role.HUNTER)))));
        }
        if (winConditionEngine.enabled(Role.HUNTER, WinCondition.ACQUIRE_ITEM)) {
            conditions.add(winconFragment("acquired", Map.of("item",
                    WinConditionEngine.prettyKey(winConditionEngine.item(Role.HUNTER)))));
        }
        if (winConditionEngine.enabled(Role.HUNTER, WinCondition.REACH_ADVANCEMENT)) {
            conditions.add(winconFragment("advancement",
                    Map.of("advancement", winConditionEngine.advancement(Role.HUNTER))));
        }
        if (winConditionEngine.enabled(Role.HUNTER, WinCondition.KILL_MOB)) {
            conditions.add(winconFragment("killed", Map.of("mob",
                    WinConditionEngine.prettyKey(winConditionEngine.mob(Role.HUNTER)))));
        }
        return ListFormatter.joinOxford(conditions);
    }

    /**
     * Renders one wincon fragment template with its named values. Fragments
     * stay unparsed here; they are substituted into the status-win lines and
     * parsed once with them.
     */
    private String winconFragment(String key, Map<String, String> values) {
        String raw = messages.string("wincon." + key, key);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return raw;
    }

    /** Sends a message to a match plus the console, never other matches. */
    public void sendToInstance(GameInstance instance, String key, Map<String, String> values) {
        if (messages.isDisabled(key)) return;
        Component rendered = messages.component(key, values);
        for (Player recipient : onlineAssignedPlayers(instance)) {
            recipient.sendMessage(rendered);
        }
        Bukkit.getConsoleSender().sendMessage(rendered);
    }

    /** Plays a match sound for a match's online players. */
    public void playInstanceSound(GameInstance instance, String key) {
        for (Player recipient : onlineAssignedPlayers(instance)) {
            sounds.playSound(recipient, key);
        }
    }

    /** Plays the neutral click for a match's online players. */
    public void playInstanceNeutral(GameInstance instance) {
        for (Player recipient : onlineAssignedPlayers(instance)) {
            sounds.playNeutralSound(recipient);
        }
    }

    /** Sends a pre-rendered message to a match plus the console, never other matches. */
    public void sendToInstanceComponent(GameInstance instance, Component rendered) {
        for (Player recipient : onlineAssignedPlayers(instance)) {
            recipient.sendMessage(rendered);
        }
        Bukkit.getConsoleSender().sendMessage(rendered);
    }

    /**
     * Resolves an instance id typed in a command: a match id first, then a
     * world-engine cell index as an alias. Empty when unparsable or unknown.
     */
    public Optional<GameInstance> resolveInstance(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        long id;
        try {
            id = Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        Optional<GameInstance> byMatch = instance(id);
        return byMatch.isPresent() ? byMatch : instanceByCell(id);
    }

    /** Lobby id used when a start has no other context; negative disables it. */
    public int defaultStartLobbyId() {
        return lobbies.multiLobbyAllowed() ? lobbies.defaultLobbyId() : 0;
    }
    public GameStateCommandManager stateCommands() { return stateCommands; }
    public Set<String> settingNames() { return configService.settingNames(); }
    public boolean getSetting(String setting) { return configService.getBoolean(setting, false); }
    public Object getSettingValue(String setting) { return configService.getValue(setting); }
    /** Sets a scalar setting parsed from a raw string. Returns false on invalid input. */
    public boolean setSetting(String setting, String rawValue) { return configService.setValue(setting, rawValue); }

    /** Registers a listener invoked whenever a match starts. */
    public void addGameStartListener(Consumer<GameInstance> listener) { gameStartListeners.add(listener); }

    /** Registers a listener invoked when a game actually begins (after pre-start window). */
    public void addBeginGameListener(Consumer<GameInstance> listener) { beginGameListeners.add(listener); }

    /** Registers a listener invoked when a match ends. */
    public void addGameEndListener(Consumer<GameInstance> listener) { gameEndListeners.add(listener); }

    /**
     * Starts a match from the default lobby. False when starting is disabled
     * (negative default), the lobby is missing or already running, or its
     * queue lacks a hunter or a speedrunner.
     */
    public boolean start() {
        int lobbyId = defaultStartLobbyId();
        if (lobbyId < 0) {
            return false;
        }
        return start(lobbyId);
    }

    /**
     * Starts a match for one lobby's queued hunters and speedrunners. Other
     * lobbies keep queueing and their matches and countdowns are untouched.
     *
     * @return false when the lobby is missing, already has a live match, or
     *         its queue lacks a hunter or a speedrunner
     */
    public boolean start(int lobbyId) {
        if (instanceForLobby(lobbyId).isPresent()) return false;
        Optional<Lobby> lobby = lobbies.get(lobbyId);
        if (lobby.isEmpty()) return false;
        Lobby resolved = lobby.get();
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> resolved.contains(p.getUniqueId()) && role(p).isParticipant())
                .map(p -> (Player) p).toList();
        if (players.stream().noneMatch(p -> role(p) == Role.HUNTER)
                || players.stream().noneMatch(p -> role(p) == Role.SPEEDRUNNER)) return false;
        cancelAutostartCountdown(lobbyId, false);
        // Remove any lingering invulnerability from a previous game end. Only
        // this match's players are touched so a concurrent match sitting in
        // its end delay keeps its protection.
        players.forEach(p -> p.setInvulnerable(false));
        matchId++;
        long currentMatchId = matchId;
        List<UUID> assignees = players.stream().map(Player::getUniqueId).toList();
        playerStates.clearMatchFor(assignees);
        for (Player player : players) {
            initMatchStats(currentMatchId, player);
            if (role(player) == Role.SPEEDRUNNER) {
                playerStates.setSpeedrunnerAlive(player.getUniqueId(), true);
            }
            playerStates.recordLastSeen(player, player.getLocation());
            playerStates.setLives(player.getUniqueId(), livesFor(role(player)));
        }
        List<Player> spectators = lobbyNonePlayers(resolved);
        // A second match drops the real border: concurrent matches are
        // confined by per-instance pseudo-borders instead.
        boolean firstMatch = instances.isEmpty();
        if (!firstMatch) {
            worldEngine.clearInstanceBorders();
        }
        OptionalLong matchCell = worldEngine.onMatchStart(players, spectators, firstMatch, lobbyId, currentMatchId);
        GameInstance instance = new GameInstance(currentMatchId, lobbyId, matchCell,
                System.currentTimeMillis());
        if (lobbies.multiLobbyAllowed()
                && MidMatchPolicy.parse(plugin.getConfig()
                        .getString("lobbies.mid-match-setplayer", "SUBLOBBY")) == MidMatchPolicy.SUBLOBBY) {
            instance.setSubLobby(new SubLobby(lobbyId, lobbies.nextSubId(lobbyId)));
        }
        for (UUID playerId : assignees) {
            instance.activate(playerId);
        }
        // Watchers join the assignment too, so win titles, sounds, and
        // broadcasts reach them like everyone else. Only SPECTATOR-role
        // watchers are forced into spectator mode; NONEs keep lobby rules.
        for (Player spectator : spectators) {
            instance.activate(spectator.getUniqueId());
            initMatchStats(currentMatchId, spectator);
            playerStates.setLives(spectator.getUniqueId(), livesFor(role(spectator)));
            if (role(spectator) == Role.SPECTATOR) {
                spectator.setGameMode(GameMode.SPECTATOR);
            }
        }
        instances.put(currentMatchId, instance);
        // Apply the configured default state and custom start commands before
        // giving role equipment. In particular, default clear-inventory must
        // not remove the hunter compass. Runs after the teleport so ON_START
        // modifiers resolve participants from the registered instance.
        stateCommands.runStart(currentMatchId, players, spectators, lobbyId);
        for (Player player : players) {
            if (role(player).isParticipant()) { compass.giveCompass(player); compass.refreshCompass(player); }
        }
        // Set participants to adventure mode during the pre-start window if
        // configured, preventing block breaking while waiting for the first
        // speedrunner hit.
        if (getSetting("settings.start-on-speedrunner-damage.enabled")
                && plugin.getConfig().getBoolean("settings.start-on-speedrunner-damage.start-in-adventure-mode", true)) {
            for (Player player : players) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
        // A headstart configured for one side holds the other side in
        // spectator while the configured side plays. Held sides only move to
        // spectator when the opposite countdown actually begins; when
        // start-on-speedrunner-damage is enabled, that happens only after
        // the speedrunner first damages a hunter.
        armHeadstarts(instance);
        if (!getSetting("settings.start-on-speedrunner-damage.enabled")) {
            beginHeadstarts(instance);
        }
        sendToInstance(instance, "manhunt.start-success", Map.of());
        gameStartListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(new JMatchStartEvent(currentMatchId, lobbyId, matchCell));
        playInstanceNeutral(instance);
        showStatusToInstance(instance, players);
        announceRoles(players, spectators);
        // load waiting delay configuration (enforces a 5 second minimum;
        // -1 waits indefinitely)
        instance.setWaitingDelayConfigured(WaitingReminder.clampDelay(
                plugin.getConfig().getInt("settings.start-on-speedrunner-damage.delay-seconds", 30)));
        if (getSetting("settings.start-on-speedrunner-damage.enabled")) scheduleWaitingReminder(instance);
        else beginGame(instance);
        plugin.logger().debug("debug.match-start",
                Map.of("lobby", String.valueOf(lobbyId), "index", cellString(instance)));
        logBorderMode();
        return true;
    }

    /**
     * Adds players to a running match with the given role, moving them into
     * the match's lobby. Players already in any live match are skipped, as is
     * everyone when the match is ending. Returns the number added.
     */
    public int joinPlayers(GameInstance instance, List<Player> players, Role role) {
        if (!instance.active() || instance.ending()) {
            return 0;
        }
        int added = 0;
        for (Player player : players) {
            UUID playerId = player.getUniqueId();
            if (instance.isActive(playerId) || isInLiveInstance(playerId)) {
                continue;
            }
            lobbies.setLobby(playerId, instance.originLobbyId());
            playerStates.setRole(player, role);
            plugin.roleTeams().sync(player);
            instance.activate(playerId);
            initMatchStats(instance.matchId(), player);
            if (role == Role.SPEEDRUNNER) {
                playerStates.setSpeedrunnerAlive(playerId, true);
            }
            playerStates.setLives(playerId, livesFor(role));
            if (instance.cellIndex().isPresent()) {
                worldEngine.teleportJoinersToCell(List.of(player), instance.cellIndex().getAsLong());
            }
            playerStates.recordLastSeen(player, player.getLocation());
            if (role.isParticipant()) {
                compass.giveCompass(player);
                compass.refreshCompass(player);
            } else if (role == Role.SPECTATOR
                    // NONE joiners take spectator gamemode only with the toggle;
                    // AFK cannot join at all (rejected in gameJoin).
                    || plugin.getConfig().getBoolean("settings.roles.turn-nones-spectator.enabled", false)) {
                player.setGameMode(GameMode.SPECTATOR);
            }
            if (!instance.begun()
                    && plugin.getConfig().getBoolean("settings.start-on-speedrunner-damage.start-in-adventure-mode", true)
                    && role.isParticipant()
                    && !instance.headstart(opposite(role)).armed()) {
                player.setGameMode(GameMode.ADVENTURE);
            }
            // A joiner is held while the opposite side's headstart runs: a
            // hunter headstart holds speedrunners, and vice versa.
            HeadstartState headstart = instance.headstart(opposite(role));
            if (role.isParticipant() && headstart.task() != null) {
                headstart.returnPoints().put(playerId, player.getLocation());
                player.setGameMode(GameMode.SPECTATOR);
            }
            Bukkit.getPluginManager().callEvent(new JPlayerJoinMatchEvent(
                    instance.matchId(), playerId, roleToPlayerRole(role)));
            sendToInstance(instance, "game.join-announce",
                    Map.of("player", player.getName(), "role", messages.roleName(role)));
            added++;
        }
        return added;
    }

    /** Where a match leaver goes, from settings.game-leave.destination. */
    public enum LeaveDestination {
        SPECTATOR,
        LOBBY;

        /** Parses case-insensitively; unknown values fall back to SPECTATOR. */
        public static LeaveDestination parse(String raw) {
            if (raw != null && raw.trim().equalsIgnoreCase("LOBBY")) {
                return LOBBY;
            }
            return SPECTATOR;
        }
    }

    /** Configured leave destination, SPECTATOR by default. */
    public LeaveDestination leaveDestination() {
        return LeaveDestination.parse(plugin.getConfig().getString("settings.game-leave.destination", "SPECTATOR"));
    }

    /**
     * Removes players from a match: deactivates them, clears their match
     * state, and moves them to the configured destination. Begun-match
     * participants drop their gear when {@code dropGear} is true (voluntary
     * leave) or are wiped match-end style when false (auto-leave); pre-start
     * leavers keep everything. Hunter and speedrunner departures are
     * announced to the lobby with the remaining role count. Returns the
     * number removed. A last leaver ends the match for the other side.
     */
    public int leaveMatch(GameInstance instance, List<Player> leavers, boolean dropGear) {
        if (!instance.active()) {
            return 0;
        }
        LeaveDestination destination = leaveDestination();
        int removed = 0;
        List<Role> leftRoles = new ArrayList<>();
        List<String> leftNames = new ArrayList<>();
        for (Player player : leavers) {
            UUID playerId = player.getUniqueId();
            if (!instance.isActive(playerId)) {
                continue;
            }
            Role before = role(player);
            if (instance.begun() && before.isParticipant()) {
                if (dropGear) {
                    dropAllGear(player);
                    stateCommands.resetVitals(player);
                } else {
                    stateCommands.resetPlayer(player);
                }
            }
            playerStates.setSpeedrunnerAlive(playerId, false);
            instance.deactivate(playerId);
            playerStates.clearMatchFor(List.of(playerId));
            compass.removeCompasses(player);
            if (destination == LeaveDestination.LOBBY) {
                playerStates.setRole(player, Role.NONE);
                worldEngine.teleportToLobby(List.of(player), instance.originLobbyId());
                worldEngine.setSpawnToLobby(List.of(player), instance.originLobbyId());
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            } else {
                playerStates.setRole(player, Role.SPECTATOR);
                player.setGameMode(GameMode.SPECTATOR);
                if (!dropGear && instance.cellIndex().isPresent()) {
                    // Auto-leave pulled them out of bounds: put the watcher
                    // back in the cell instead of stranding them outside it.
                    worldEngine.teleportJoinersToCell(List.of(player), instance.cellIndex().getAsLong());
                }
            }
            plugin.roleTeams().sync(player);
            messages.message(player, "game.leave-success", Map.of());
            leftRoles.add(before);
            leftNames.add(player.getName());
            removed++;
        }
        for (int index = 0; index < leftRoles.size(); index++) {
            Role before = leftRoles.get(index);
            if (before == Role.HUNTER) {
                sendToLobby(instance.originLobbyId(), "game.hunter-left",
                        Map.of("player", leftNames.get(index),
                                "remaining", String.valueOf(activeHunterCount(instance))));
            } else if (before == Role.SPEEDRUNNER) {
                sendToLobby(instance.originLobbyId(), "game.speedrunner-left",
                        Map.of("player", leftNames.get(index),
                                "remaining", String.valueOf(activeRunnerCount(instance))));
            }
        }
        if (removed > 0) {
            finishIfBucketEmpty(instance);
        }
        return removed;
    }

    /**
     * Removes a begun-match participant standing outside their cell or in
     * the lobby world, with a reason notice. The End is skipped like the
     * border enforcement, and while borders are enabled they confine
     * instead. Returns true when the player was removed.
     */
    public boolean autoLeaveIfOutside(Player player, Location at) {
        Optional<GameInstance> match = instanceOf(player.getUniqueId());
        if (match.isEmpty() || !match.get().begun() || match.get().ending()) {
            return false;
        }
        if (!playerStates.role(player).isParticipant()) {
            return false;
        }
        GameInstance instance = match.get();
        if (at.getWorld() != null && at.getWorld().getName().equals(lobbyWorldName())) {
            messages.message(player, "game.auto-left-lobby-world", Map.of());
            leaveMatch(instance, List.of(player), false);
            return true;
        }
        if (at.getWorld() == null) {
            return false;
        }
        World.Environment environment = at.getWorld().getEnvironment();
        if (environment != World.Environment.NORMAL && environment != World.Environment.NETHER) {
            return false;
        }
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled() || instance.cellIndex().isEmpty()) {
            return false;
        }
        if (config.worldBorderEnabled()) {
            // Borders confine instead: the rubber-band brings them back.
            return false;
        }
        boolean nether = environment == World.Environment.NETHER;
        CellBounds bounds = CellBounds.forCell(instance.cellIndex().getAsLong(),
                config.cellSize(), config.startBorderDiameter(), !instance.begun());
        if (bounds.contains(at.getX(), at.getZ(), nether)) {
            return false;
        }
        messages.message(player, "game.auto-left-bounds", Map.of());
        leaveMatch(instance, List.of(player), false);
        return true;
    }

    /** Drops a player's full gear at their feet, death style. */
    private static void dropAllGear(Player player) {
        Location at = player.getLocation();
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            dropStack(world, at, item);
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            dropStack(world, at, item);
        }
        dropStack(world, at, player.getInventory().getItemInOffHand());
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
    }

    private static void dropStack(World world, Location at, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            world.dropItemNaturally(at, item);
        }
    }

    /** Fresh per-match stat row for one participant. */
    private void initMatchStats(long matchId, Player player) {
        Stats playerStats = stats.getOrCreate(matchId, player.getUniqueId());
        playerStats.player = player.getName();
        playerStats.uuid = player.getUniqueId();
        playerStats.role = role(player);
        playerStats.matchStartedAt = System.currentTimeMillis();
    }

    /** Configured starting lives for a role. -1 means unlimited. */
    private int livesFor(Role role) {
        if (role == Role.HUNTER) {
            return plugin.getConfig().getInt("settings.hunter-respawn.lives.hunter", -1);
        }
        if (role == Role.SPEEDRUNNER) {
            return plugin.getConfig().getInt("settings.hunter-respawn.lives.speedrunner", 1);
        }
        return -1;
    }

    /**
     * Tells each participant their own role when a match starts. This runs
     * inside {@link #start()} after the match status is shown, but still
     * before the pre-start window opens, so it always plays before any damage
     * can occur. Non-participants are skipped. Sounds play as part of the
     * announcement: when both chat and title are disabled, nothing plays at
     * all.
     */
    private void announceRoles(List<Player> players, List<Player> spectators) {
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
            Map<String, String> values = Map.of("role", messages.roleName(playerRole));
            if (chat) {
                messages.message(player, "manhunt.role-announce-chat", values);
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
        for (Player spectator : spectators) {
            if (role(spectator) != Role.SPECTATOR) {
                continue;
            }
            Map<String, String> values = Map.of("role", messages.roleName(Role.SPECTATOR));
            if (chat) {
                messages.message(spectator, "manhunt.role-announce-chat", values);
            }
            if (title) {
                spectator.showTitle(Title.title(
                        messages.component("manhunt.role-announce-title", values),
                        messages.component("manhunt.role-announce-subtitle-spectator"), times));
            }
            sounds.playSound(spectator, "announce.spectator");
        }
    }

    private static long toMillis(double seconds) {
        return Math.max(0L, Math.round(seconds * 1000.0));
    }

    /** Ends the match when exactly one is live; a no-op otherwise. */
    public void finish(Role winner) {
        singleLiveInstance().ifPresent(instance -> finish(instance, winner));
    }

    /** Ends one match. */
    public void finish(GameInstance instance, Role winner) {
        finish(instance, winner, false);
    }

    /**
     * Ends one match. When {@code immediate} is true the configured
     * {@code match.end-delay} is skipped: statistics post instantly and the
     * final cleanup runs at once instead of after the delay. An immediate end
     * during an in-progress end delay finishes the match at once instead of
     * being blocked.
     */
    public void finish(GameInstance instance, Role winner, boolean immediate) {
        if (instance.ending()) {
            if (immediate) {
                showEndStatsOnce(instance);
                finishEndPhase(instance);
            }
            return;
        }
        instance.setEnding(true);
        gameEndListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(new JMatchEndEvent(instance.matchId(), roleToPlayerRole(winner)));
        cancelWaitingTasks(instance);
        cancelHeadstarts(instance);
        cancelTimeLimit(instance);

        String title = winner == Role.HUNTER ? "game.hunters-title" : "game.speedrunners-title";
        sendToInstanceComponent(instance, messages.renderLiteral(getWinMessage(winner), Map.of()));
        for (Player player : onlineAssignedPlayers(instance)) {
            player.showTitle(Title.title(messages.component(title), Component.empty(),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        playerStates.resetOfflinePlayers(Bukkit.getOnlinePlayers(), instance.assignedPlayerIds());
        playInstanceSound(instance, winner == Role.HUNTER ? "game.fail-sound" : "game.win-sound");
        stats.completeMatch(instance.matchId(), winner);

        // Make all players invulnerable on game end if configured
        if (configService.getBoolean("settings.invulnerability.on-game-end.enabled", true)) {
            onlineAssignedPlayers(instance).forEach(p -> p.setInvulnerable(true));
        }

        // cancel interval modifiers early so they don't fire during the end delay
        stateCommands.cancelIntervalModifiers(instance.matchId());
        // ran before the delay to ensure that any commands that depend on the match being completed can run immediately
        stateCommands.runConsoleCleanup();
        stateCommands.runPlayerCleanup(onlineActivePlayers(instance));

        long delay = immediate
                ? 0L
                : Math.max(0L, Math.round(plugin.getConfig().getDouble("match.end-delay", 10.0) * 20.0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> showEndStatsOnce(instance), delay / 2);
        Bukkit.getScheduler().runTaskLater(plugin, () -> finishEndPhase(instance), delay);
    }

    /** Sends end-of-match statistics, exactly once per match. */
    private void showEndStatsOnce(GameInstance instance) {
        if (instance.endStatsShown()) return;
        instance.setEndStatsShown(true);
        stats.showStats(instance.matchId(), onlineAssignedPlayers(instance));
    }

    /** Runs end commands and deactivates the match, exactly once per match. */
    private void finishEndPhase(GameInstance instance) {
        if (instance.endPhaseDone()) return;
        instance.setEndPhaseDone(true);
        teardownNow(instance);
    }

    /**
     * Shared immediate teardown tail: end commands, lobby teleport, role
     * reset, and deactivation. Callers run their own announcements, stat
     * handling, and cleanup commands first.
     */
    private void teardownNow(GameInstance instance) {
        long teardownId = instance.matchId();
        List<Player> participants = onlineAssignedPlayers(instance).stream()
                .filter(p -> role(p).isParticipant()).toList();
        List<Player> spectators = instanceNonePlayers(instance);
        boolean lastMatch = instances.size() <= 1;
        stateCommands.runEnd(teardownId, participants, spectators, instance.originLobbyId(), lastMatch);
        worldEngine.onMatchEnd(participants, spectators, instance.originLobbyId(), teardownId);
        if (plugin.getConfig().getBoolean("settings.roles.reset-on-game-end.enabled", true)) {
            playerStates.resetRoles(instance.assignedPlayerIds());
        }
        // A finished match fields no sides, even when roles are kept.
        plugin.roleTeams().removeAll(onlineAssignedPlayers(instance));
        plugin.spawnCamp().clearMatch(teardownId);
        instance.setActive(false);
        playerStates.clearMatchFor(instance.assignedPlayerIds());
        stats.clearMatch(teardownId);
        instances.remove(teardownId);
        plugin.logger().debug("debug.match-end", Map.of("index", cellString(instance)));
        worldEngine.prepareNextCell();
        restoreSingleBorder();
        logBorderMode();
        updateAutostartState();
    }

    /** Cancels the match when exactly one is live; a no-op otherwise. */
    public void cancel() { singleLiveInstance().ifPresent(instance -> cancel(instance)); }

    /** Cancels one match with no winner. */
    public void cancel(GameInstance instance) { cancel(instance, false); }

    /**
     * Cancels one match with no winner. Career statistics are not
     * saved, but the in-memory match statistics still back the end screen.
     * Runs the normal end delay intermission unless immediate skips
     * straight to teardown. No JMatchEndEvent fires (there is no winner);
     * a JMatchCancelEvent fires instead.
     */
    public void cancel(GameInstance instance, boolean immediate) {
        if (!instance.active()) return;
        if (instance.ending()) {
            if (immediate) {
                showEndStatsOnce(instance);
                finishEndPhase(instance);
            }
            return;
        }
        instance.setEnding(true);
        gameEndListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(new JMatchCancelEvent(instance.matchId()));
        cancelWaitingTasks(instance);
        cancelHeadstarts(instance);
        cancelTimeLimit(instance);

        sendToInstance(instance, "game.cancelled", Map.of());
        for (Player player : onlineAssignedPlayers(instance)) {
            player.showTitle(Title.title(messages.component("game.cancelled-title"), Component.empty(),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        playerStates.resetOfflinePlayers(Bukkit.getOnlinePlayers(), instance.assignedPlayerIds());
        playInstanceSound(instance, "game.cancelled-sound");

        // Make all players invulnerable on cancel if configured
        if (configService.getBoolean("settings.invulnerability.on-game-end.enabled", true)) {
            onlineAssignedPlayers(instance).forEach(p -> p.setInvulnerable(true));
        }

        // cancel interval modifiers early so they don't fire during the end delay
        stateCommands.cancelIntervalModifiers(instance.matchId());
        // ran before the delay to ensure that any commands that depend on the match being completed can run immediately
        stateCommands.runConsoleCleanup();
        stateCommands.runPlayerCleanup(onlineActivePlayers(instance));

        long delay = immediate
                ? 0L
                : Math.max(0L, Math.round(plugin.getConfig().getDouble("match.end-delay", 10.0) * 20.0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> showEndStatsOnce(instance), delay / 2);
        Bukkit.getScheduler().runTaskLater(plugin, () -> finishEndPhase(instance), delay);
    }

    /** Ends the match next tick when exactly one is live; a no-op otherwise. */
    public void finishLater(Role winner) {
        singleLiveInstance().ifPresent(instance -> finishLater(instance, winner));
    }

    /** Ends one match on the next tick. */
    public void finishLater(GameInstance instance, Role winner) {
        Bukkit.getScheduler().runTask(plugin, () -> finish(instance, winner));
    }

    /** The live match when exactly one runs; empty with zero or concurrent matches. */
    private Optional<GameInstance> singleLiveInstance() {
        List<GameInstance> live = liveInstances();
        return live.size() == 1 ? Optional.of(live.get(0)) : Optional.empty();
    }

    /** Begins the match when exactly one is live; a no-op otherwise. */
    public void beginGame() {
        singleLiveInstance().ifPresent(this::beginGame);
    }

    /** Begins one match: real gameplay starts for its participants. */
    public void beginGame(GameInstance instance) {
        if (instance.begun()) return;
        instance.setBegun(true);
        // The match clock (elapsed status, time-limit countdown) ignores
        // the pre-start wait: it anchors here, when play actually starts.
        instance.setStartedAtMillis(System.currentTimeMillis());
        scheduleTimeLimit(instance, instance.matchId());
        cancelWaitingTasks(instance);
        // Restore participants to survival when the game begins if they were
        // set to adventure mode during the pre-start window. Held headstart
        // sides stay out: their countdown moves them to spectator below.
        if (plugin.getConfig().getBoolean("settings.start-on-speedrunner-damage.start-in-adventure-mode", true)) {
            for (Player player : onlineActivePlayers(instance)) {
                Role playerRole = role(player);
                if (playerRole.isParticipant() && !instance.headstart(opposite(playerRole)).armed()) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
        }
        sendToInstance(instance, "manhunt.started-by-damage", Map.of());
        playInstanceNeutral(instance);
        worldEngine.onBeginGame();
        beginGameListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(new JGameBeginEvent(instance.matchId()));
        // AFTER pre-start-order modifiers waited out the pre-start window;
        // their ON_START sequence runs now instead of at match start.
        stateCommands.runPostStartModifiers(instance.matchId());
        stateCommands.startIntervalModifiers(instance.matchId());
        // Armed headstarts begin counting now (the countdown only starts once
        // the speedrunner first damages a hunter).
        beginHeadstarts(instance);
    }

    /** Arms the configured headstart sides for a match. */
    private void armHeadstarts(GameInstance instance) {
        armHeadstartSide(instance, Role.HUNTER, Headstart.parse(plugin.getConfig(), "hunter"));
        armHeadstartSide(instance, Role.SPEEDRUNNER, Headstart.parse(plugin.getConfig(), "speedrunner"));
    }

    private void armHeadstartSide(GameInstance instance, Role role, Headstart side) {
        HeadstartState state = instance.headstart(role);
        boolean armed = side.enabled() && side.delaySeconds() > 0;
        state.setArmed(armed);
        state.setRemaining(armed ? side.delaySeconds() : 0);
    }

    /** Starts the countdown for every armed headstart side. */
    private void beginHeadstarts(GameInstance instance) {
        beginHeadstart(instance, Role.HUNTER);
        beginHeadstart(instance, Role.SPEEDRUNNER);
    }

    /**
     * Starts one side's headstart countdown. A headstart configured for a
     * side holds the OPPOSITE side: a hunter headstart freezes speedrunners
     * so the hunters get a head start. Held players stay in spectator mode
     * until the delay expires, then are teleported back to their recorded
     * spawnpoints and restored to survival.
     */
    private void beginHeadstart(GameInstance instance, Role role) {
        HeadstartState state = instance.headstart(role);
        if (!state.armed() || state.task() != null) return;
        Role held = opposite(role);
        // Each held player's current location is recorded so endHeadstart()
        // can return them to their spawnpoint even if they flew elsewhere,
        // including across dimensions since the location carries its world.
        state.returnPoints().clear();
        for (Player player : onlineActivePlayers(instance)) {
            if (role(player) == held) {
                state.returnPoints().put(player.getUniqueId(), player.getLocation());
                player.setGameMode(GameMode.SPECTATOR);
            }
        }
        sendToInstance(instance, "manhunt.headstart-active",
                Map.of("seconds", String.valueOf(state.remaining()), "role", messages.roleName(held)));
        long headstartMatchId = instance.matchId();
        state.setTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (instances.get(headstartMatchId) != instance || !instance.active()) {
                cancelHeadstartTask(state);
                return;
            }
            state.setRemaining(state.remaining() - 1);
            if (state.remaining() <= 0) {
                endHeadstart(instance, role);
            } else if (state.remaining() <= 5) {
                sendToInstance(instance, "manhunt.headstart-ending",
                        Map.of("seconds", String.valueOf(state.remaining()),
                                "role", messages.roleName(opposite(role))));
                playInstanceSound(instance, "game.autostart-countdown");
            }
        }, 20L, 20L));
    }

    /**
     * Ends one side's headstart, returning held players to their recorded
     * spawnpoints and restoring them to survival mode.
     */
    private void endHeadstart(GameInstance instance, Role role) {
        HeadstartState state = instance.headstart(role);
        cancelHeadstartTask(state);
        state.setArmed(false);
        Role held = opposite(role);
        for (Player player : onlineActivePlayers(instance)) {
            if (role(player) == held) {
                Location returnPoint = state.returnPoints().remove(player.getUniqueId());
                if (returnPoint != null && returnPoint.getWorld() != null) {
                    player.teleport(returnPoint);
                }
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
        state.returnPoints().clear();
        sendToInstance(instance, "manhunt.headstart-ended", Map.of("role", messages.roleName(held)));
        playInstanceNeutral(instance);
    }

    /** The other participant side; non-participants map to themselves. Pure for tests. */
    static Role opposite(Role role) {
        return switch (role) {
            case HUNTER -> Role.SPEEDRUNNER;
            case SPEEDRUNNER -> Role.HUNTER;
            default -> role;
        };
    }

    /**
     * Drops both headstart holds, restoring held players to survival so a
     * match ending mid-headstart never strands them in spectator.
     */
    private void cancelHeadstarts(GameInstance instance) {
        for (Role role : List.of(Role.HUNTER, Role.SPEEDRUNNER)) {
            HeadstartState state = instance.headstart(role);
            cancelHeadstartTask(state);
            state.setArmed(false);
            state.returnPoints().clear();
            Role held = opposite(role);
            for (Player player : onlineActivePlayers(instance)) {
                if (role(player) == held && player.getGameMode() == GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
        }
    }

    private void cancelHeadstartTask(HeadstartState state) {
        if (state.task() != null) {
            state.task().cancel();
            state.setTask(null);
        }
    }

    private String getWinMessage(Role winner) {
        String text = winner == Role.HUNTER ?
                messages.string("game.hunters-win", "Hunters Win!") :
                messages.string("game.speedrunners-win", "Speedrunners Win!");
        return messages.addSeparators(text);
    }

    private void scheduleWaitingReminder(GameInstance instance) {
        instance.setWaitingStartTime(System.currentTimeMillis());
        int configured = instance.waitingDelayConfigured();
        if (configured > 0) {
            // Finite delay: broadcast exactly three reminders at the delay and
            // two equally-sized slices (e.g. 30s -> 30, 20, 10).
            int slice = WaitingReminder.sliceSeconds(configured);
            List<Integer> checkpoints = List.of(configured,
                    Math.max(1, configured - slice),
                    Math.max(1, configured - 2 * slice));
            sendToInstance(instance, "manhunt.waiting-for-damage", Map.of("seconds", String.valueOf(configured)));
            instance.setWaitingReminderTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (instances.get(instance.matchId()) != instance || instance.begun()) return;
                long elapsedMillis = System.currentTimeMillis() - instance.waitingStartTime();
                int remaining = (int) Math.round(configured - elapsedMillis / 1000.0);
                if (remaining > 0 && checkpoints.contains(remaining)) {
                    sendToInstance(instance, "manhunt.waiting-for-damage", Map.of("seconds", String.valueOf(remaining)));
                }
            }, 20L, 20L));
        } else {
            // Indefinite waiting (-1): use the configured reminder interval and
            // never schedule an expiry.
            double interval = configService.getFloat("match.start-reminder-interval", 10.0f);
            if (interval == -1.0) return;
            long delay = Math.max(1L, Math.round(interval * 20.0));
            sendToInstance(instance, "manhunt.waiting-for-damage-indefinite", Map.of());
            instance.setWaitingReminderTask(Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> {
                        if (instances.get(instance.matchId()) == instance && !instance.begun()) {
                            sendToInstance(instance, "manhunt.waiting-for-damage-indefinite", Map.of());
                        }
                    }, delay, delay));
        }

        // schedule expiry task which ends the waiting period if no damage occurs
        if (configured > 0) {
            long expiryTicks = Math.max(1L, Math.round(configured * 20.0));
            instance.setWaitingExpiryTask(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // only cancel if still active and game hasn't begun and match unchanged
                if (instances.get(instance.matchId()) == instance && instance.active() && !instance.begun()) {
                    if (instance.waitingReminderTask() != null) {
                        instance.waitingReminderTask().cancel();
                        instance.setWaitingReminderTask(null);
                    }
                    boolean forceStart = plugin.getConfig().getString("settings.start-on-speedrunner-damage.on-expire", "CANCEL").equals("FORCE_START");
                    if (forceStart) {
                        sendToInstance(instance, WaitingReminder.expiryMessageKey(true), Map.of());
                    } else {
                        sendToInstance(instance, WaitingReminder.expiryMessageKey(false), Map.of("seconds", String.valueOf(configured)));
                    }
                    // end match as cancelled if configured
                    if (!forceStart) {
                        expireWaitingMatch(instance);
                    } else {
                        // force start the game
                        beginGame(instance);
                    }
                }
            }, expiryTicks));
        }
    }

    /** Aborts a match whose pre-start wait expired, without saving stats. */
    private void expireWaitingMatch(GameInstance instance) {
        // do not save stats
        stats.clearMatch(instance.matchId());
        stateCommands.cancelIntervalModifiers(instance.matchId());
        stateCommands.runConsoleCleanup();
        stateCommands.runPlayerCleanup(onlineActivePlayers(instance));
        List<Player> assigned = onlineAssignedPlayers(instance);
        teardownNow(instance);
        if (configService.getBoolean("settings.invulnerability.on-game-end.enabled", true)) {
            assigned.forEach(p -> p.setInvulnerable(true));
        }
    }

    private void cancelWaitingTasks(GameInstance instance) {
        if (instance.waitingReminderTask() != null) {
            instance.waitingReminderTask().cancel();
            instance.setWaitingReminderTask(null);
        }
        if (instance.waitingExpiryTask() != null) {
            instance.waitingExpiryTask().cancel();
            instance.setWaitingExpiryTask(null);
        }
    }

    public void updateAutostartState() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> updateAutostartState());
            return;
        }
        if (!plugin.getConfig().getBoolean("settings.autostart.enabled", false)) {
            cancelAllAutostartCountdowns(true);
            return;
        }
        pruneAutostartCountdowns();
        for (int lobbyId : lobbies.lobbyIds()) {
            if (!lobbies.multiLobbyAllowed() && lobbyId != 0) {
                continue;
            }
            updateAutostartState(lobbyId);
        }
    }

    /** Drops countdowns for deleted lobbies (and non-zero lobbies with the engine off). */
    private void pruneAutostartCountdowns() {
        for (int lobbyId : List.copyOf(autostartCountdowns.keySet())) {
            if (lobbies.get(lobbyId).isEmpty() || (!lobbies.multiLobbyAllowed() && lobbyId != 0)) {
                cancelAutostartCountdown(lobbyId, false);
            }
        }
    }

    private void updateAutostartState(int lobbyId) {
        Optional<Lobby> lobby = lobbies.get(lobbyId);
        if (lobby.isEmpty() || instanceForLobby(lobbyId).isPresent() || !isEligibleToStart(lobby.get())) {
            cancelAutostartCountdown(lobbyId, true);
            return;
        }
        if (autostartCountdowns.containsKey(lobbyId)) return;
        int configured = Math.max(0, plugin.getConfig().getInt("settings.autostart.countdown-seconds", 60));
        if (configured == 0) {
            start(lobbyId);
            return;
        }
        worldEngine.prepareNextCell();
        AutostartCountdown countdown = new AutostartCountdown();
        countdown.configured = configured;
        countdown.remaining = configured;
        autostartCountdowns.put(lobbyId, countdown);
        sendToLobby(lobbyId, "manhunt.autostart-eligible", Map.of("seconds", String.valueOf(configured)));
        playLobbySound(lobbyId, "game.autostart-countdown");
        announceAutostartCheckpoint(lobbyId, countdown, countdown.remaining);
        countdown.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Optional<Lobby> tickLobby = lobbies.get(lobbyId);
            if (instanceForLobby(lobbyId).isPresent() || tickLobby.isEmpty() || !isEligibleToStart(tickLobby.get())) {
                cancelAutostartCountdown(lobbyId, true);
                return;
            }
            countdown.remaining--;
            if (countdown.remaining <= 0) {
                cancelAutostartCountdown(lobbyId, false);
                start(lobbyId);
                return;
            }
            announceAutostartCheckpoint(lobbyId, countdown, countdown.remaining);
        }, 20L, 20L);
    }

    private void announceAutostartCheckpoint(int lobbyId, AutostartCountdown countdown, int remainingSeconds) {
        if (!AutostartCountdownMessages.shouldAnnounce(remainingSeconds, countdown.configured)) return;
        sendToLobby(lobbyId, "manhunt.autostart-countdown", Map.of("seconds", String.valueOf(remainingSeconds)));
        playLobbySound(lobbyId, "game.autostart-countdown");
    }

    private void cancelAutostartCountdown(int lobbyId, boolean announce) {
        AutostartCountdown countdown = autostartCountdowns.remove(lobbyId);
        if (countdown != null) {
            if (countdown.task != null) {
                countdown.task.cancel();
            }
            if (announce) {
                sendToLobby(lobbyId, "manhunt.autostart-cancelled", Map.of());
            }
        }
    }

    private void cancelAllAutostartCountdowns(boolean announce) {
        for (int lobbyId : List.copyOf(autostartCountdowns.keySet())) {
            cancelAutostartCountdown(lobbyId, announce);
        }
    }

    /** Online lobby members for scoped autostart messages and sounds. */
    private List<Player> lobbyRecipients(int lobbyId) {
        Optional<Lobby> lobby = lobbies.get(lobbyId);
        if (lobby.isEmpty()) {
            return List.of();
        }
        Lobby resolved = lobby.get();
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> resolved.contains(player.getUniqueId()))
                .map(player -> (Player) player).toList();
    }

    private void sendToLobby(int lobbyId, String key, Map<String, String> values) {
        messages.sendTo(lobbyRecipients(lobbyId), key, values);
        // Console keeps seeing every lobby, as with the old broadcasts.
        if (!messages.isDisabled(key)) {
            Bukkit.getConsoleSender().sendMessage(messages.component(key, values));
        }
    }

    /**
     * Announces a passive&lt;-&gt;active role change to the player's lobby
     * mates, excluding the player and anyone in a live match. Same-class
     * changes stay silent. Only the active side of the change is named.
     */
    public void announceRoleChange(Player player, Role from, Role to) {
        if (!configService.getBoolean("settings.announce-role-changes", false)) {
            return;
        }
        if (from.isParticipant() == to.isParticipant()) {
            return;
        }
        Role active = to.isParticipant() ? to : from;
        String key = to.isParticipant() ? "manhunt.role-is-now" : "manhunt.role-no-longer";
        Map<String, String> values = Map.of("player", player.getName(),
                "active-role", messages.roleName(active));
        Optional<Lobby> lobby = lobbies.lobbyOf(player.getUniqueId());
        if (lobby.isEmpty()) {
            return;
        }
        for (Player recipient : lobbyRecipients(lobby.get().id())) {
            if (recipient.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (instanceOf(recipient.getUniqueId()).isPresent()) {
                continue;
            }
            messages.message(recipient, key, values);
        }
    }

    private void playLobbySound(int lobbyId, String key) {
        for (Player recipient : lobbyRecipients(lobbyId)) {
            sounds.playSound(recipient, key);
        }
    }

    private boolean isEligibleToStart(Lobby lobby) {
        boolean hasHunter = false;
        boolean hasSpeedrunner = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!lobby.contains(player.getUniqueId())) {
                continue;
            }
            Role playerRole = role(player);
            if (playerRole == Role.HUNTER) hasHunter = true;
            else if (playerRole == Role.SPEEDRUNNER) hasSpeedrunner = true;
            if (hasHunter && hasSpeedrunner) return true;
        }
        return false;
    }

    /** Shows the starting roster to one match's players. */
    private void showStatusToInstance(GameInstance instance, List<Player> players) {
        for (Player recipient : onlineAssignedPlayers(instance)) {
            messages.message(recipient, "manhunt.status-header", Map.of("status", "ACTIVE"));
            sendRoleSection(recipient, players, Role.SPEEDRUNNER, "manhunt.speedrunners-header");
            sendRoleSection(recipient, players, Role.HUNTER, "manhunt.hunters-header");
            sendRoleSection(recipient, players, Role.AFK, "manhunt.afk-header");
            sendRoleSection(recipient, players, Role.NONE, "manhunt.none-header");
        }
    }

    private void sendRoleSection(Player receiver, List<Player> players, Role role, String headerKey) {
        List<String> names = new ArrayList<>();
        for (Player player : players) {
            if (role(player) == role) names.add(player.getName());
        }
        if (names.isEmpty()) return;
        messages.message(receiver, headerKey, Map.of());
        names.sort(String::compareTo);
        for (String line : ListFormatter.chunk(names, 10)) {
            messages.message(receiver, "manhunt.status-player", Map.of("player", line));
        }
    }

    private Role role(Player player) { return playerStates.role(player); }

    /** Online lobby members watching without playing: NONE and SPECTATOR, never AFK. */
    private List<Player> lobbyNonePlayers(Lobby lobby) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> isWatching(role(p)) && lobby.contains(p.getUniqueId()))
                .map(p -> (Player) p).toList();
    }

    /** Online match assignees watching without playing, including eliminated hunters. */
    private List<Player> instanceNonePlayers(GameInstance instance) {
        Set<UUID> assigned = instance.assignedPlayerIds();
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> isWatching(role(p)) && assigned.contains(p.getUniqueId()))
                .map(p -> (Player) p).toList();
    }

    private static boolean isWatching(Role role) {
        return role == Role.NONE || role == Role.SPECTATOR;
    }

    /** Debug label for a match cell, "none" when the engine is off. */
    private static String cellString(GameInstance instance) {
        return instance.cellIndex().isPresent()
                ? String.valueOf(instance.cellIndex().getAsLong())
                : "none";
    }

    /** Logs the active border mode; silent when borders cannot apply. */
    private void logBorderMode() {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled() || !config.worldBorderEnabled()) {
            return;
        }
        plugin.logger().debug("debug.border-mode",
                Map.of("mode", BorderMode.resolve(instances.size(), true, true).name()));
    }

    /**
     * Hands the real border to the surviving match when concurrency drops
     * back to one. Honors its start-border phase when it has not begun yet.
     */
    private void restoreSingleBorder() {
        if (instances.size() != 1) {
            return;
        }
        GameInstance survivor = liveInstances().get(0);
        if (survivor.cellIndex().isPresent()) {
            worldEngine.applyInstanceBorder(survivor.cellIndex().getAsLong(), survivor.begun());
        }
    }

    /**
     * Confines concurrent matches to their cells: players outside their
     * cell are rubber-banded back in and take border damage past the damage
     * buffer. Spectators bypass it like the vanilla border, and the End is
     * left alone until end cells land.
     */
    private void enforcePseudoBorders() {
        if (instances.size() < 2) {
            return;
        }
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled() || !config.worldBorderEnabled()) {
            return;
        }
        for (GameInstance instance : liveInstances()) {
            if (instance.cellIndex().isEmpty()) {
                continue;
            }
            CellBounds bounds = CellBounds.forCell(instance.cellIndex().getAsLong(),
                    config.cellSize(), config.startBorderDiameter(), !instance.begun());
            for (Player player : onlineActivePlayers(instance)) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                World.Environment environment = player.getWorld().getEnvironment();
                if (environment != World.Environment.NETHER && environment != World.Environment.NORMAL) {
                    continue;
                }
                boolean nether = environment == World.Environment.NETHER;
                Location location = player.getLocation();
                double outside = bounds.outsideBy(location.getX(), location.getZ(), nether);
                if (outside <= 0.0) {
                    continue;
                }
                double[] inside = bounds.clampInside(location.getX(), location.getZ(), nether, 2.0);
                if (inside != null) {
                    player.teleport(new Location(location.getWorld(), inside[0], location.getY(), inside[1],
                            location.getYaw(), location.getPitch()));
                }
                if (outside > config.damageBuffer() && config.damageAmount() > 0.0) {
                    player.damage(config.damageAmount());
                }
            }
        }
    }

    /** Current world-engine cell index, or empty when the store is unavailable. */
    public OptionalLong cellIndex() { return worldEngine.cellIndex(); }

    /** Current world-engine cell index cap for the live cell size. */
    public long cellIndexCap() { return worldEngine.cellIndexCap(); }

    /** Buffered ready-cell indexes, oldest first. */
    public List<Long> bufferedCellIndexes() { return worldEngine.bufferedCellIndexes(); }

    /** Configured lobby world name. */
    public String lobbyWorldName() { return worldEngine.lobbyWorldName(); }

    /** True when newcomers have a lobby to wait in. */
    public boolean hasLobbyLocation(int lobbyId) { return worldEngine.hasLobbyLocation(lobbyId); }

    /**
     * Parks a lobby-less newcomer in the newest running match as a
     * spectator. Cell matches place them via the join spread; cell-less
     * matches fall back to the game world spawn. False when no match runs.
     */
    public boolean joinLeastTimeMatch(Player player) {
        Optional<GameInstance> target = leastTimeMatch(liveInstances());
        if (target.isEmpty()) {
            return false;
        }
        GameInstance instance = target.get();
        joinPlayers(instance, List.of(player), Role.SPECTATOR);
        if (instance.cellIndex().isEmpty()) {
            teleportToGameWorldSpawn(player);
        }
        return true;
    }

    /** Newest live match (running the least time); empty when none runs. Pure for tests. */
    static Optional<GameInstance> leastTimeMatch(java.util.Collection<GameInstance> instances) {
        return instances.stream()
                .filter(instance -> instance.active() && !instance.ending())
                .max(Comparator.comparingLong(GameInstance::startedAtMillis));
    }

    private void teleportToGameWorldSpawn(Player player) {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        World world = Bukkit.getWorld(config.worldName());
        Location spawn = world != null ? world.getSpawnLocation() : player.getWorld().getSpawnLocation();
        player.teleport(spawn);
        player.setRespawnLocation(spawn, true);
    }

    /** True when the lobby world is loaded or has a folder waiting. */
    public boolean lobbyWorldExists() { return worldEngine.lobbyWorldExists(); }

    /** True when lobby-world-name collides with the game world name. */
    public boolean lobbyWorldNameClashes() { return worldEngine.lobbyWorldNameClashes(); }

    /** Warns on a lobby/game world name clash. True when clean. */
    public boolean validateLobbyWorldName() { return worldEngine.validateLobbyWorldName(); }

    /**
     * Arms or confirms lobby-world generation for one sender key. True only
     * on a matching second call within the timeout.
     */
    public boolean confirmLobbyGeneration(String senderKey) { return worldEngine.confirmLobbyGeneration(senderKey); }

    /** Loads or generates the lobby world. Empty when creation fails. */
    public Optional<LobbyWorld> ensureLobbyWorld() {
        return worldEngine.ensureLobbyWorld();
    }

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
            case SPECTATOR -> com.jruk8.jmanhunt.api.PlayerRole.SPECTATOR;
        };
    }


    /**
     * Quick-starts a match by assigning eligible players of one lobby to
     * teams and immediately starting the game, bypassing the autostart
     * system. Queue caps apply unless forced; cap-skipped players keep
     * their current role and may fail validation below.
     *
     * @param speedrunnerPercent the percentage of convertible players that
     *                           should become speedrunners (0-100), or -1 for
     *                           default (keep teams, converting only what is
     *                           missing to start)
     * @param force when true, queue caps are ignored and NONE players join
     *              the convertible pool
     * @param lobbyId the lobby whose members form the convertible pool
     * @return the start result plus any roles blocked by caps
     */
    public QuickStartOutcome quickStart(int speedrunnerPercent, boolean force, int lobbyId) {
        if (instanceForLobby(lobbyId).isPresent()) return new QuickStartOutcome(false, Set.of());
        Optional<Lobby> lobby = lobbies.get(lobbyId);
        if (lobby.isEmpty()) return new QuickStartOutcome(false, Set.of());
        Lobby resolved = lobby.get();
        // Every online participant lobby member is convertible: existing
        // hunters and speedrunners keep their roles unless conversion is
        // needed. AFK players and spectators are never touched, and NONEs
        // sit out like AFK unless forced — the one case where NONE can
        // still be picked.
        List<Player> pool = Bukkit.getOnlinePlayers().stream()
                .filter(p -> role(p) != Role.AFK && role(p) != Role.SPECTATOR
                        && (force || role(p) != Role.NONE))
                .filter(p -> resolved.contains(p.getUniqueId()))
                .map(p -> (Player) p)
                .toList();
        // Cancel any autostart countdown silently
        cancelAllAutostartCountdowns(false);
        Set<Role> capped = new java.util.HashSet<>();
        // A match needs at least one hunter and one speedrunner, so with
        // fewer than two convertible players there is nothing to assign.
        if (pool.size() < 2) return new QuickStartOutcome(start(lobbyId), capped);
        if (speedrunnerPercent < 0) {
            ensureMinimumTeams(pool, force, resolved, capped);
        } else {
            // Percentage-based assignment over the whole convertible pool:
            // random selection, players become speedrunners until the count
            // is reached and hunters after that.
            int speedrunnerCount = quickStartSpeedrunnerCount(pool.size(), speedrunnerPercent);
            List<Player> shuffled = new ArrayList<>(pool);
            java.util.Collections.shuffle(shuffled);
            int runners = 0;
            for (Player player : shuffled) {
                Role want = runners < speedrunnerCount ? Role.SPEEDRUNNER : Role.HUNTER;
                if (!force && !allowsInLobby(resolved, want)) {
                    capped.add(want);
                    continue;
                }
                playerStates.setRole(player, want);
                plugin.roleTeams().sync(player);
                if (want == Role.SPEEDRUNNER) {
                    runners++;
                }
            }
        }
        // Validate after assignment: start() requires at least one hunter and
        // one speedrunner, so e.g. two players online with one AFK will fail.
        return new QuickStartOutcome(start(lobbyId), capped);
    }

    /** True when one more player may take a role in a lobby queue. */
    private boolean allowsInLobby(Lobby lobby, Role role) {
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (role(online) == role && lobby.contains(online.getUniqueId())) {
                count++;
            }
        }
        return CapLimits.allows(count, plugin.getConfig().getInt(
                "lobbies.queue-caps." + role.name().toLowerCase(Locale.ROOT), -1));
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
     * everyone else keeps their current role. Conversions that would overflow
     * a queue cap are skipped and recorded instead.
     */
    private void ensureMinimumTeams(List<Player> pool, boolean force, Lobby lobby, Set<Role> capped) {
        boolean hasHunter = pool.stream().anyMatch(p -> role(p) == Role.HUNTER);
        boolean hasSpeedrunner = pool.stream().anyMatch(p -> role(p) == Role.SPEEDRUNNER);
        Player converted = null;
        if (!hasSpeedrunner) {
            if (!force && !allowsInLobby(lobby, Role.SPEEDRUNNER)) {
                capped.add(Role.SPEEDRUNNER);
            } else {
                converted = pickConvertible(pool, null);
                if (converted != null) {
                    playerStates.setRole(converted, Role.SPEEDRUNNER);
                    plugin.roleTeams().sync(converted);
                }
            }
        }
        if (!hasHunter) {
            if (!force && !allowsInLobby(lobby, Role.HUNTER)) {
                capped.add(Role.HUNTER);
            } else {
                Player hunter = pickConvertible(pool, converted);
                if (hunter != null) {
                    playerStates.setRole(hunter, Role.HUNTER);
                    plugin.roleTeams().sync(hunter);
                }
            }
        }
        for (Player p : pool) {
            if (role(p) != Role.NONE) {
                continue;
            }
            if (!force && !allowsInLobby(lobby, Role.HUNTER)) {
                capped.add(Role.HUNTER);
                continue;
            }
            playerStates.setRole(p, Role.HUNTER);
            plugin.roleTeams().sync(p);
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
