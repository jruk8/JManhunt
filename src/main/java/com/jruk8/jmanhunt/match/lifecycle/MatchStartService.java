package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.core.DebugLevel;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.api.events.JGameBeginEvent;
import com.jruk8.jmanhunt.api.events.JMatchStartEvent;
import com.jruk8.jmanhunt.api.events.JPlayerJoinMatchEvent;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.lobby.JoinTiming;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.config.MatchSettingsFacade;
import com.jruk8.jmanhunt.lobby.config.PlayersSettingsFacade;
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
import com.jruk8.jmanhunt.match.listeners.PlayerRespawnListener;
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

    /** Match/player settings, config service, engine settings, and logger. */
    public record StartReads(MatchSettingsFacade match, PlayersSettingsFacade players,
            ConfigService config,
            com.jruk8.jmanhunt.config.WorldEngineConfig engineSettings, JManhuntLogger log) {
    }

    /** States, compass, stats, commands, engine, lobbies, store, and phases. */
    public record StartMatch(PlayerStateStore playerStates, CompassManager compass,
            StatsManager stats, GameStateCommandManager stateCommands,
            WorldEngineService worldEngine, LobbyService lobbies, MatchStore store,
            TimeLimitService timeLimits, PrestartService prestart,
            AutostartService autostart) {
    }

    /** Fakes, role teams, respawn listener, sounds, and scheduler. */
    public record StartEdge(FakeSpectatorService fakes, RoleTeamService roleTeams,
            PlayerRespawnListener respawn, SoundService sounds, TaskScheduler tasks) {
    }

    /** Message bus, game/manhunt texts, and match messaging. */
    public record StartTexts(MessageService messages, GameMessages game,
            ManhuntMessages manhunt, MatchMessaging messaging) {
    }

    private final StartReads reads;
    private final StartMatch services;
    private final StartEdge edge;
    private final StartTexts texts;
    private final MatchAnnounceService announce;
    private final QuickStartService quickStart;
    private final List<Consumer<GameInstance>> gameStartListeners = new ArrayList<>();
    private final List<Consumer<GameInstance>> beginGameListeners = new ArrayList<>();

    public MatchStartService(StartReads reads, StartMatch services, StartEdge edge,
            StartTexts texts) {
        this.reads = reads;
        this.services = services;
        this.edge = edge;
        this.texts = texts;
        this.announce = new MatchAnnounceService(reads.players(),
                new MatchAnnounceService.AnnounceTexts(texts.messages(), edge.sounds(),
                        texts.manhunt()),
                new MatchAnnounceService.AnnounceState(services.playerStates(),
                        services.store(),
                        new StatusRosterService(texts.messages(), texts.manhunt(),
                                services.playerStates()),
                        edge.respawn()));
        this.quickStart = new QuickStartService(
                new QuickStartService.QuickRoster(services.lobbies(),
                        services.playerStates(), edge.roleTeams()),
                services.store(), services.autostart(), this::start);
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
        Optional<Lobby> lobby = services.lobbies().get(lobbyId);
        if (lobby.isEmpty()) {
            return false;
        }
        Optional<List<Player>> players = matchPlayers(lobby.get());
        if (players.isEmpty()) {
            return false;
        }
        List<Player> participants = players.get();
        services.autostart().cancelAutostartCountdown(lobbyId, false);
        // Remove any lingering invulnerability from a previous game end. Only
        // this match's players are touched so a concurrent match sitting in
        // its end delay keeps its protection.
        participants.forEach(p -> p.setInvulnerable(false));
        long currentMatchId = services.store().nextMatchId();
        List<UUID> assignees = prepareMatchPlayers(participants, currentMatchId);
        List<Player> spectators = lobbyNonePlayers(lobby.get());
        participants.forEach(services.lobbies()::restoreCollisions); // match wins; fakes re-disable below
        spectators.forEach(services.lobbies()::applyLobbyCollisions); // stayers keep lobby rules
        OptionalLong matchCell = services.worldEngine().onMatchStart(participants, lobbyId);
        GameInstance instance = createMatchInstance(lobbyId, currentMatchId, matchCell, assignees);
        instance.setStartCenter(engineOffStartCenter(participants, surroundOrigin, matchCell));
        services.store().registerInstance(instance);
        promoteQueuedSpectators(instance, lobby.get());
        services.stats().recordLobbySession(lobbyId);
        applyStartState(instance, participants, spectators, lobbyId);
        publishMatchStart(instance, participants, spectators, lobbyId, matchCell);
        beginMatchPlay(instance);
        reads.log().debug(DebugLevel.INFO, DebugMessages::getMatchStart,
                Map.of("lobby", String.valueOf(lobbyId), "index", GameManager.cellString(instance)));
        return true;
    }

    /**
     * Engine-off start center: surrounds participants around the origin
     * and returns the center, or null when the engine runs the match.
     */
    private Location engineOffStartCenter(List<Player> participants, Location surroundOrigin,
            OptionalLong matchCell) {
        if (matchCell.isPresent() || reads.engineSettings().isEnabled()) {
            return null;
        }
        return surroundParticipants(participants, surroundOrigin);
    }

    /** Lobby id used when a start has no other context; negative disables it. */
    public int defaultStartLobbyId() {
        return services.lobbies().multiLobbyAllowed() ? services.lobbies().defaultLobbyId() : 0;
    }

    /** Attaches queued spectators of a starting lobby to the new match, all policies. */
    private void promoteQueuedSpectators(GameInstance instance, Lobby lobby) {
        List<Player> queued = Bukkit.getOnlinePlayers().stream()
                .filter(p -> lobby.contains(p.getUniqueId())
                        && isQueuedSpectator(services.playerStates().role(p),
                                services.store().isInLiveInstance(p.getUniqueId())))
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
                        && services.playerStates().role(p).isParticipant()
                        && !services.store().isInLiveInstance(p.getUniqueId()))
                .map(p -> (Player) p).toList();
        boolean ready = players.stream().anyMatch(p -> services.playerStates().role(p) == Role.HUNTER)
                && players.stream().anyMatch(p -> services.playerStates().role(p) == Role.SPEEDRUNNER);
        return ready ? Optional.of(players) : Optional.empty();
    }

    /** Clears stale match state and seeds per-player stats. Returns the assignee ids. */
    private List<UUID> prepareMatchPlayers(List<Player> players, long currentMatchId) {
        List<UUID> assignees = players.stream().map(Player::getUniqueId).toList();
        services.playerStates().clearMatchFor(assignees);
        Integer lobby = services.store().lobbyOf(currentMatchId);
        for (Player player : players) {
            initMatchStats(currentMatchId, player);
            if (services.playerStates().role(player) == Role.SPEEDRUNNER) {
                services.playerStates().setSpeedrunnerAlive(player.getUniqueId(), true);
            }
            services.playerStates().recordLastSeen(player, player.getLocation());
            services.playerStates().setLives(player.getUniqueId(),
                    livesFor(lobby, services.playerStates().role(player)));
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
        if (services.lobbies().multiLobbyAllowed() && services.lobbies().midMatchPolicy().usesSubLobbies()) {
            instance.setSubLobby(new SubLobby(lobbyId, services.lobbies().nextSubId(lobbyId)));
        }
        for (UUID playerId : assignees) {
            instance.activate(playerId);
        }
        services.stateCommands().trackMatchEntry(assignees);
        return instance;
    }

    /** Applies start commands, role equipment, and the pre-start game mode. */
    private void applyStartState(GameInstance instance, List<Player> players,
            List<Player> spectators, int lobbyId) {
        // Apply the configured default state and custom start commands before
        // giving role equipment. In particular, default clear-inventory must
        // not remove the hunter services.compass(). Runs after the teleport so ON_START
        // modifiers resolve participants from the registered instance.
        services.stateCommands().runStart(instance.matchId(), players, spectators, lobbyId);
        for (Player player : players) {
            if (services.playerStates().role(player).isParticipant()) {
                services.compass().giveCompass(player);
                services.compass().refreshCompass(player);
            }
        }
        // Set participants to adventure mode during the pre-start window if
        // configured, preventing block breaking while waiting for the first
        // speedrunner hit.
        if (reads.match().startOnDamageEnabled(lobbyId)
                && reads.match().startInAdventureMode(lobbyId)) {
            for (Player player : players) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    /** Broadcasts the match start to players, listeners, and the API event. */
    private void publishMatchStart(GameInstance instance, List<Player> players,
            List<Player> spectators, int lobbyId, OptionalLong matchCell) {
        texts.messaging().sendToInstance(instance, texts.manhunt().getStartSuccess(), Map.of());
        gameStartListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(new JMatchStartEvent(instance.matchId(), lobbyId, matchCell));
        texts.messaging().playInstanceNeutral(instance);
        if (reads.config().getBoolean("settings.server.status.show-on-start", true)) {
            announce.showStatusToInstance(instance, players);
        }
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
        services.prestart().armHeadstarts(instance);
        boolean gated = reads.match().startOnDamageEnabled(lobby);
        if (!gated) {
            services.prestart().beginHeadstarts(instance);
        }
        // load waiting delay configuration (enforces a 5 second minimum;
        // -1 waits indefinitely)
        instance.setWaitingDelayConfigured(WaitingReminder.clampDelay(
                reads.match().startOnDamageDelaySeconds(lobby)));
        if (gated) {
            services.prestart().scheduleWaitingReminder(instance);
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
        WorldEngineConfig spawnConfig = WorldEngineConfig.fromSettings(reads.engineSettings());
        List<Location> spawns = MatchTeleportService.spreadSpawnsForConfig(world, centerX, centerZ,
                SURROUND_RADIUS, participants, spawnConfig);
        for (int index = 0; index < participants.size(); index++) {
            participants.get(index).teleport(spawns.get(index));
        }
        return center;
    }

    /** Random origin in the game world for executor-less starts. */
    private Location fallbackOrigin() {
        World world = Bukkit.getWorld(reads.engineSettings().getWorldName());
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
        if (instance.isActive(playerId) || services.store().isInLiveInstance(playerId)) {
            return false;
        }
        Role source = entrantSourceRole(player, instance.originLobbyId());
        services.lobbies().setLobby(playerId, instance.originLobbyId());
        services.playerStates().setRole(player, role);
        edge.roleTeams().sync(player);
        instance.activate(playerId);
        initMatchStats(instance.matchId(), player);
        if (role == Role.SPEEDRUNNER) {
            services.playerStates().setSpeedrunnerAlive(playerId, true);
        }
        services.playerStates().setLives(playerId, livesFor(instance.originLobbyId(), role));
        if (instance.cellIndex().isPresent()) {
            services.worldEngine().teleportJoinersToCell(instance, List.of(player), instance.cellIndex().getAsLong());
        }
        services.playerStates().recordLastSeen(player, player.getLocation());
        if (role.isParticipant()) {
            services.compass().giveCompass(player);
            services.compass().refreshCompass(player);
        }
        services.lobbies().restoreCollisions(player); // match wins; fake enable below re-disables
        applyJoinGameMode(instance, player, role);
        Bukkit.getPluginManager().callEvent(new JPlayerJoinMatchEvent(
                instance.matchId(), playerId, GameManager.roleToPlayerRole(role)));
        texts.messaging().sendToInstance(instance, texts.game().getJoinAnnounce(),
                Map.of("player", player.getName(), "role", texts.messages().roleName(role)));
        services.stateCommands().trackMatchEntry(List.of(playerId));
        applyJoinTiming(instance, player, source, role);
        return true;
    }

    /**
     * Switches one match member to a new role with lives refreshed
     * to the new role value. Position, inventory, pending respawns,
     * and headstart holds stay untouched; future events use the new
     * role. New-role ON_START and ON_RESPAWN catch-up runs when
     * neither fired for this game or life yet. Same-role switches
     * succeed without effect. Always true.
     */
    public boolean switchPlayerRole(GameInstance instance, Player player, Role target) {
        UUID playerId = player.getUniqueId();
        Role source = services.playerStates().role(player);
        if (source == target) {
            return true;
        }
        Role timingSource = entrantSourceRole(player, instance.originLobbyId());
        SwitchPlan plan = planSwitch(source, target, instance.isActive(playerId),
                instance.isHeadstartHeld(playerId));
        services.playerStates().setRole(player, target);
        edge.roleTeams().sync(player);
        if (plan.runnerAlive() != null) {
            services.playerStates().setSpeedrunnerAlive(playerId, plan.runnerAlive());
        }
        services.playerStates().setLives(playerId, livesFor(instance.originLobbyId(), target));
        if (plan.activate()) {
            instance.activate(playerId);
        }
        if (plan.deactivate()) {
            instance.deactivate(playerId);
        }
        // A swap back into the game un-dies the player (no status skull).
        if (target.isParticipant()) {
            instance.clearDeathRecord(playerId);
        }
        applySwitchEdge(instance, player, playerId, plan, source, target);
        runSwitchCatchup(instance, player, target);
        applyJoinTiming(instance, player, timingSource, target);
        return true;
    }

    /** Compass reconcile plus the deferred fakes/compass edge of a switch. */
    private void applySwitchEdge(GameInstance instance, Player player, UUID playerId,
            SwitchPlan plan, Role source, Role target) {
        if (source.isParticipant() || target.isParticipant()) {
            services.compass().clearHotspotHistory(playerId);
            services.compass().reconcileTeammateModes(instance);
        }
        if (plan.participantEdge()) {
            edge.tasks().run(() -> {
                edge.fakes().disable(player);
                if (services.compass().hasCompass(player)) {
                    services.compass().refreshCompassIdentity(player);
                } else {
                    services.compass().giveCompass(player);
                }
                services.compass().refreshCompass(player);
            });
        }
        if (plan.watcherEdge()) {
            edge.tasks().run(() -> edge.fakes().enable(player));
        }
    }

    /** New-role ON_START (once per game) and ON_RESPAWN (once per life) catch-up. */
    private void runSwitchCatchup(GameInstance instance, Player player, Role target) {
        if (!target.isParticipant()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (instance.markStartFired(playerId)) {
            services.stateCommands().runStartForPlayer(instance.matchId(), player);
        }
        if (instance.markRespawnFired(playerId, instance.lifeOf(playerId))) {
            services.stateCommands().runRespawnForPlayer(instance.matchId(), player);
        }
    }

    /** Pure role-switch roster plan behind pswitch. Pure for tests. */
    static record SwitchPlan(boolean activate, boolean deactivate, Boolean runnerAlive,
            boolean participantEdge, boolean watcherEdge) {
    }

    /**
     * Plans one role switch: roster flips, the runner-alive flag
     * (null when untouched), and which deferred edge applies. Held
     * players keep their headstart hold, so no participant edge
     * runs for them. Pure for tests.
     */
    static SwitchPlan planSwitch(Role source, Role target, boolean active, boolean held) {
        Boolean runnerAlive = target == Role.SPEEDRUNNER ? Boolean.TRUE
                : source == Role.SPEEDRUNNER ? Boolean.FALSE : null;
        return new SwitchPlan(target.isParticipant() && !active,
                !target.isParticipant() && active, runnerAlive,
                target.isParticipant() && !held, !target.isParticipant());
    }

    /** Applies the fake spectator, pre-start, and headstart-hold modes for a joiner. */
    private void applyJoinGameMode(GameInstance instance, Player player, Role role) {
        Integer lobby = instance.originLobbyId();
        // Spectators join watching; the rest keep their mode (AFK cannot join).
        if (role == Role.SPECTATOR) {
            edge.fakes().enable(player);
        }
        if (!instance.begun()
                && reads.match().startInAdventureMode(lobby)
                && role.isParticipant()
                && !instance.headstart(role.opposite()).armed()) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        // A joiner is held while the opposite side's headstart runs: a
        // hunter headstart holds speedrunners, and vice versa.
        HeadstartState headstart = instance.headstart(role.opposite());
        if (role.isParticipant() && headstart.task() != null) {
            headstart.returnPoints().put(player.getUniqueId(), player.getLocation());
            edge.fakes().enable(player);
        }
    }

    /** Begins the match when exactly one is live; a no-op otherwise. */
    public void beginGame() {
        services.store().singleLiveInstance().ifPresent(this::beginGame);
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
        services.timeLimits().scheduleTimeLimit(instance, instance.matchId());
        services.prestart().cancelWaitingTasks(instance);
        // Restore participants to survival when the game begins if they were
        // set to adventure mode during the pre-start window. Held headstart
        // sides stay out: their countdown moves them to fake spectator below.
        if (reads.match().startInAdventureMode(instance.originLobbyId())) {
            for (Player player : services.store().onlineActivePlayers(instance)) {
                Role playerRole = services.playerStates().role(player);
                if (playerRole.isParticipant() && !instance.headstart(playerRole.opposite()).armed()) {
                    edge.fakes().disable(player);
                }
            }
        }
        texts.messaging().sendToInstance(instance, texts.manhunt().getStartedByDamage(), Map.of());
        texts.messaging().playInstanceNeutral(instance);
        // Dedicated begin cue, after any prestart window: begin runs once
        // per match (the begun guard above), so this plays exactly once.
        texts.messaging().playInstanceSound(instance, "game.match-started");
        beginGameListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(new JGameBeginEvent(instance.matchId()));
        // AFTER pre-start-order modifiers waited out the pre-start window;
        // their ON_START sequence runs now instead of at match start.
        services.stateCommands().runPostStartModifiers(instance.matchId());
        services.stateCommands().startIntervalModifiers(instance.matchId());
        // Armed headstarts begin counting now (the countdown only starts once
        // the speedrunner first damages a hunter).
        services.prestart().beginHeadstarts(instance);
    }

    /** Fresh per-match stat row for one participant; spectators never get one. */
    private void initMatchStats(long matchId, Player player) {
        if (!services.playerStates().role(player).isParticipant()) { return; }
        Stats playerStats = services.stats().getOrCreate(matchId, player.getUniqueId());
        playerStats.player = player.getName();
        playerStats.uuid = player.getUniqueId();
        playerStats.role = services.playerStates().role(player);
        playerStats.matchStartedAt = System.currentTimeMillis();
    }
    /** Configured starting lives for a role. -1 means unlimited. */
    private int livesFor(Integer lobby, Role role) {
        return switch (role) {
            case HUNTER -> reads.players().hunterLives(lobby);
            case SPEEDRUNNER -> reads.players().speedrunnerLives(lobby);
            default -> -1;
        };
    }

    /**
     * WAIT timing in seconds for a joined role: the larger of the
     * headstart and respawn durations, each counting only when
     * enabled and positive. Non-participant roles never wait.
     * Pure for tests.
     */
    static int joinWaitSeconds(Role role, boolean headstartEnabled, int headstartDelay,
            boolean respawnEnabled, int respawnDelay) {
        if (!role.isParticipant()) {
            return 0;
        }
        int headstart = headstartEnabled && headstartDelay > 0 ? headstartDelay : 0;
        int respawn = respawnEnabled && respawnDelay > 0 ? respawnDelay : 0;
        return Math.max(headstart, respawn);
    }

    /** True for the source roles join timing affects. Pure for tests. */
    static boolean needsJoinTiming(Role sourceRole) {
        return sourceRole == Role.NONE || sourceRole == Role.SPECTATOR || sourceRole == Role.AFK;
    }

    /**
     * Source role in the target match lobby: members keep their
     * stored role, outsiders and the lobby-less count as NONE.
     * Pure for tests.
     */
    static Role sourceRoleIn(int originLobby, Optional<Integer> playerLobby, Role storedRole) {
        if (playerLobby.isEmpty() || playerLobby.get() != originLobby) {
            return Role.NONE;
        }
        return storedRole;
    }

    /** Applies WAIT/INSTANT timing to one match entrant. */
    private void applyJoinTiming(GameInstance instance, Player player, Role source, Role target) {
        if (services.lobbies().joinTiming() != JoinTiming.WAIT || !needsJoinTiming(source)) {
            return;
        }
        boolean headstartEnabled;
        int headstartDelay;
        boolean respawnEnabled;
        int respawnDelay;
        if (target == Role.HUNTER) {
            var headstart = reads.match().getHeadstarts().getHunter();
            var respawn = reads.players().getRespawn().getHunter();
            headstartEnabled = headstart.isEnabled();
            headstartDelay = headstart.getDelaySeconds();
            respawnEnabled = respawn.isEnabled();
            respawnDelay = respawn.getDelaySeconds();
        } else {
            var headstart = reads.match().getHeadstarts().getSpeedrunner();
            var respawn = reads.players().getRespawn().getSpeedrunner();
            headstartEnabled = headstart.isEnabled();
            headstartDelay = headstart.getDelaySeconds();
            respawnEnabled = respawn.isEnabled();
            respawnDelay = respawn.getDelaySeconds();
        }
        int wait = joinWaitSeconds(target, headstartEnabled, headstartDelay,
                respawnEnabled, respawnDelay);
        if (wait > 0) {
            edge.respawn().scheduleJoinHold(player, instance, wait);
        }
    }

    /** Source role of an entrant in the target match lobby. */
    private Role entrantSourceRole(Player player, int originLobby) {
        UUID playerId = player.getUniqueId();
        Optional<Integer> lobby = services.lobbies().lobbyOf(playerId).map(Lobby::id);
        return sourceRoleIn(originLobby, lobby, services.playerStates().role(player));
    }

    /** Online lobby members watching without playing: NONE and SPECTATOR, never AFK. */
    private List<Player> lobbyNonePlayers(Lobby lobby) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> services.playerStates().role(p).isWatching() && lobby.contains(p.getUniqueId()))
                .map(p -> (Player) p).toList();
    }

    public boolean joinLeastTimeMatch(Player player) {
        Optional<GameInstance> target = leastTimeMatch(services.store().liveInstances());
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
        WorldEngineConfig config = WorldEngineConfig.fromSettings(reads.engineSettings());
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
