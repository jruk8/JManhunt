package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.core.DebugLevel;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.api.events.JGameBeginEvent;
import com.jruk8.jmanhunt.api.events.JMatchStartEvent;
import com.jruk8.jmanhunt.api.events.JPlayerJoinMatchEvent;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.SubLobby;
import com.jruk8.jmanhunt.message.DebugMessages;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.stats.Stats;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.teleport.MatchTeleportService;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.world.WorldEngineService;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
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
import com.jruk8.jmanhunt.match.StatusRosterService;
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
    private final GameMessages game;
    private final ManhuntMessages manhunt;
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
    private final MatchAnnounceService announce;
    private final QuickStartService quickStart;
    private final List<Consumer<GameInstance>> gameStartListeners = new ArrayList<>();
    private final List<Consumer<GameInstance>> beginGameListeners = new ArrayList<>();

    public MatchStartService(JManhuntPlugin plugin, MessageService messages, GameMessages game,
            ManhuntMessages manhunt, SoundService sounds,
            PlayerStateStore playerStates, CompassManager compass, StatsManager stats,
            GameStateCommandManager stateCommands, ConfigService configService,
            WorldEngineService worldEngine, LobbyService lobbies, MatchStore store,
            MatchMessaging messaging, TimeLimitService timeLimits, PrestartService prestart,
            AutostartService autostart) {
        this.plugin = plugin;
        this.messages = messages;
        this.game = game;
        this.manhunt = manhunt;
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
        this.announce = new MatchAnnounceService(plugin, messages, sounds,
                playerStates, store, new StatusRosterService(messages, manhunt, playerStates),
                manhunt);
        this.quickStart = new QuickStartService(plugin, lobbies, playerStates, store, autostart,
                this::start);
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
     * Under sublobby policies the new match becomes the next child
     * sublobby even while siblings run.
     *
     * @return false when the lobby is missing, a live match blocks the
     *         start under the mid-match policy, or its queue lacks a
     *         hunter or a speedrunner
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
        if (quickStart.blocksStart(lobbyId)) {
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
        participants.forEach(lobbies::restoreCollisions); // match wins; fakes re-disable below
        spectators.forEach(lobbies::applyLobbyCollisions); // stayers keep lobby rules
        OptionalLong matchCell = worldEngine.onMatchStart(participants, lobbyId);
        GameInstance instance = createMatchInstance(lobbyId, currentMatchId, matchCell, assignees);
        instance.setStartCenter(engineOffStartCenter(participants, surroundOrigin, matchCell));
        store.registerInstance(instance);
        promoteQueuedSpectators(instance, lobby.get());
        stats.recordLobbySession(lobbyId);
        applyStartState(instance, participants, spectators, lobbyId);
        publishMatchStart(instance, participants, spectators, lobbyId, matchCell);
        beginMatchPlay(instance);
        plugin.logger().debug(DebugLevel.INFO, DebugMessages::getMatchStart,
                Map.of("lobby", String.valueOf(lobbyId), "index", GameManager.cellString(instance)));
        return true;
    }

    /**
     * Engine-off start center: surrounds participants around the origin
     * and returns the center, or null when the engine runs the match.
     */
    private Location engineOffStartCenter(List<Player> participants, Location surroundOrigin,
            OptionalLong matchCell) {
        if (matchCell.isPresent() || configService.getBoolean("world-engine.enabled", false)) {
            return null;
        }
        return surroundParticipants(participants, surroundOrigin);
    }

    /** Lobby id used when a start has no other context; negative disables it. */
    public int defaultStartLobbyId() {
        return lobbies.multiLobbyAllowed() ? lobbies.defaultLobbyId() : 0;
    }

    /** Attaches queued spectators of a starting lobby to the new match, all policies. */
    private void promoteQueuedSpectators(GameInstance instance, Lobby lobby) {
        List<Player> queued = Bukkit.getOnlinePlayers().stream()
                .filter(p -> lobby.contains(p.getUniqueId())
                        && isQueuedSpectator(playerStates.role(p),
                                store.isInLiveInstance(p.getUniqueId())))
                .map(p -> (Player) p).toList();
        joinPlayers(instance, queued, Role.SPECTATOR);
    }

    /**
     * True when a lobby member queues as a spectator for a starting
     * match: spectator role only, never participants, idlers, or AFK,
     * and not already in a live match. Pure for tests.
     */
    static boolean isQueuedSpectator(Role role, boolean inLiveMatch) {
        return role == Role.SPECTATOR && !inLiveMatch;
    }

    /** Collects queued lobby participants, excluding live matches. */
    private Optional<List<Player>> matchPlayers(Lobby resolved) {
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> resolved.contains(p.getUniqueId())
                        && playerStates.role(p).isParticipant()
                        && !store.isInLiveInstance(p.getUniqueId()))
                .map(p -> (Player) p).toList();
        boolean ready = players.stream().anyMatch(p -> playerStates.role(p) == Role.HUNTER)
                && players.stream().anyMatch(p -> playerStates.role(p) == Role.SPEEDRUNNER);
        return ready ? Optional.of(players) : Optional.empty();
    }

    /** Clears stale match state and seeds per-player stats. Returns the assignee ids. */
    private List<UUID> prepareMatchPlayers(List<Player> players, long currentMatchId) {
        List<UUID> assignees = players.stream().map(Player::getUniqueId).toList();
        playerStates.clearMatchFor(assignees);
        Integer lobby = store.lobbyOf(currentMatchId);
        for (Player player : players) {
            initMatchStats(currentMatchId, player);
            if (playerStates.role(player) == Role.SPEEDRUNNER) {
                playerStates.setSpeedrunnerAlive(player.getUniqueId(), true);
            }
            playerStates.recordLastSeen(player, player.getLocation());
            playerStates.setLives(player.getUniqueId(), livesFor(lobby, playerStates.role(player)));
        }
        return assignees;
    }

    /**
     * Creates the instance and activates participants only. Lobby
     * watchers stay in the lobby: they join the match explicitly via
     * game join or the spectator browser instead of leaking in here.
     */
    private GameInstance createMatchInstance(int lobbyId, long currentMatchId, OptionalLong matchCell,
            List<UUID> assignees) {
        GameInstance instance = new GameInstance(currentMatchId, lobbyId, matchCell,
                System.currentTimeMillis());
        if (lobbies.multiLobbyAllowed() && lobbies.midMatchPolicy().usesSubLobbies()) {
            instance.setSubLobby(new SubLobby(lobbyId, lobbies.nextSubId(lobbyId)));
        }
        for (UUID playerId : assignees) {
            instance.activate(playerId);
        }
        stateCommands.trackMatchEntry(assignees);
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
        if (plugin.overrides().getBoolean(lobbyId,
                        "settings.match.start-on-speedrunner-damage.enabled", false)
                && plugin.overrides().getBoolean(lobbyId,
                        "settings.match.start-on-speedrunner-damage.start-in-adventure-mode", true)) {
            for (Player player : players) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    /** Broadcasts the match start to players, listeners, and the API event. */
    private void publishMatchStart(GameInstance instance, List<Player> players,
            List<Player> spectators, int lobbyId, OptionalLong matchCell) {
        messaging.sendToInstance(instance, manhunt.getStartSuccess(), Map.of());
        gameStartListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(new JMatchStartEvent(instance.matchId(), lobbyId, matchCell));
        messaging.playInstanceNeutral(instance);
        announce.showStatusToInstance(instance, players);
        announce.announceRoles(lobbyId, players, spectators);
    }

    /** Arms headstarts, then begins play or waits for the first hit. */
    private void beginMatchPlay(GameInstance instance) {
        Integer lobby = instance.originLobbyId();
        // A headstart configured for one side holds the other side in
        // spectator while the configured side plays. Held sides only move to
        // spectator when the opposite countdown actually begins; when
        // start-on-speedrunner-damage is enabled, that happens only after
        // the speedrunner first damages a hunter.
        prestart.armHeadstarts(instance);
        boolean gated = plugin.overrides().getBoolean(lobby,
                "settings.match.start-on-speedrunner-damage.enabled", false);
        if (!gated) {
            prestart.beginHeadstarts(instance);
        }
        // load waiting delay configuration (enforces a 5 second minimum;
        // -1 waits indefinitely)
        instance.setWaitingDelayConfigured(WaitingReminder.clampDelay(
                plugin.overrides().getInt(lobby,
                        "settings.match.start-on-speedrunner-damage.delay-seconds", 30)));
        if (gated) {
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
        WorldEngineConfig spawnConfig = WorldEngineConfig.fromConfig(configService);
        List<Location> spawns = MatchTeleportService.spreadSpawnsForConfig(world, centerX, centerZ,
                SURROUND_RADIUS, participants, spawnConfig);
        for (int index = 0; index < participants.size(); index++) {
            participants.get(index).teleport(spawns.get(index));
        }
        return center;
    }

    /** Random origin in the game world for executor-less starts. */
    private Location fallbackOrigin() {
        World world = Bukkit.getWorld(configService.getString("world-engine.world-name", "world"));
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
        return (int) players.stream().filter(player -> joinPlayer(instance, player, role)).count();
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
        playerStates.setLives(playerId, livesFor(instance.originLobbyId(), role));
        if (instance.cellIndex().isPresent()) {
            worldEngine.teleportJoinersToCell(instance, List.of(player), instance.cellIndex().getAsLong());
        }
        playerStates.recordLastSeen(player, player.getLocation());
        if (role.isParticipant()) {
            compass.giveCompass(player);
            compass.refreshCompass(player);
        }
        lobbies.restoreCollisions(player); // match wins; fake enable below re-disables
        applyJoinGameMode(instance, player, role);
        Bukkit.getPluginManager().callEvent(new JPlayerJoinMatchEvent(
                instance.matchId(), playerId, GameManager.roleToPlayerRole(role)));
        messaging.sendToInstance(instance, game.getJoinAnnounce(),
                Map.of("player", player.getName(), "role", messages.roleName(role)));
        stateCommands.trackMatchEntry(List.of(playerId));
        return true;
    }

    /** Applies the fake spectator, pre-start, and headstart-hold modes for a joiner. */
    private void applyJoinGameMode(GameInstance instance, Player player, Role role) {
        Integer lobby = instance.originLobbyId();
        if (role == Role.SPECTATOR
                // NONE joiners take fake spectator mode only with the toggle;
                // AFK cannot join at all (rejected in gameJoin).
                || (!role.isParticipant() && plugin.overrides().getBoolean(lobby,
                        "settings.players.roles.turn-nones-spectator.enabled", false))) {
            plugin.fakeSpectators().enable(player);
        }
        if (!instance.begun()
                && plugin.overrides().getBoolean(lobby,
                        "settings.match.start-on-speedrunner-damage.start-in-adventure-mode", true)
                && role.isParticipant()
                && !instance.headstart(role.opposite()).armed()) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        // A joiner is held while the opposite side's headstart runs: a
        // hunter headstart holds speedrunners, and vice versa.
        HeadstartState headstart = instance.headstart(role.opposite());
        if (role.isParticipant() && headstart.task() != null) {
            headstart.returnPoints().put(player.getUniqueId(), player.getLocation());
            plugin.fakeSpectators().enable(player);
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
        // sides stay out: their countdown moves them to fake spectator below.
        if (plugin.overrides().getBoolean(instance.originLobbyId(),
                "settings.match.start-on-speedrunner-damage.start-in-adventure-mode", true)) {
            for (Player player : store.onlineActivePlayers(instance)) {
                Role playerRole = playerStates.role(player);
                if (playerRole.isParticipant() && !instance.headstart(playerRole.opposite()).armed()) {
                    plugin.fakeSpectators().disable(player);
                }
            }
        }
        messaging.sendToInstance(instance, manhunt.getStartedByDamage(), Map.of());
        messaging.playInstanceNeutral(instance);
        // Dedicated begin cue, after any prestart window: begin runs once
        // per match (the begun guard above), so this plays exactly once.
        messaging.playInstanceSound(instance, "game.match-started");
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

    /** Fresh per-match stat row for one participant; spectators never get one. */
    private void initMatchStats(long matchId, Player player) {
        if (!playerStates.role(player).isParticipant()) { return; }
        Stats playerStats = stats.getOrCreate(matchId, player.getUniqueId());
        playerStats.player = player.getName();
        playerStats.uuid = player.getUniqueId();
        playerStats.role = playerStates.role(player);
        playerStats.matchStartedAt = System.currentTimeMillis();
    }
    /** Configured starting lives for a role. -1 means unlimited. */
    private int livesFor(Integer lobby, Role role) {
        return switch (role) {
            case HUNTER -> plugin.overrides().getInt(lobby, "settings.players.respawn.hunter.lives", -1);
            case SPEEDRUNNER -> plugin.overrides().getInt(lobby, "settings.players.respawn.speedrunner.lives", 1);
            default -> -1;
        };
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
        WorldEngineConfig config = WorldEngineConfig.fromConfig(configService);
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
    /**
     * Quick-starts a match by assigning eligible players of one lobby to
     * teams and immediately starting the game, bypassing the autostart
     * system. Queue caps never apply and NONE players always join the
     * convertible pool. See {@link QuickStartService}.
     *
     * @param speedrunnerPercent the percentage of convertible players that
     *                           should become speedrunners (0-100), or -1 for
     *                           default (keep teams, converting only what is
     *                           missing to start)
     * @param lobbyId the lobby whose members form the convertible pool
     * @return whether the match started
     */
    public QuickStartOutcome quickStart(int speedrunnerPercent, int lobbyId) {
        return quickStart.quickStart(speedrunnerPercent, lobbyId);
    }

    /**
     * Same, but the surround origin gathers participants when the world
     * engine is off: a player executor's location, or null for a random
     * wilderness point (console).
     */
    public QuickStartOutcome quickStart(int speedrunnerPercent, int lobbyId, Location surroundOrigin) {
        return quickStart.quickStart(speedrunnerPercent, lobbyId, surroundOrigin);
    }
}
