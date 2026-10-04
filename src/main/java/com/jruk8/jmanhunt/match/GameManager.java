package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.MatchConfig;
import com.jruk8.jmanhunt.config.PlayerSettings;
import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.core.JManhuntPlaceholders;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpawnCampService;
import com.jruk8.jmanhunt.lobby.config.LobbyPreset;
import com.jruk8.jmanhunt.lobby.config.MatchSettingsFacade;
import com.jruk8.jmanhunt.lobby.config.PlayersSettingsFacade;
import com.jruk8.jmanhunt.lobby.config.WinConditionsSettingsFacade;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.MidMatchPolicy;
import com.jruk8.jmanhunt.lobby.world.LobbyWorld;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.message.WinconMessages;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.WorldEngineService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.jruk8.jmanhunt.match.autostart.AutostartService;
import com.jruk8.jmanhunt.match.lifecycle.MatchControl;
import com.jruk8.jmanhunt.match.lifecycle.MatchFinishService;
import com.jruk8.jmanhunt.match.lifecycle.MatchMessaging;
import com.jruk8.jmanhunt.match.lifecycle.MatchStartService;
import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.command.StatValues;
import com.jruk8.jmanhunt.command.TagCooldownStore;
import com.jruk8.jmanhunt.match.lifecycle.MatchStore;
import com.jruk8.jmanhunt.match.lifecycle.QuickStartOutcome;
import com.jruk8.jmanhunt.match.lifecycle.QuickStartService;
import com.jruk8.jmanhunt.match.lifecycle.TimeLimitService;
import com.jruk8.jmanhunt.match.listeners.PlayerCombatListener;
import com.jruk8.jmanhunt.match.listeners.PlayerConnectionListener;
import com.jruk8.jmanhunt.match.listeners.PlayerRespawnListener;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import com.jruk8.jmanhunt.match.prestart.PrestartService;
import com.jruk8.jmanhunt.world.border.PseudoborderParticleService;
import org.bukkit.scheduler.BukkitTask;

public final class GameManager implements MatchControl {
    /** States, compass, and stats. */
    public record GameServices(PlayerStateStore playerStates, CompassManager compass,
            StatsManager stats) {
    }

    /** Modifier reads, engine, win engine, and lobbies. */
    public record GameReads(ConfigService configService, WorldEngineService worldEngine,
            WinConditionEngine winConditionEngine, LobbyService lobbies) {
    }

    /** Message bus, texts, and sounds. */
    public record GameTexts(MessageService messages, ManhuntMessages manhunt,
            GameMessages gameTexts, WinconMessages wincon, SoundService sounds) {
    }

    /** Plugin-owned edges, narrowed. */
    public record GameEdge(EngineStateRepository engineStates, FakeSpectatorService fakes,
            JManhuntLogger log, OverrideService overrides, JManhuntPlaceholders placeholders,
            PlayerRespawnListener respawn, RoleTeamService roleTeams,
            SpawnCampService spawnCamp, LobbyConfig lobbyConfig, TaskScheduler tasks,
            Supplier<JManhuntConfig> configRoot) {
    }

    private final GameServices services;
    private final GameReads reads;
    private final GameTexts texts;
    private final GameEdge edge;
    private final GameStateCommandManager stateCommands;
    private final MatchStore store;
    private final MatchMessaging messaging;
    private final TimeLimitService timeLimits;
    private final PrestartService prestart;
    private final AutostartService autostart;
    private final MatchFinishService matchFinish;
    private final MatchStartService matchStart;
    private final PseudoborderParticleService pseudoborderParticles;
    private final FlagStore flagStore;
    private final TagCooldownStore cooldownStore;
    private final WinConditionTextService winConditions;
    private final MatchSettingsFacade matchSettings;
    private final PlayersSettingsFacade playersSettings;
    private final WinConditionsSettingsFacade winConditionsSettings;

