package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.DurationFormat;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.config.LobbyPreset;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.world.LobbyWorld;
import com.jruk8.jmanhunt.message.ListFormatter;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.WorldEngineService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import com.jruk8.jmanhunt.match.autostart.AutostartService;
import com.jruk8.jmanhunt.match.lifecycle.MatchControl;
import com.jruk8.jmanhunt.match.lifecycle.MatchFinishService;
import com.jruk8.jmanhunt.match.lifecycle.MatchMessaging;
import com.jruk8.jmanhunt.match.lifecycle.MatchStartService;
import com.jruk8.jmanhunt.match.lifecycle.MatchStore;
import com.jruk8.jmanhunt.match.lifecycle.QuickStartOutcome;
import com.jruk8.jmanhunt.match.lifecycle.TimeLimitService;
import com.jruk8.jmanhunt.match.prestart.PrestartService;

public final class GameManager implements MatchControl {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final PlayerStateStore playerStates;
    private final GameStateCommandManager stateCommands;
    private final ConfigService configService;
    private final WorldEngineService worldEngine;
    private final WinConditionEngine winConditionEngine;
    private final LobbyService lobbies;
    private final MatchStore store;
    private final MatchMessaging messaging;
    private final TimeLimitService timeLimits;
    private final PrestartService prestart;
    private final AutostartService autostart;
    private final MatchFinishService matchFinish;
    private final MatchStartService matchStart;

