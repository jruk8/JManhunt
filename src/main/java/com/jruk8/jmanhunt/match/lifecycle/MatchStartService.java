package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.api.events.JGameBeginEvent;
import com.jruk8.jmanhunt.api.events.JMatchStartEvent;
import com.jruk8.jmanhunt.api.events.JPlayerJoinMatchEvent;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.MidMatchPolicy;
import com.jruk8.jmanhunt.lobby.SubLobby;
import com.jruk8.jmanhunt.message.ListFormatter;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.stats.Stats;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.teleport.MatchTeleportService;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.world.WorldEngineService;

import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.match.autostart.AutostartService;
import com.jruk8.jmanhunt.match.prestart.HeadstartState;
import com.jruk8.jmanhunt.match.prestart.PrestartService;
import com.jruk8.jmanhunt.match.prestart.WaitingReminder;

/**
 * Starts matches and grows them: lobby starts, quick starts, mid-match
 * joins, and the begin-play transition. GameManager keeps thin delegates.
 */
public final class MatchStartService {
    /** Engine-off surround scatter around the origin, in blocks. */
    static final int SURROUND_RADIUS = 5;
    /** Fallback origin spread: random point within this of 0,0. */
    static final int SURROUND_FALLBACK_RADIUS = 5000;

    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final SoundService sounds;
    private final PlayerStateStore playerStates;
    private final CompassManager compass;
    private final StatsManager stats;
    private final GameStateCommandManager stateCommands;
    private final ConfigService configService;
    private final WorldEngineService worldEngine;
    private final LobbyService lobbies;
    private final MatchStore store;
    private final MatchMessaging messaging;
    private final TimeLimitService timeLimits;
    private final PrestartService prestart;
    private final AutostartService autostart;
    private final MatchFinishService finishService;
    private final List<Consumer<GameInstance>> gameStartListeners = new ArrayList<>();
    private final List<Consumer<GameInstance>> beginGameListeners = new ArrayList<>();

    public MatchStartService(JManhuntPlugin plugin, MessageService messages, SoundService sounds,
            PlayerStateStore playerStates, CompassManager compass, StatsManager stats,
            GameStateCommandManager stateCommands, ConfigService configService,
            WorldEngineService worldEngine, LobbyService lobbies, MatchStore store,
            MatchMessaging messaging, TimeLimitService timeLimits, PrestartService prestart,
            AutostartService autostart, MatchFinishService finishService) {
        this.plugin = plugin;
        this.messages = messages;
        this.sounds = sounds;
        this.playerStates = playerStates;
        this.compass = compass;
        this.stats = stats;
        this.stateCommands = stateCommands;
        this.configService = configService;
        this.worldEngine = worldEngine;
        this.lobbies = lobbies;
        this.store = store;
        this.messaging = messaging;
        this.timeLimits = timeLimits;
        this.prestart = prestart;
        this.autostart = autostart;
        this.finishService = finishService;
    }

    public void addGameStartListener(Consumer<GameInstance> listener) {
        gameStartListeners.add(listener);
    }