    public GameManager(GameServices services, GameReads reads, GameTexts texts,
            GameEdge edge) {
        this.services = services;
        this.reads = reads;
        this.texts = texts;
        this.edge = edge;
        var root = edge.configRoot().get();
        var match = root.getSettings().getMatch();
        var players = root.getSettings().getPlayers();
        var engine = root.getWorldEngine();
        this.matchSettings = new MatchSettingsFacade(edge.overrides(), match);
        this.playersSettings = new PlayersSettingsFacade(edge.overrides(), players);
        this.winConditionsSettings = new WinConditionsSettingsFacade(edge.overrides(), match.getWinConditions());
        var interop = root.getAdvanced().getMisc().getInterop();
        this.stateCommands = new GameStateCommandManager(
                new GameStateCommandManager.CommandReads(services.playerStates(), reads.configService(), interop,
                        playersSettings),
                new GameStateCommandManager.CommandEdge(edge.engineStates(),
                        edge.fakes(), edge.log(), edge.overrides(),
                        edge.placeholders(), edge.tasks()),
                texts.messages(), texts.sounds(), this);
        this.store = new MatchStore(services.playerStates());
        this.flagStore = new FlagStore();
        this.cooldownStore = new TagCooldownStore();
        this.messaging = new MatchMessaging(
                new MatchMessaging.MessagingTexts(texts.messages(), texts.manhunt(),
                        texts.sounds()),
                root.getSettings().getServer(), store, reads.lobbies());
        this.timeLimits = new TimeLimitService(
                new TimeLimitService.TimeEdge(edge.log(), edge.tasks()), reads.winConditionEngine(),
                store, new TimeLimitService.TimeTexts(messaging, texts.gameTexts()), this);
        this.prestart = newPrestartService(match.getHeadstarts());
        this.autostart = newAutostartService();
        this.matchFinish = newMatchFinish(engine);
        this.matchStart = newMatchStart(engine);
        this.pseudoborderParticles = new PseudoborderParticleService(edge.tasks(),
                edge.fakes(), engine, store, reads.worldEngine());
        this.winConditions = new WinConditionTextService(texts.messages(), texts.wincon(),
                players.getRespawn(), reads.winConditionEngine());
        subscribeSettingChanges();
    }

    private PrestartService newPrestartService(
            com.jruk8.jmanhunt.config.MatchSettings.Headstarts headstarts) {
        return new PrestartService(
                new PrestartService.PrestartConfig(headstarts, matchSettings,
                        playersSettings, edge.overrides()),
                new PrestartService.PrestartServices(services.playerStates(), services.stats(),
                        stateCommands, store, this, edge.fakes(), edge.tasks()),
                texts.messages(), messaging, texts.manhunt());
    }

    private AutostartService newAutostartService() {
        return new AutostartService(
                new AutostartService.AutoConfig(matchSettings, edge.tasks()),
                new AutostartService.AutoMatch(services.playerStates(), reads.lobbies(),
                        reads.worldEngine(), store, this),
                texts.messages(), messaging, texts.manhunt());
    }

    private MatchFinishService newMatchFinish(
            com.jruk8.jmanhunt.config.WorldEngineConfig engine) {
        return new MatchFinishService(
                new MatchFinishService.FinishReads(playersSettings, matchSettings, engine,
                        edge.overrides(), edge.log()),
                new MatchFinishService.FinishMatch(services.playerStates(), services.compass(),
                        services.stats(), stateCommands, reads.worldEngine(), store, timeLimits,
                        prestart, autostart, flagStore, cooldownStore),
                new MatchFinishService.FinishEdge(edge.fakes(), edge.roleTeams(),
                        edge.spawnCamp(), edge.tasks(), edge.configRoot()),
                new MatchFinishService.FinishTexts(texts.messages(), texts.gameTexts(),
                        messaging));
    }

    private MatchStartService newMatchStart(
            com.jruk8.jmanhunt.config.WorldEngineConfig engine) {
        return new MatchStartService(
                new MatchStartService.StartReads(matchSettings, playersSettings,
                        reads.configService(), engine, edge.log()),
                new MatchStartService.StartMatch(services.playerStates(), services.compass(),
                        services.stats(), stateCommands, reads.worldEngine(), reads.lobbies(),
                        store, timeLimits, prestart, autostart),
                new MatchStartService.StartEdge(edge.fakes(), edge.roleTeams(),
                        edge.respawn(), texts.sounds()),
                new MatchStartService.StartTexts(texts.messages(), texts.gameTexts(),
                        texts.manhunt(), messaging));
    }