    public GameManager(JManhuntPlugin plugin, MessageService messages, SoundService sounds,
                       PlayerStateStore playerStates, CompassManager compass, StatsManager stats,
                       ConfigService configService, WorldEngineService worldEngine,
                       WinConditionEngine winConditionEngine, LobbyService lobbyService) {
        this.plugin = plugin;
        this.messages = messages;
        this.playerStates = playerStates;
        this.configService = configService;
        this.worldEngine = worldEngine;
        this.winConditionEngine = winConditionEngine;
        this.lobbies = lobbyService;
        this.stateCommands = new GameStateCommandManager(plugin, playerStates, configService,
                messages, sounds, worldEngine.teleportService(), this);
        this.store = new MatchStore(playerStates);
        this.messaging = new MatchMessaging(messages, sounds, configService, store, lobbies);
        this.timeLimits = new TimeLimitService(plugin, winConditionEngine, store, messaging, this);
        this.prestart = new PrestartService(plugin, configService, messages, playerStates,
                stats, stateCommands, store, messaging, this);
        this.autostart = new AutostartService(plugin, messages, playerStates, lobbies,
                worldEngine, store, messaging, this);
        this.matchFinish = new MatchFinishService(plugin, messages, playerStates, compass, stats,
                stateCommands, configService, worldEngine, store, messaging, timeLimits, prestart,
                autostart);
        this.matchStart = new MatchStartService(plugin, messages, sounds, playerStates, compass, stats,
                stateCommands, configService, worldEngine, lobbies, store, messaging, timeLimits,
                prestart, autostart, matchFinish);

        // assign events
        configService.onChange("settings.match.autostart.enabled", (oldValue, newValue) -> updateAutostartState());
        configService.onChange("world-engine.enabled", (oldValue, newValue) -> worldEngine.onReload());
        // Structure datapacks refresh exactly like the world-engine datapack:
        // toggling in-game applies or removes the files immediately instead of
        // waiting for a restart.
        configService.onChange("settings.match.game-boosts.nether-structures.enabled",
                (oldValue, newValue) -> worldEngine.onReload());
        configService.onChange("settings.match.game-boosts.overworld-structures.enabled",
                (oldValue, newValue) -> worldEngine.onReload());
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
    public boolean isActiveInInstance(long matchId, UUID playerId) {
        return store.isActiveInInstance(matchId, playerId);
    }
    /** Live speedrunners of a match (active, alive, and holding the role). */
    public int activeRunnerCount(GameInstance instance) { return store.activeRunnerCount(instance); }
    /** Live hunters of a match holding the role. */
    public int activeHunterCount(GameInstance instance) { return store.activeHunterCount(instance); }

    /** Sends a message to a match plus the console, never other matches. */
    public void sendToInstance(GameInstance instance, String key, Map<String, String> values) {
        messaging.sendToInstance(instance, key, values);
    }
    /** Plays a match sound for a match's online players. */
    public void playInstanceSound(GameInstance instance, String key) {
        messaging.playInstanceSound(instance, key);
    }
    /** Plays the neutral click for a match's online players. */
    public void playInstanceNeutral(GameInstance instance) {
        messaging.playInstanceNeutral(instance);
    }
    /** Sends a pre-rendered message to a match plus the console, never other matches. */
    public void sendToInstanceComponent(GameInstance instance, Component rendered) {
        messaging.sendToInstanceComponent(instance, rendered);
    }
    /**
     * Announces a passive&lt;-&gt;active role change to the player's lobby
     * mates, excluding the player and anyone in a live match. Same-class
     * changes stay silent. Only the active side of the change is named.
     */
    public void announceRoleChange(Player player, Role from, Role to) {
        messaging.announceRoleChange(player, from, to);
    }

    public void updateAutostartState() { autostart.updateAutostartState(); }

    /**
     * Tells queued hunters and speedrunners of ineligible lobbies how
     * many more of each role autostart needs, at most once per
     * configured interval. Runs every second from the plugin scheduler.
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
     * (negative default), the lobby is missing or already running, or its
     * queue lacks a hunter or a speedrunner.
     */
    public boolean start() {
        return matchStart.start();
    }

    /**
     * Starts a match for one lobby's queued hunters and speedrunners. Other
     * lobbies keep queueing and their matches and countdowns are untouched.
     *
     * @return false when the lobby is missing, already has a live match, or
     *         its queue lacks a hunter or a speedrunner
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
        return matchFinish.leaveDestination();
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
     * Removes a begun-match participant standing outside their cell or in
     * the lobby world, with a reason notice. Returns true when the player
     * was removed.
     */
    public boolean autoLeaveIfOutside(Player player, Location at) {
        return matchFinish.autoLeaveIfOutside(player, at);
    }

    /** Ends the match when exactly one is live; a no-op otherwise. */
    public void finish(Role winner) {
        matchFinish.finish(winner);
    }

    /** Ends one match. */
    @Override
    public void finish(GameInstance instance, Role winner) {
        matchFinish.finish(instance, winner);
    }

    /**
     * Ends one match. When {@code immediate} is true the configured
     * {@code match.end-delay} is skipped: statistics post instantly and the
     * final cleanup runs at once instead of after the delay.
     */
    public void finish(GameInstance instance, Role winner, boolean immediate) {
        matchFinish.finish(instance, winner, immediate);
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
    public void finishLater(Role winner) {
        matchFinish.finishLater(winner);
    }

    /** Ends one match on the next tick. */
    public void finishLater(GameInstance instance, Role winner) {
        matchFinish.finishLater(instance, winner);
    }


    /** Live instance by world-engine cell index. */
    public Optional<GameInstance> instanceByCell(long cellIndex) {
        return store.instances().values().stream()
                .filter(instance -> instance.cellIndex().isPresent()
                        && instance.cellIndex().getAsLong() == cellIndex)
                .findFirst();
    }


    /** Status text for the speedrunner win conditions: base plus enabled alternates. */
    public String speedrunnerWinConditions() {
        // Hunters with infinite lives can never be eliminated, so the
        // elimination line hides instead of promising an un-winnable goal.
        List<String> conditions = new ArrayList<>();
        int hunterLives = configService.getInt("settings.players.respawn.hunter.lives", -1);
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
        Optional<GameInstance> byMatch = store.instance(id);
        return byMatch.isPresent() ? byMatch : instanceByCell(id);
    }

    public GameStateCommandManager stateCommands() { return stateCommands; }
    public Set<String> settingNames() { return configService.settingNames(); }
    public boolean getSetting(String setting) { return configService.getBoolean(setting, false); }
    public Object getSettingValue(String setting) { return configService.getValue(setting); }
    /** Sets a scalar setting parsed from a raw string, with typed validation. */
    public ConfigService.SetOutcome setSetting(String setting, String rawValue) {
        return configService.setValue(setting, rawValue);
    }



    /** The other participant side; non-participants map to themselves. Pure for tests. */
    static Role opposite(Role role) {
        return role.opposite();
    }

    /**
     * Thresholds to announce now: marks strictly below the limit that the
     * remaining time has reached and that are still unannounced, highest
     * first. Pure for tests.
     */
    static List<Long> dueThresholds(long limitSecs, long remainingSecs, Set<Long> announced) {
        return TimeLimitService.dueThresholds(limitSecs, remainingSecs, announced);
    }

    /** Winner when both time limits run: earlier expiry wins, ties favor runners. Pure for tests. */
    static Role timeLimitWinner(double runnerSecs, double hunterSecs) {
        return TimeLimitService.timeLimitWinner(runnerSecs, hunterSecs);
    }

    /** Winning survive clock across runners, hunters, and cancel; null when none runs. Pure for tests. */
    static TimeLimitService.SurviveOutcome resolveSurvive(Double runnerSecs, Double hunterSecs, Double cancelSecs) {
        return TimeLimitService.resolveSurvive(runnerSecs, hunterSecs, cancelSecs);
    }

    /**
     * Roles still missing queued players against the autostart minimums,
     * mapped to how many more each needs. Empty means eligible to start.
     * Minimums clamp to a hard minimum of 1 per role. Pure for tests.
     */
    static Map<Role, Integer> autostartShortfall(int hunters, int speedrunners,
            int minHunters, int minSpeedrunners) {
        return AutostartService.autostartShortfall(hunters, speedrunners, minHunters, minSpeedrunners);
    }

    /**
     * True when the team set gained members since the last sighting. A
     * null previous set (first sighting, e.g. after a restart) counts
     * as no growth, so the nag still fires immediately. Pure for tests.
     */
    static boolean teamGrew(Set<UUID> previous, Set<UUID> current) {
        return AutostartService.teamGrew(previous, current);
    }

    /**
     * True when the shortfall nag may fire: never broadcast, or a full
     * interval elapsed since the last one. Pure for tests.
     */
    static boolean nagDue(long now, Long lastBroadcast, int intervalSeconds) {
        return AutostartService.nagDue(now, lastBroadcast, intervalSeconds);
    }

    /**
     * True for the roles the autostart shortfall nag goes to: assigned
     * hunters and speedrunners only. Pure for tests.
     */
    static boolean receivesShortfall(Role role) {
        return AutostartService.receivesShortfall(role);
    }

    /**
     * One "two more Hunters" shortfall fragment. The role color is
     * closed back to the message's yellow so it cannot bleed into the
     * separator or the sentence tail. Pure for tests.
     */
    static String shortfallPart(String countWord, String coloredRoleName, int need) {
        return AutostartService.shortfallPart(countWord, coloredRoleName, need);
    }

    /** True when a pre-start match can still progress: both sides fielded. Pure for tests. */
    static boolean canProgress(int hunterCount, int runnerCount) {
        return MatchFinishService.canProgress(hunterCount, runnerCount);
    }

    /** Winner when a bucket is empty; empty when both sides stand. Pure for tests. */
    static Optional<Role> bucketWinner(int hunterCount, int runnerCount) {
        return MatchFinishService.bucketWinner(hunterCount, runnerCount);
    }

    /** Newest live match (running the least time); empty when none runs. Pure for tests. */
    static Optional<GameInstance> leastTimeMatch(java.util.Collection<GameInstance> instances) {
        return MatchStartService.leastTimeMatch(instances);
    }

    /**
     * Computes how many players of a convertible pool become speedrunners for
     * a quick-start percentage. Fractional results are rounded to the nearest
     * whole player, with always at least one speedrunner.
     */
    static int quickStartSpeedrunnerCount(int poolSize, int percent) {
        return MatchStartService.quickStartSpeedrunnerCount(poolSize, percent);
    }


    /** Debug label for a match cell, "none" when the engine is off. */
    public static String cellString(GameInstance instance) {
        return instance.cellIndex().isPresent()
                ? String.valueOf(instance.cellIndex().getAsLong())
                : "none";
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
        return ensureLobbyWorld(Optional.empty());
    }

    /** Same, with a one-shot preset override for fresh generation. */
    public Optional<LobbyWorld> ensureLobbyWorld(Optional<LobbyPreset> presetOverride) {
        return worldEngine.ensureLobbyWorld(presetOverride);
    }

    /** Overwrites the world-engine cell index. Returns false when unavailable. */
    public boolean cellIndex(long value) { return worldEngine.cellIndex(value); }

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