    public void addBeginGameListener(Consumer<GameInstance> listener) {
        beginGameListeners.add(listener);
    }

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
        return start(lobbyId, null);
    }

    /**
     * Same, but the surround origin gathers participants when the world
     * engine is off: a player executor's location, or null for a random
     * wilderness point (console and autostart).
     */
    public boolean start(int lobbyId, Location surroundOrigin) {
        if (store.instanceForLobby(lobbyId).isPresent()) {
            return false;
        }
        Optional<Lobby> lobby = lobbies.get(lobbyId);
        if (lobby.isEmpty()) {
            return false;
        }
        Optional<List<Player>> players = matchPlayers(lobby.get());
        if (players.isEmpty()) {
            return false;
        }
        List<Player> participants = players.get();
        autostart.cancelAutostartCountdown(lobbyId, false);
        // Remove any lingering invulnerability from a previous game end. Only
        // this match's players are touched so a concurrent match sitting in
        // its end delay keeps its protection.
        participants.forEach(p -> p.setInvulnerable(false));
        long currentMatchId = store.nextMatchId();
        List<UUID> assignees = prepareMatchPlayers(participants, currentMatchId);
        List<Player> spectators = lobbyNonePlayers(lobby.get());
        // A second match drops the real border: concurrent matches are
        // confined by per-instance pseudo-borders instead.
        boolean firstMatch = store.isEmpty();
        if (!firstMatch) {
            worldEngine.clearInstanceBorders();
        }
        OptionalLong matchCell = worldEngine.onMatchStart(participants, spectators, firstMatch,
                lobbyId, currentMatchId);
        Location startCenter = null;
        if (matchCell.isEmpty() && !plugin.getConfig().getBoolean("world-engine.enabled", false)) {
            startCenter = surroundParticipants(participants, surroundOrigin);
        }
        GameInstance instance = createMatchInstance(lobbyId, currentMatchId, matchCell,
                assignees, spectators);
        instance.setStartCenter(startCenter);
        store.registerInstance(instance);
        applyStartState(instance, participants, spectators, lobbyId);
        publishMatchStart(instance, participants, spectators, lobbyId, matchCell);
        beginMatchPlay(instance);
        plugin.logger().debug("debug.match-start",
                Map.of("lobby", String.valueOf(lobbyId), "index", GameManager.cellString(instance)));
        finishService.logBorderMode();
        return true;
    }

    /** Lobby id used when a start has no other context; negative disables it. */
    public int defaultStartLobbyId() {
        return lobbies.multiLobbyAllowed() ? lobbies.defaultLobbyId() : 0;
    }

    /** Collects lobby participants, requiring at least one hunter and one speedrunner. */
    private Optional<List<Player>> matchPlayers(Lobby resolved) {
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> resolved.contains(p.getUniqueId())
                        && playerStates.role(p).isParticipant())
                .map(p -> (Player) p).toList();
        if (players.stream().noneMatch(p -> playerStates.role(p) == Role.HUNTER)
                || players.stream().noneMatch(p -> playerStates.role(p) == Role.SPEEDRUNNER)) {
            return Optional.empty();
        }
        return Optional.of(players);
    }

    /** Clears stale match state and seeds per-player stats. Returns the assignee ids. */
    private List<UUID> prepareMatchPlayers(List<Player> players, long currentMatchId) {
        List<UUID> assignees = players.stream().map(Player::getUniqueId).toList();
        playerStates.clearMatchFor(assignees);
        for (Player player : players) {
            initMatchStats(currentMatchId, player);
            if (playerStates.role(player) == Role.SPEEDRUNNER) {
                playerStates.setSpeedrunnerAlive(player.getUniqueId(), true);
            }
            playerStates.recordLastSeen(player, player.getLocation());
            playerStates.setLives(player.getUniqueId(), livesFor(playerStates.role(player)));
        }
        return assignees;
    }

    /** Creates the instance and activates participants and watchers. */
    private GameInstance createMatchInstance(int lobbyId, long currentMatchId, OptionalLong matchCell,
            List<UUID> assignees, List<Player> spectators) {
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
            playerStates.setLives(spectator.getUniqueId(), livesFor(playerStates.role(spectator)));
            if (playerStates.role(spectator) == Role.SPECTATOR) {
                spectator.setGameMode(GameMode.SPECTATOR);
            }
        }
        return instance;
    }

    /** Applies start commands, role equipment, and the pre-start game mode. */
    private void applyStartState(GameInstance instance, List<Player> players,
            List<Player> spectators, int lobbyId) {
        // Apply the configured default state and custom start commands before
        // giving role equipment. In particular, default clear-inventory must
        // not remove the hunter compass. Runs after the teleport so ON_START
        // modifiers resolve participants from the registered instance.
        stateCommands.runStart(instance.matchId(), players, spectators, lobbyId);
        for (Player player : players) {
            if (playerStates.role(player).isParticipant()) {
                compass.giveCompass(player);
                compass.refreshCompass(player);
            }
        }
        // Set participants to adventure mode during the pre-start window if
        // configured, preventing block breaking while waiting for the first
        // speedrunner hit.
        if (configService.getBoolean("settings.start-on-speedrunner-damage.enabled", false)
                && plugin.getConfig().getBoolean(
                        "settings.start-on-speedrunner-damage.start-in-adventure-mode", true)) {
            for (Player player : players) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    /** Broadcasts the match start to players, listeners, and the API event. */
    private void publishMatchStart(GameInstance instance, List<Player> players,
            List<Player> spectators, int lobbyId, OptionalLong matchCell) {
        messaging.sendToInstance(instance, "manhunt.start-success", Map.of());
        gameStartListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(new JMatchStartEvent(instance.matchId(), lobbyId, matchCell));
        messaging.playInstanceNeutral(instance);
        showStatusToInstance(instance, players);
        announceRoles(players, spectators);
    }

    /** Arms headstarts, then begins play or waits for the first hit. */
    private void beginMatchPlay(GameInstance instance) {
        // A headstart configured for one side holds the other side in
        // spectator while the configured side plays. Held sides only move to
        // spectator when the opposite countdown actually begins; when
        // start-on-speedrunner-damage is enabled, that happens only after
        // the speedrunner first damages a hunter.
        prestart.armHeadstarts(instance);
        if (!configService.getBoolean("settings.start-on-speedrunner-damage.enabled", false)) {
            prestart.beginHeadstarts(instance);
        }
        // load waiting delay configuration (enforces a 5 second minimum;
        // -1 waits indefinitely)
        instance.setWaitingDelayConfigured(WaitingReminder.clampDelay(
                plugin.getConfig().getInt("settings.start-on-speedrunner-damage.delay-seconds", 30)));
        if (configService.getBoolean("settings.start-on-speedrunner-damage.enabled", false)) {
            prestart.scheduleWaitingReminder(instance);
        } else {
            beginGame(instance);
        }
    }

    /**
     * Gathers participants around the surround origin when the world
     * engine is off, using the same safe-spawn scatter as cell spawns.
     * A null origin (console, autostart) falls back to a random
     * wilderness point in the game world. Returns the center used, or
     * null when nobody scattered.
     */
    private Location surroundParticipants(List<Player> participants, Location surroundOrigin) {
        if (participants.isEmpty()) {
            return null;
        }
        Location center = surroundOrigin != null ? surroundOrigin.clone() : fallbackOrigin();
        if (center == null || center.getWorld() == null) {
            return null;
        }
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        WorldEngineConfig spawnConfig = WorldEngineConfig.fromConfig(plugin.getConfig());
        for (Player player : participants) {
            player.teleport(MatchTeleportService.spreadSpawnForConfig(world, centerX, centerZ, SURROUND_RADIUS,
                    player.getLocation().getYaw(), player.getLocation().getPitch(), spawnConfig));
        }
        return center;
    }

    /** Random origin in the game world for executor-less starts. */
    private Location fallbackOrigin() {
        World world = Bukkit.getWorld(plugin.getConfig().getString("world-engine.world-name", "world"));
        if (world == null) {
            return null;
        }
        int x = ThreadLocalRandom.current().nextInt(-SURROUND_FALLBACK_RADIUS, SURROUND_FALLBACK_RADIUS + 1);
        int z = ThreadLocalRandom.current().nextInt(-SURROUND_FALLBACK_RADIUS, SURROUND_FALLBACK_RADIUS + 1);
        return new Location(world, x + 0.5, 64.0, z + 0.5);
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
            if (joinPlayer(instance, player, role)) {
                added++;
            }
        }
        return added;
    }

    /** Joins one player to the instance. Returns false when already assigned. */
    private boolean joinPlayer(GameInstance instance, Player player, Role role) {
        UUID playerId = player.getUniqueId();
        if (instance.isActive(playerId) || store.isInLiveInstance(playerId)) {
            return false;
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
        }
        applyJoinGameMode(instance, player, role);
        Bukkit.getPluginManager().callEvent(new JPlayerJoinMatchEvent(
                instance.matchId(), playerId, GameManager.roleToPlayerRole(role)));
        messaging.sendToInstance(instance, "game.join-announce",
                Map.of("player", player.getName(), "role", messages.roleName(role)));
        return true;
    }

    /** Applies the spectator, pre-start, and headstart-hold game modes for a joiner. */
    private void applyJoinGameMode(GameInstance instance, Player player, Role role) {
        if (!role.isParticipant() && (role == Role.SPECTATOR
                // NONE joiners take spectator gamemode only with the toggle;
                // AFK cannot join at all (rejected in gameJoin).
                || plugin.getConfig().getBoolean("settings.roles.turn-nones-spectator.enabled", false))) {
            player.setGameMode(GameMode.SPECTATOR);
        }
        if (!instance.begun()
                && plugin.getConfig().getBoolean(
                        "settings.start-on-speedrunner-damage.start-in-adventure-mode", true)
                && role.isParticipant()
                && !instance.headstart(role.opposite()).armed()) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        // A joiner is held while the opposite side's headstart runs: a
        // hunter headstart holds speedrunners, and vice versa.
        HeadstartState headstart = instance.headstart(role.opposite());
        if (role.isParticipant() && headstart.task() != null) {
            headstart.returnPoints().put(player.getUniqueId(), player.getLocation());
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    /** Begins the match when exactly one is live; a no-op otherwise. */
    public void beginGame() {
        store.singleLiveInstance().ifPresent(this::beginGame);
    }

    /** Begins one match: real gameplay starts for its participants. */
    public void beginGame(GameInstance instance) {
        if (instance.begun()) {
            return;
        }
        instance.setBegun(true);
        // The match clock (elapsed status, time-limit countdown) ignores
        // the pre-start wait: it anchors here, when play actually starts.
        instance.setStartedAtMillis(System.currentTimeMillis());
        timeLimits.scheduleTimeLimit(instance, instance.matchId());
        prestart.cancelWaitingTasks(instance);
        // Restore participants to survival when the game begins if they were
        // set to adventure mode during the pre-start window. Held headstart
        // sides stay out: their countdown moves them to spectator below.
        if (plugin.getConfig().getBoolean("settings.start-on-speedrunner-damage.start-in-adventure-mode", true)) {
            for (Player player : store.onlineActivePlayers(instance)) {
                Role playerRole = playerStates.role(player);
                if (playerRole.isParticipant() && !instance.headstart(playerRole.opposite()).armed()) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
        }
        messaging.sendToInstance(instance, "manhunt.started-by-damage", Map.of());
        messaging.playInstanceNeutral(instance);
        worldEngine.onBeginGame();
        beginGameListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(new JGameBeginEvent(instance.matchId()));
        // AFTER pre-start-order modifiers waited out the pre-start window;
        // their ON_START sequence runs now instead of at match start.
        stateCommands.runPostStartModifiers(instance.matchId());
        stateCommands.startIntervalModifiers(instance.matchId());
        // Armed headstarts begin counting now (the countdown only starts once
        // the speedrunner first damages a hunter).
        prestart.beginHeadstarts(instance);
    }

    /** Fresh per-match stat row for one participant. */
    private void initMatchStats(long matchId, Player player) {
        Stats playerStats = stats.getOrCreate(matchId, player.getUniqueId());
        playerStats.player = player.getName();
        playerStats.uuid = player.getUniqueId();
        playerStats.role = playerStates.role(player);
        playerStats.matchStartedAt = System.currentTimeMillis();
    }

    /** Configured starting lives for a role. -1 means unlimited. */
    private int livesFor(Role role) {
        if (role == Role.HUNTER) {
            return plugin.getConfig().getInt("settings.respawn.hunter.lives", -1);
        }
        if (role == Role.SPEEDRUNNER) {
            return plugin.getConfig().getInt("settings.respawn.speedrunner.lives", 1);
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
            Role playerRole = playerStates.role(player);
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
            if (playerStates.role(spectator) != Role.SPECTATOR) {
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

    /** Shows the starting roster to one match's players. */
    private void showStatusToInstance(GameInstance instance, List<Player> players) {
        for (Player recipient : store.onlineAssignedPlayers(instance)) {
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
            if (playerStates.role(player) == role) {
                names.add(player.getName());
            }
        }
        if (names.isEmpty()) {
            return;
        }
        messages.message(receiver, headerKey, Map.of());
        names.sort(String::compareTo);
        for (String line : ListFormatter.chunk(names, 10)) {
            messages.message(receiver, "manhunt.status-player", Map.of("player", line));
        }
    }

    /** Online lobby members watching without playing: NONE and SPECTATOR, never AFK. */
    private List<Player> lobbyNonePlayers(Lobby lobby) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> playerStates.role(p).isWatching() && lobby.contains(p.getUniqueId()))
                .map(p -> (Player) p).toList();
    }

    public boolean joinLeastTimeMatch(Player player) {
        Optional<GameInstance> target = leastTimeMatch(store.liveInstances());
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
    public static Optional<GameInstance> leastTimeMatch(java.util.Collection<GameInstance> instances) {
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

    /**
     * Quick-starts a match by assigning eligible players of one lobby to
     * teams and immediately starting the game, bypassing the autostart
     * system. Queue caps never apply and NONE players always join the
     * convertible pool.
     *
     * @param speedrunnerPercent the percentage of convertible players that
     *                           should become speedrunners (0-100), or -1 for
     *                           default (keep teams, converting only what is
     *                           missing to start)
     * @param lobbyId the lobby whose members form the convertible pool
     * @return whether the match started
     */
    public QuickStartOutcome quickStart(int speedrunnerPercent, int lobbyId) {
        return quickStart(speedrunnerPercent, lobbyId, null);
    }

    /**
     * Same, but the surround origin gathers participants when the world
     * engine is off: a player executor's location, or null for a random
     * wilderness point (console).
     */
    public QuickStartOutcome quickStart(int speedrunnerPercent, int lobbyId, Location surroundOrigin) {
        if (store.instanceForLobby(lobbyId).isPresent()) {
            return new QuickStartOutcome(false);
        }
        Optional<Lobby> lobby = lobbies.get(lobbyId);
        if (lobby.isEmpty()) {
            return new QuickStartOutcome(false);
        }
        Lobby resolved = lobby.get();
        // Every online non-AFK, non-spectator lobby member is convertible:
        // existing hunters and speedrunners keep their roles unless
        // conversion is needed, and NONEs always join the pool.
        List<Player> pool = Bukkit.getOnlinePlayers().stream()
                .filter(p -> playerStates.role(p) != Role.AFK && playerStates.role(p) != Role.SPECTATOR)
                .filter(p -> resolved.contains(p.getUniqueId()))
                .map(p -> (Player) p)
                .toList();
        // Cancel any autostart countdown silently
        autostart.cancelAllAutostartCountdowns(false);
        // A match needs at least one hunter and one speedrunner, so with
        // fewer than two convertible players there is nothing to assign.
        if (pool.size() < 2) {
            return new QuickStartOutcome(start(lobbyId, surroundOrigin));
        }
        if (speedrunnerPercent < 0) {
            ensureMinimumTeams(pool);
        } else {
            assignQuickStartRoles(pool, speedrunnerPercent);
        }
        // Validate after assignment: start() requires at least one hunter and
        // one speedrunner, so e.g. two players online with one AFK will fail.
        return new QuickStartOutcome(start(lobbyId, surroundOrigin));
    }

    /** Assigns quickstart roles by percentage over the convertible pool. */
    private void assignQuickStartRoles(List<Player> pool, int speedrunnerPercent) {
        // Percentage-based assignment over the whole convertible pool:
        // random selection, players become speedrunners until the count
        // is reached and hunters after that.
        int speedrunnerCount = quickStartSpeedrunnerCount(pool.size(), speedrunnerPercent);
        List<Player> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        int runners = 0;
        for (Player player : shuffled) {
            Role want = runners < speedrunnerCount ? Role.SPEEDRUNNER : Role.HUNTER;
            playerStates.setRole(player, want);
            plugin.roleTeams().sync(player);
            if (want == Role.SPEEDRUNNER) {
                runners++;
            }
        }
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
    public static int quickStartSpeedrunnerCount(int poolSize, int percent) {
        return Math.max(1, (int) Math.round(poolSize * percent / 100.0));
    }

    /**
     * Guarantees at least one hunter and one speedrunner by converting random
     * pool members only where a team is missing, so an all-hunter or an
     * all-speedrunner lobby still starts. NONE players become hunters and
     * everyone else keeps their current role.
     */
    private void ensureMinimumTeams(List<Player> pool) {
        boolean hasHunter = pool.stream().anyMatch(p -> playerStates.role(p) == Role.HUNTER);
        boolean hasSpeedrunner = pool.stream().anyMatch(p -> playerStates.role(p) == Role.SPEEDRUNNER);
        Player converted = null;
        if (!hasSpeedrunner) {
            converted = pickConvertible(pool, null);
            if (converted != null) {
                playerStates.setRole(converted, Role.SPEEDRUNNER);
                plugin.roleTeams().sync(converted);
            }
        }
        if (!hasHunter) {
            Player hunter = pickConvertible(pool, converted);
            if (hunter != null) {
                playerStates.setRole(hunter, Role.HUNTER);
                plugin.roleTeams().sync(hunter);
            }
        }
        for (Player p : pool) {
            if (playerStates.role(p) != Role.NONE) {
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
        List<Player> unassigned = candidates.stream()
                .filter(p -> playerStates.role(p) == Role.NONE).toList();
        List<Player> preferred = unassigned.isEmpty() ? candidates : unassigned;
        if (preferred.isEmpty()) {
            return null;
        }
        return preferred.get(ThreadLocalRandom.current().nextInt(preferred.size()));
    }
}