    /** True while any match runs, including end-delay phases. */
    public boolean isActive() { return store.instances().values().stream().anyMatch(GameInstance::active); }
    /** True once any live match has begun. */
    public boolean isGameBegun() { return store.instances().values().stream().anyMatch(GameInstance::begun); }
    /** True while any match is being finished. */
    public boolean isEnding() { return store.instances().values().stream().anyMatch(GameInstance::ending); }
    public long matchId() { return store.matchId(); }
    /** Live instances keyed by match id; more than one only with the world engine on. */
    public Map<Long, GameInstance> instances() { return store.instances(); }
    /** Looks up a live instance by match id. */
    public Optional<GameInstance> instance(long matchId) { return store.instance(matchId); }
    /** Origin lobby of one match for override resolution, or null when gone. */
    public Integer lobbyOf(long matchId) { return store.lobbyOf(matchId); }
    /** Origin lobby of a player's match for override resolution, or null outside matches. */
    public Integer lobbyOfPlayer(UUID playerId) { return store.lobbyOfPlayer(playerId); }
    /** Shared flag store behind command tags; cleared per match on teardown. */
    public FlagStore flagStore() { return flagStore; }
    /** Shared cooldown stamps behind command tags; cleared per match on teardown. */
    public TagCooldownStore cooldownStore() { return cooldownStore; }
    /** Stat values bound to one match for one tag run. */
    public StatValues matchStatValues(long matchId) {
        return new MatchStatValues(services.stats(), store, matchId);
    }

    /** Live instances oldest first. */
    public List<GameInstance> liveInstances() { return store.liveInstances(); }
    /** The live instance a player actively participates in, if any. */
    public Optional<GameInstance> instanceOf(UUID playerId) { return store.instanceOf(playerId); }
    /** Live instance started from a lobby, if that lobby has one running. */
    public Optional<GameInstance> instanceForLobby(int lobbyId) { return store.instanceForLobby(lobbyId); }
    /** Live instances started from one lobby, sublobbies included. */
    public List<GameInstance> instancesForLobby(int lobbyId) { return store.instancesForLobby(lobbyId); }
    /** True when the player actively participates in any live match. */
    public boolean isInLiveInstance(UUID playerId) { return store.isInLiveInstance(playerId); }
    /** Online active participants of a match. */
    public List<Player> onlineParticipants(long matchId) { return store.onlineParticipants(matchId); }
    /** Online active participants of a match. */
    public List<Player> onlineActivePlayers(GameInstance instance) { return store.onlineActivePlayers(instance); }
    /** Online players ever assigned to a match, including the eliminated. */
    public List<Player> onlineAssignedPlayers(GameInstance instance) {
        return store.onlineAssignedPlayers(instance);
    }
    /** Online match members for status output (lobby-returned leavers excluded). */
    public List<Player> onlineMatchRoster(GameInstance instance) {
        return store.onlineMatchRoster(instance);
    }
    public boolean isActiveInInstance(long matchId, UUID playerId) {
        return store.isActiveInInstance(matchId, playerId);
    }
    /** Live speedrunners of a match (active, alive, and holding the role). */
    public int activeRunnerCount(GameInstance instance) { return store.activeRunnerCount(instance); }
    /** Live hunters of a match holding the role. */
    public int activeHunterCount(GameInstance instance) { return store.activeHunterCount(instance); }

    /** Match-scoped messaging; listeners announce through this. */
    public MatchMessaging messaging() {
        return messaging;
    }

    public MatchSettingsFacade matchSettings() {
        return matchSettings;
    }

    public PlayersSettingsFacade playersSettings() {
        return playersSettings;
    }

    public WinConditionsSettingsFacade winConditionsSettings() {
        return winConditionsSettings;
    }

    /** Live-reacts to the toggles this manager owns. */
    private void subscribeSettingChanges() {
        // assign events
        reads.configService().onChange("settings.match.autostart.enabled",
                (oldValue, newValue) -> updateAutostartState());
        reads.configService().onChange("world-engine.enabled", (oldValue, newValue) -> reads.worldEngine().onReload());
        // Structure datapacks refresh exactly like the world-engine datapack:
        // toggling in-game applies or removes the files immediately instead of
        // waiting for a restart.
        reads.configService().onChange("settings.match.game-boosts.nether-structures.enabled",
                (oldValue, newValue) -> reads.worldEngine().onReload());
        reads.configService().onChange("settings.match.game-boosts.overworld-structures.enabled",
                (oldValue, newValue) -> reads.worldEngine().onReload());
        reads.configService().onChange(LobbyService.COLLISIONS_PATH,
                (oldValue, newValue) -> reads.lobbies().reapplyCollisions());
    }

    /** Connection listener with typed player and disconnect sections. */
    public PlayerConnectionListener connectionListener(PlayerSettings players,
            MatchConfig.DisconnectHandling handling, PlayerRespawnListener respawn,
            SpeedrunnerDisconnectTracker disconnects,
            Map<UUID, BukkitTask> disconnectTasks, CompassManager compass,
            GameMessages gameTexts) {
        return new PlayerConnectionListener(
                new PlayerConnectionListener.ConnectReads(services.playerStates(), edge.fakes(),
                        texts.messages(), gameTexts),
                new PlayerConnectionListener.ConnectMatch(this, reads.lobbies(), compass, disconnects,
                        disconnectTasks),
                new PlayerConnectionListener.ConnectWorld(reads.worldEngine().teleportService(),
                        reads.worldEngine()),
                new PlayerConnectionListener.ConnectConfig(players, handling),
                new PlayerConnectionListener.ConnectEdge(edge.roleTeams(), edge.tasks()));
    }

    /** Combat listener with the typed player section. */
    public PlayerCombatListener combatListener(PlayerSettings players,
            PlayerRespawnListener respawn, SpeedrunnerDisconnectTracker disconnects,
            Map<UUID, BukkitTask> disconnectTasks, CompassManager compass,
            GameMessages gameTexts) {
        return new PlayerCombatListener(
                new PlayerCombatListener.CombatReads(services.playerStates(), edge.fakes(),
                        players, gameTexts),
                new PlayerCombatListener.CombatMatch(this, services.stats(), reads.winConditionEngine(),
                        disconnects, disconnectTasks),
                new PlayerCombatListener.CombatWorld(compass, reads.lobbies(), reads.worldEngine(), respawn),
                new PlayerCombatListener.CombatEdge(edge.spawnCamp(), edge.roleTeams(),
                        edge.log(), edge.lobbyConfig()),
                edge.tasks());
    }

    public void updateAutostartState() { autostart.updateAutostartState(); }

    /**
     * Tells queued hunters and speedrunners of ineligible lobbies how
     * many more of each role autostart needs, at most once per
     * configured interval. Runs every second from the tasks scheduler.
     */
    public void broadcastAutostartShortfalls() { autostart.broadcastAutostartShortfalls(); }

    /** Registers a listener invoked whenever a match starts. */
    public void addGameStartListener(Consumer<GameInstance> listener) {
        matchStart.addGameStartListener(listener);
    }

    /** Registers a listener invoked when a game actually begins (after pre-start window). */
    public void addBeginGameListener(Consumer<GameInstance> listener) {
        matchStart.addBeginGameListener(listener);
    }

    /** Registers a listener invoked when a match ends. */
    public void addGameEndListener(Consumer<GameInstance> listener) {
        matchFinish.addGameEndListener(listener);
    }

    /**
     * Starts a match from the default lobby. False when starting is disabled
     * (negative default), the lobby is missing or blocked by a live match
     * under the mid-match policy, or its queue lacks a hunter or a
     * speedrunner.
     */
    public boolean start() {
        return matchStart.start();
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
    @Override
    public boolean start(int lobbyId) {
        return matchStart.start(lobbyId);
    }

    /**
     * Same, but the surround origin gathers participants when the world
     * engine is off: a player executor's location, or null for a random
     * wilderness point (console and autostart).
     */
    public boolean start(int lobbyId, Location surroundOrigin) {
        return matchStart.start(lobbyId, surroundOrigin);
    }

    /** Lobby id used when a start has no other context; negative disables it. */
    public int defaultStartLobbyId() {
        return matchStart.defaultStartLobbyId();
    }

    /**
     * Adds players to a running match with the given role, moving them into
     * the match's lobby. Players already in any live match are skipped, as is
     * everyone when the match is ending. Returns the number added.
     */
    public int joinPlayers(GameInstance instance, List<Player> players, Role role) {
        return matchStart.joinPlayers(instance, players, role);
    }

    /** Begins the match when exactly one is live; a no-op otherwise. */
    public void beginGame() {
        matchStart.beginGame();
    }

    /** Begins one match: real gameplay starts for its participants. */
    @Override
    public void beginGame(GameInstance instance) {
        matchStart.beginGame(instance);
    }

    public boolean joinLeastTimeMatch(Player player) {
        return matchStart.joinLeastTimeMatch(player);
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
        return matchStart.quickStart(speedrunnerPercent, lobbyId);
    }

    /**
     * Same, but the surround origin gathers participants when the world
     * engine is off: a player executor's location, or null for a random
     * wilderness point (console).
     */
    public QuickStartOutcome quickStart(int speedrunnerPercent, int lobbyId, Location surroundOrigin) {
        return matchStart.quickStart(speedrunnerPercent, lobbyId, surroundOrigin);
    }

    /**
     * Ends a begun match whose hunter or speedrunner bucket hit zero through
     * a role change. Deaths end matches on their own paths; this covers
     * setplayer, joins, leaves, and removals.
     */
    public void finishIfBucketEmpty(GameInstance instance) {
        matchFinish.finishIfBucketEmpty(instance);
    }

    /**
     * Cancels an unbegun match that lost a whole side through a leave.
     * Pre-start matches need at least one hunter and one speedrunner to
     * progress; the autostart minimums do not apply here.
     */
    public void cancelIfPreStartUnviable(GameInstance instance) {
        matchFinish.cancelIfPreStartUnviable(instance);
    }

    /** Configured leave destination, SPECTATOR by default. */
    public LeaveDestination leaveDestination() {
        return matchFinish.leaveDestination(null);
    }

    /**
     * Removes players from a match: deactivates them, clears their match
     * state, and moves them to the configured destination. Returns the
     * number removed. A last leaver ends the match for the other side.
     */
    public int leaveMatch(GameInstance instance, List<Player> leavers, boolean dropGear) {
        return matchFinish.leaveMatch(instance, leavers, dropGear);
    }

    /**
     * Mid-match setplayer to AFK or NONE: a full leave plus a lobby
     * return under the given role. The role must be AFK or NONE.
     */
    public int leaveMatchToLobby(GameInstance instance, Player player, Role role) {
        return matchFinish.leaveMatchToLobby(instance, player, role);
    }

    /**
     * Removes a begun-match participant standing outside their cell or in
     * the lobby world, with a reason notice. Returns true when the player
     * was removed.
     */
    public boolean autoLeaveIfOutside(Player player, Location at) {
        return matchFinish.autoLeaveIfOutside(player, at);
    }

    /** Ends the match when exactly one is live; a no-op otherwise. */
    public void finish(Role winner, String reason) {
        matchFinish.finish(winner, reason);
    }

    /**
     * Eliminates a runner/hunter by name with a loss announcement.
     * False with no effect when the match is not live or the player
     * is unknown, offline, or not a runner/hunter.
     */
    public boolean losePlayer(long matchId, String playerName, String reason) {
        return matchFinish.losePlayer(matchId, playerName, reason);
    }

    /** Ends one match. */
    @Override
    public void finish(GameInstance instance, Role winner, String reason) {
        matchFinish.finish(instance, winner, reason);
    }

    /**
     * Ends one match. When {@code immediate} is true the configured
     * {@code advanced.advanced-match-controls.end-delay} is skipped: statistics post instantly and the
     * final cleanup runs at once instead of after the delay.
     */
    public void finish(GameInstance instance, Role winner, boolean immediate, String reason) {
        matchFinish.finish(instance, winner, immediate, reason);
    }

    /**
     * Shared immediate teardown tail: end commands, lobby teleport, role
     * reset, and deactivation. Callers run their own announcements, stat
     * handling, and cleanup commands first.
     */
    @Override
    public void teardownNow(GameInstance instance) {
        matchFinish.teardownNow(instance);
    }

    /** Cancels the match when exactly one is live; a no-op otherwise. */
    public void cancel() {
        matchFinish.cancel();
    }

    /** Cancels one match with no winner. */
    @Override
    public void cancel(GameInstance instance) {
        matchFinish.cancel(instance);
    }

    /** Ends every live match at once for server shutdown: no stats, no scheduling. */
    public void shutdownMatches() {
        matchFinish.shutdownAll();
    }

    /**
     * Cancels one match with no winner. Career statistics are not
     * saved, but the in-memory match statistics still back the end screen.
     */
    public void cancel(GameInstance instance, boolean immediate) {
        matchFinish.cancel(instance, immediate);
    }

    /** Ends the match next tick when exactly one is live; a no-op otherwise. */
    public void finishLater(Role winner, String reason) {
        matchFinish.finishLater(winner, reason);
    }

    /** Ends one match on the next tick. */
    public void finishLater(GameInstance instance, Role winner, String reason) {
        matchFinish.finishLater(instance, winner, reason);
    }


    /** Status text for the speedrunner win conditions: base plus enabled alternates. */
    public String speedrunnerWinConditions() {
        return winConditions.speedrunnerWinConditions();
    }

    /** Status text for the hunter win conditions: base plus enabled alternates. */
    public String hunterWinConditions() {
        return winConditions.hunterWinConditions();
    }

    /** Live instance by world-engine cell index. */
    public Optional<GameInstance> instanceByCell(long cellIndex) {
        return store.instanceByCell(cellIndex);
    }

    /**
     * Resolves an instance id typed in a command: a match id first, then a
     * world-engine cell index as an alias. Empty when unparsable or unknown.
     */
    public Optional<GameInstance> resolveInstance(String raw) {
        return store.resolveInstance(raw);
    }

    public GameStateCommandManager stateCommands() { return stateCommands; }

    /** Runs a deferred match-end wipe for a rejoiner when one is pending. */
    public boolean applyPendingEndWipe(Player player) {
        return stateCommands.applyPendingEndWipe(player);
    }

    /** Loads surviving crash cleanup rows into the pending wipe set after a crash. */
    public void loadCrashCleanup() {
        stateCommands.loadCrashCleanup();
    }

    /** Runs the post-crash wipe for a rejoiner when one is pending. */
    public boolean applyPendingCrashWipe(Player player) {
        return stateCommands.applyPendingCrashWipe(player);
    }

    /** Reconciles live matches with toggled modifiers. */
    public void syncModifierToggles(Collection<String> names) {
        stateCommands.syncModifierToggles(names);
    }
    public Set<String> settingNames() { return reads.configService().settingNames(); }
    public boolean getSetting(String setting) { return reads.configService().getBoolean(setting, false); }
    public Object getSettingValue(String setting) { return reads.configService().getValue(setting); }
    /** Sets a scalar setting parsed from a raw string, with typed validation. */
    public ConfigService.SetOutcome setSetting(String setting, String rawValue) {
        return reads.configService().setValue(setting, rawValue);
    }



    /**
     * Mid-match join target for one role change: spectators under
     * SUBLOBBY_WITH_SPECTATORS join the oldest running sublobby of
     * their lobby, falling back to the lobby match when no sublobby
     * runs; every other case keeps the lobby match.
     */
    public GameInstance midMatchJoinTarget(MidMatchPolicy policy, int lobbyId, GameInstance live,
            Role role) {
        if (policy == MidMatchPolicy.SUBLOBBY_WITH_SPECTATORS && role == Role.SPECTATOR) {
            return MatchStore.oldestSubLobby(store.instancesForLobby(lobbyId)).orElse(live);
        }
        return live;
    }

    /**
     * Computes how many players of a convertible pool become speedrunners for
     * a quick-start percentage. Delegates to the quick-start service.
     */
    static int quickStartSpeedrunnerCount(int poolSize, int percent) {
        return QuickStartService.quickStartSpeedrunnerCount(poolSize, percent);
    }

    /** Debug label for a match cell, "none" when the engine is off. */
    public static String cellString(GameInstance instance) {
        return instance.cellIndex().isPresent()
                ? String.valueOf(instance.cellIndex().getAsLong())
                : "none";
    }


    /** Current world-engine cell index, or empty when the store is unavailable. */
    public OptionalLong cellIndex() { return reads.worldEngine().cellIndex(); }

    /** Current world-engine cell index cap for the live cell size. */
    public long cellIndexCap() { return reads.worldEngine().cellIndexCap(); }

    /** Buffered ready-cell indexes, oldest first. */
    public List<Long> bufferedCellIndexes() { return reads.worldEngine().bufferedCellIndexes(); }

    /** Configured lobby world name. */
    public String lobbyWorldName() { return reads.worldEngine().lobbyWorldName(); }

    /** True when newcomers have a lobby to wait in. */
    public boolean hasLobbyLocation(int lobbyId) { return reads.worldEngine().hasLobbyLocation(lobbyId); }

    /** Teleports players to a lobby spawn, announcing the travel. */
    public boolean teleportToLobby(List<Player> targets, int lobbyId) {
        return reads.worldEngine().teleportToLobby(targets, lobbyId);
    }

    public Optional<Location> lowestLobbyTeleport() {
        return reads.worldEngine().lowestLobbyTeleport();
    }

    /** Pins respawn locations to a lobby spawn without announcing. */
    public boolean setSpawnToLobbyQuiet(List<Player> targets, int lobbyId) {
        return reads.worldEngine().setSpawnToLobbyQuiet(targets, lobbyId);
    }

    /** Center surface point of a match cell, or empty without the engine. */
    public Optional<Location> cellCenter(long cellIndex) { return reads.worldEngine().cellCenter(cellIndex); }


    /** True when the lobby world is loaded or has a folder waiting. */
    public boolean lobbyWorldExists() { return reads.worldEngine().lobbyWorldExists(); }

    /** True when lobby-world-name collides with the game world name. */
    public boolean lobbyWorldNameClashes() { return reads.worldEngine().lobbyWorldNameClashes(); }

    /** Warns on a lobby/game world name clash. True when clean. */
    public boolean validateLobbyWorldName() { return reads.worldEngine().validateLobbyWorldName(); }

    /**
     * Arms or confirms lobby-world generation for one sender key. True only
     * on a matching second call within the timeout.
     */
    public boolean confirmLobbyGeneration(String senderKey) {
        return reads.worldEngine().confirmLobbyGeneration(senderKey);
    }

    /** Loads or generates the lobby world. Empty when creation fails. */
    public Optional<LobbyWorld> ensureLobbyWorld() {
        return ensureLobbyWorld(Optional.empty());
    }

    /** Same, with a one-shot preset override for fresh generation. */
    public Optional<LobbyWorld> ensureLobbyWorld(Optional<LobbyPreset> presetOverride) {
        return reads.worldEngine().ensureLobbyWorld(presetOverride);
    }

    /** Points lobby 0 at the spawn when none is configured. True when written. */
    public boolean ensureLobbyZero(Location spawn) {
        return reads.worldEngine().ensureLobbyZero(spawn);
    }

    /** Overwrites the world-engine cell index. Returns false when unavailable. */
    public boolean cellIndex(long value) { return reads.worldEngine().cellIndex(value); }

    /** Maps an internal role to the API player role, defaulting to the winner role of NONE. */
    public static com.jruk8.jmanhunt.api.PlayerRole roleToPlayerRole(Role role) {
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
}
