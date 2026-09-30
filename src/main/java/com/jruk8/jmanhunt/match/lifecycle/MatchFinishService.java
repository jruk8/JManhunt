package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.core.DebugLevel;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.api.events.JMatchCancelEvent;
import com.jruk8.jmanhunt.api.events.JMatchEndEvent;
import com.jruk8.jmanhunt.api.events.JPlayerJoinMatchEvent;
import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.command.TagCooldownStore;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.match.LeaveDestination;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.teleport.MatchTeleportService;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.world.WorldEngineService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.match.autostart.AutostartService;
import com.jruk8.jmanhunt.match.prestart.PrestartService;

/**
 * Ends matches and shrinks them: finishes, cancels, teardown, mid-match
 * leaves, and the concurrent-match border guard. GameManager keeps thin
 * delegates.
 */
public final class MatchFinishService {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final PlayerStateStore playerStates;
    private final StatsManager stats;
    private final GameStateCommandManager stateCommands;
    private final ConfigService configService;
    private final WorldEngineService worldEngine;
    private final MatchStore store;
    private final MatchMessaging messaging;
    private final TimeLimitService timeLimits;
    private final PrestartService prestart;
    private final AutostartService autostart;
    private final FlagStore flagStore;
    private final TagCooldownStore cooldowns;
    private final MatchEliminationService elimination;
    private final MatchLeaveService leave;
    private final List<Consumer<GameInstance>> gameEndListeners = new ArrayList<>();

    public MatchFinishService(JManhuntPlugin plugin, MessageService messages, PlayerStateStore playerStates,
            CompassManager compass, StatsManager stats, GameStateCommandManager stateCommands,
            ConfigService configService, WorldEngineService worldEngine, MatchStore store,
            MatchMessaging messaging, TimeLimitService timeLimits, PrestartService prestart,
            AutostartService autostart, FlagStore flagStore, TagCooldownStore cooldowns) {
        this.plugin = plugin;
        this.messages = messages;
        this.playerStates = playerStates;
        this.stats = stats;
        this.stateCommands = stateCommands;
        this.configService = configService;
        this.worldEngine = worldEngine;
        this.store = store;
        this.messaging = messaging;
        this.timeLimits = timeLimits;
        this.prestart = prestart;
        this.autostart = autostart;
        this.flagStore = flagStore;
        this.cooldowns = cooldowns;
        this.elimination = new MatchEliminationService(plugin, playerStates, compass, store,
                messaging, flagStore, this::finishIfBucketEmpty);
        this.leave = new MatchLeaveService(plugin, messages, playerStates, compass, stateCommands,
                configService, worldEngine, store, messaging, flagStore, instance -> {
                    compass.reconcileTeammateModes(instance);
                    finishIfBucketEmpty(instance);
                    cancelIfPreStartUnviable(instance);
                });
        new MatchBorderEnforcer(plugin, configService, store, worldEngine, playerStates);
    }

    public void addGameEndListener(Consumer<GameInstance> listener) {
        gameEndListeners.add(listener);
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
        bucketWinner(store.activeHunterCount(instance), store.activeRunnerCount(instance))
                .ifPresent(winner -> finishLater(instance, winner, winner == Role.HUNTER
                        ? "All speedrunners removed"
                        : "All hunters removed"));
    }

    /** Eliminates one runner/hunter by name; see {@link MatchEliminationService}. */
    public boolean losePlayer(long matchId, String playerName, String reason) {
        return elimination.losePlayer(matchId, playerName, reason);
    }

    /**
     * Cancels an unbegun match that lost a whole side through a leave.
     * Pre-start matches need at least one hunter and one speedrunner to
     * progress; the autostart minimums do not apply here.
     */
    public void cancelIfPreStartUnviable(GameInstance instance) {
        if (instance.begun() || instance.ending()) {
            return;
        }
        if (!canProgress(store.activeHunterCount(instance), store.activeRunnerCount(instance))) {
            cancel(instance);
        }
    }

    /** True when a pre-start match can still progress: both sides fielded. Pure for tests. */
    public static boolean canProgress(int hunterCount, int runnerCount) {
        return hunterCount > 0 && runnerCount > 0;
    }

    /**
     * Why a match with these buckets cannot progress, for cancellation
     * messages. Only meaningful when canProgress is false. Pure for tests.
     */
    public static String invalidReason(int hunterCount, int runnerCount) {
        if (hunterCount <= 0 && runnerCount <= 0) {
            return "neither side is fielded";
        }
        if (hunterCount <= 0) {
            return "no hunters remain";
        }
        return "no speedrunners remain";
    }

    /** Winner when a bucket is empty; empty when both sides stand. Pure for tests. */
    public static Optional<Role> bucketWinner(int hunterCount, int runnerCount) {
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

    /** Configured leave destination, SPECTATOR by default. See {@link MatchLeaveService}. */
    public LeaveDestination leaveDestination(Integer lobby) {
        return leave.leaveDestination(lobby);
    }

    /**
     * Removes players from a match: deactivates them, clears their match
     * state, and moves them to the configured destination. Returns the
     * number removed. A last leaver ends the match for the other side.
     * See {@link MatchLeaveService}.
     */
    public int leaveMatch(GameInstance instance, List<Player> leavers, boolean dropGear) {
        return leave.leaveMatch(instance, leavers, dropGear);
    }

    /**
     * Mid-match setplayer to AFK or NONE: a full leave plus a lobby
     * return under the given role. The role must be AFK or NONE.
     * Returns the number removed. See {@link MatchLeaveService}.
     */
    public int leaveMatchToLobby(GameInstance instance, Player player, Role role) {
        return leave.leaveMatchToLobby(instance, player, role);
    }

    /**
     * Removes a begun-match participant standing outside their cell or in
     * the lobby world, with a reason notice. Returns true when the player
     * was removed. See {@link MatchLeaveService}.
     */
    public boolean autoLeaveIfOutside(Player player, Location at) {
        return leave.autoLeaveIfOutside(player, at);
    }

    /** Ends the match when exactly one is live; a no-op otherwise. */
    public void finish(Role winner, String reason) {
        store.singleLiveInstance().ifPresent(instance -> finish(instance, winner, reason));
    }

    /** Ends one match. */
    public void finish(GameInstance instance, Role winner, String reason) {
        finish(instance, winner, false, reason);
    }

    /**
     * Ends one match. When {@code immediate} is true the configured
     * {@code match.end-delay} is skipped: statistics post instantly and the
     * final cleanup runs at once instead of after the delay. An immediate end
     * during an in-progress end delay finishes the match at once instead of
     * being blocked.
     */
    public void finish(GameInstance instance, Role winner, boolean immediate, String reason) {
        if (instance.ending()) {
            if (immediate) {
                showEndStatsOnce(instance);
                finishEndPhase(instance);
            }
            return;
        }
        instance.refreshElapsedCache(System.currentTimeMillis());
        instance.setEnding(true);
        gameEndListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(
                new JMatchEndEvent(instance.matchId(), GameManager.roleToPlayerRole(winner)));
        prestart.cancelWaitingTasks(instance);
        prestart.cancelHeadstarts(instance);
        timeLimits.cancelTimeLimit(instance);

        messaging.sendToInstanceComponent(instance, messages.renderLiteral(
                messages.winAnnouncement(winner),
                Map.of("wincon", reason, "rolecolor", messages.roleColor(winner))));
        Component titleComponent = messages.winTitle(winner);
        for (Player player : store.onlineAssignedPlayers(instance)) {
            player.showTitle(Title.title(titleComponent, Component.empty(),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        playerStates.resetOfflinePlayers(Bukkit.getOnlinePlayers(), instance.assignedPlayerIds());
        messaging.playInstanceSound(instance, winner == Role.HUNTER ? "game.fail-sound" : "game.win-sound");
        stats.completeMatch(instance.matchId(), winner);

        // Make all players invulnerable on game end if configured
        if (plugin.overrides().getBoolean(instance.originLobbyId(),
                "settings.players.invulnerability.on-game-end.enabled", true)) {
            store.onlineAssignedPlayers(instance).forEach(p -> p.setInvulnerable(true));
        }

        // cancel interval modifiers early so they don't fire during the end delay
        stateCommands.cancelIntervalModifiers(instance.matchId());
        // ran before the delay to ensure that any commands that depend on the match being completed can run immediately
        stateCommands.runConsoleCleanup(instance.matchId());
        stateCommands.runPlayerCleanup(instance.matchId(), store.onlineActivePlayers(instance));

        long delay = endDelayTicks(instance, immediate);
        Bukkit.getScheduler().runTaskLater(plugin, () -> showEndStatsOnce(instance), delay / 2);
        Bukkit.getScheduler().runTaskLater(plugin, () -> finishEndPhase(instance), delay);
    }

    /** Sends end-of-match statistics, exactly once per match. */
    /** End-delay in ticks, or zero when the end is immediate. */
    private long endDelayTicks(GameInstance instance, boolean immediate) {
        if (immediate) {
            return 0L;
        }
        return Math.max(0L, Math.round(plugin.overrides().getDouble(
                instance.originLobbyId(),
                "advanced.advanced-match-controls.end-delay", 10.0) * 20.0));
    }

    private void showEndStatsOnce(GameInstance instance) {
        if (instance.endStatsShown()) {
            return;
        }
        instance.setEndStatsShown(true);
        stats.showStats(instance.matchId(), store.onlineAssignedPlayers(instance));
    }

    /** Runs end commands and deactivates the match, exactly once per match. */
    private void finishEndPhase(GameInstance instance) {
        if (instance.endPhaseDone()) {
            return;
        }
        instance.setEndPhaseDone(true);
        teardownNow(instance);
    }

    /**
     * Shared immediate teardown tail: end commands, lobby teleport, role
     * reset, and deactivation. Callers run their own announcements, stat
     * handling, and cleanup commands first.
     */
    public void teardownNow(GameInstance instance) {
        long teardownId = instance.matchId();
        List<Player> participants = store.onlineAssignedPlayers(instance).stream()
                .filter(p -> playerStates.role(p).isParticipant()).toList();
        List<Player> spectators = instanceNonePlayers(instance);
        Set<UUID> transferred = transferSpectators(instance, spectators);
        List<Player> returning = spectators.stream()
                .filter(spectator -> !transferred.contains(spectator.getUniqueId())).toList();
        boolean lastMatch = store.instances().size() <= 1;
        stateCommands.runEnd(teardownId, participants, returning, instance.originLobbyId(), lastMatch);
        scatterEngineOffEnd(instance, participants);
        worldEngine.onMatchEnd(participants, returning, instance.originLobbyId(), teardownId);
        markOfflineEndWipes(instance);
        if (plugin.overrides().getBoolean(instance.originLobbyId(),
                "settings.players.roles.reset-on-game-end.enabled", true)) {
            Set<UUID> resetIds = new HashSet<>(instance.assignedPlayerIds());
            resetIds.removeAll(transferred);
            playerStates.resetRoles(resetIds);
        }
        // A finished match fields no sides, even when roles are kept.
        plugin.roleTeams().removeAll(store.onlineAssignedPlayers(instance).stream()
                .filter(player -> !transferred.contains(player.getUniqueId())).toList());
        plugin.spawnCamp().clearMatch(teardownId);
        instance.setActive(false);
        Set<UUID> clearIds = new HashSet<>(instance.assignedPlayerIds());
        clearIds.removeAll(transferred);
        stateCommands.untrackMatchExit(clearIds);
        playerStates.clearMatchFor(clearIds);
        stats.clearMatch(teardownId);
        flagStore.clearMatch(teardownId);
        cooldowns.clearMatch(teardownId);
        store.removeInstance(teardownId);
        plugin.logger().debug(DebugLevel.INFO, "debug.match-end", Map.of("index", GameManager.cellString(instance)));
        worldEngine.prepareNextCell();
        autostart.updateAutostartState();
    }

    /**
     * Moves ending-match spectators to the oldest running match of the
     * same lobby, returning the moved player ids. The target side mirrors
     * a spectator join (activation, spawn-pick teleport, fake spectator
     * mode, teams, join event and announcement); the ending side excludes
     * the moved players from its own cleanup below. Eliminated NONEs are
     * never moved: they return to the lobby with everyone else.
     */
    private Set<UUID> transferSpectators(GameInstance instance, List<Player> spectators) {
        Optional<GameInstance> target = transferTarget(
                store.instancesForLobby(instance.originLobbyId()), instance.matchId());
        if (target.isEmpty()) {
            return Set.of();
        }
        GameInstance destination = target.get();
        Set<UUID> moved = new HashSet<>();
        for (Player spectator : spectators) {
            if (playerStates.role(spectator) != Role.SPECTATOR) {
                continue;
            }
            UUID playerId = spectator.getUniqueId();
            instance.deactivate(playerId);
            destination.activate(playerId);
            if (destination.cellIndex().isPresent()) {
                worldEngine.teleportJoinersToCell(destination, List.of(spectator),
                        destination.cellIndex().getAsLong());
            }
            playerStates.recordLastSeen(spectator, spectator.getLocation());
            if (!plugin.fakeSpectators().isFakeSpectator(spectator)) {
                plugin.fakeSpectators().enable(spectator);
            }
            plugin.roleTeams().sync(spectator);
            Bukkit.getPluginManager().callEvent(new JPlayerJoinMatchEvent(destination.matchId(),
                    playerId, GameManager.roleToPlayerRole(Role.SPECTATOR)));
            messaging.sendToInstance(destination, "game.join-announce",
                    Map.of("player", spectator.getName(), "role", messages.roleName(Role.SPECTATOR)));
            moved.add(playerId);
        }
        stateCommands.trackMatchEntry(moved);
        return moved;
    }

    /**
     * Oldest running same-lobby match excluding the ending one; empty
     * when no sibling runs. Lowest live sublobby wins. Pure for tests.
     */
    static Optional<GameInstance> transferTarget(
            Collection<GameInstance> lobbyInstances, long endingMatchId) {
        return MatchStore.oldestSubLobby(lobbyInstances.stream()
                .filter(other -> other.matchId() != endingMatchId).toList());
    }

    /**
     * Defers the match-end wipe for assigned participants who are
     * offline at teardown; they get wiped on rejoin instead. Must run
     * before roles reset, while roles still identify participants.
     */
    private void markOfflineEndWipes(GameInstance instance) {
        if (!stateCommands.endWipeEnabled(instance.originLobbyId())) {
            return;
        }
        Set<UUID> online = new HashSet<>();
        for (Player onlinePlayer : store.onlineAssignedPlayers(instance)) {
            online.add(onlinePlayer.getUniqueId());
        }
        List<UUID> offline = new ArrayList<>();
        for (UUID assigned : instance.assignedPlayerIds()) {
            if (!online.contains(assigned) && playerStates.role(assigned).isParticipant()) {
                offline.add(assigned);
            }
        }
        stateCommands.markPendingEndWipe(offline);
    }

    /**
     * With the world engine off, gathers participants around the original
     * start center with fresh random offsets. A no-op with the engine on
     * or when no center was recorded.
     */
    private void scatterEngineOffEnd(GameInstance instance, List<Player> participants) {
        Location center = instance.startCenter();
        if (participants.isEmpty() || center == null || center.getWorld() == null
                || configService.getBoolean("world-engine.enabled", false)) {
            return;
        }
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        WorldEngineConfig spawnConfig = WorldEngineConfig.fromConfig(configService);
        List<Location> spawns = MatchTeleportService.spreadSpawnsForConfig(world, centerX, centerZ,
                MatchStartService.SURROUND_RADIUS, participants, spawnConfig);
        for (int index = 0; index < participants.size(); index++) {
            participants.get(index).teleport(spawns.get(index));
        }
    }

    /**
     * Ends every live match at once for server shutdown: tasks cancelled,
     * cleanup commands run, synchronous teardown. No announcements, no
     * scheduler use, and no career statistics are recorded.
     */
    public void shutdownAll() {
        for (GameInstance instance : List.copyOf(store.liveInstances())) {
            shutdown(instance);
        }
    }

    /** Synchronous no-stats teardown of one match for server shutdown. */
    private void shutdown(GameInstance instance) {
        if (!instance.active()) {
            return;
        }
        instance.refreshElapsedCache(System.currentTimeMillis());
        instance.setEnding(true);
        gameEndListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(new JMatchCancelEvent(instance.matchId()));
        prestart.cancelWaitingTasks(instance);
        prestart.cancelHeadstarts(instance);
        timeLimits.cancelTimeLimit(instance);
        stateCommands.cancelIntervalModifiers(instance.matchId());
        stateCommands.runConsoleCleanup(instance.matchId());
        stateCommands.runPlayerCleanup(instance.matchId(), store.onlineActivePlayers(instance));
        teardownNow(instance);
    }

    /** Cancels the match when exactly one is live; a no-op otherwise. */
    public void cancel() {
        store.singleLiveInstance().ifPresent(this::cancel);
    }

    /** Cancels one match with no winner. */
    public void cancel(GameInstance instance) {
        cancel(instance, false);
    }

    /**
     * Cancels one match with no winner. Career statistics are not
     * saved, but the in-memory match statistics still back the end screen.
     * Runs the normal end delay intermission unless immediate skips
     * straight to teardown. No JMatchEndEvent fires (there is no winner);
     * a JMatchCancelEvent fires instead.
     */
    public void cancel(GameInstance instance, boolean immediate) {
        if (!instance.active()) {
            return;
        }
        if (instance.ending()) {
            if (immediate) {
                showEndStatsOnce(instance);
                finishEndPhase(instance);
            }
            return;
        }
        instance.refreshElapsedCache(System.currentTimeMillis());
        instance.setEnding(true);
        gameEndListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(new JMatchCancelEvent(instance.matchId()));
        prestart.cancelWaitingTasks(instance);
        prestart.cancelHeadstarts(instance);
        timeLimits.cancelTimeLimit(instance);

        messaging.sendToInstance(instance, "game.cancelled", Map.of());
        for (Player player : store.onlineAssignedPlayers(instance)) {
            player.showTitle(Title.title(messages.component("game.cancelled-title"), Component.empty(),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        playerStates.resetOfflinePlayers(Bukkit.getOnlinePlayers(), instance.assignedPlayerIds());
        messaging.playInstanceSound(instance, "game.cancelled-sound");

        // Make all players invulnerable on cancel if configured
        if (plugin.overrides().getBoolean(instance.originLobbyId(),
                "settings.players.invulnerability.on-game-end.enabled", true)) {
            store.onlineAssignedPlayers(instance).forEach(p -> p.setInvulnerable(true));
        }

        // cancel interval modifiers early so they don't fire during the end delay
        stateCommands.cancelIntervalModifiers(instance.matchId());
        // ran before the delay to ensure that any commands that depend on the match being completed can run immediately
        stateCommands.runConsoleCleanup(instance.matchId());
        stateCommands.runPlayerCleanup(instance.matchId(), store.onlineActivePlayers(instance));

        long delay = endDelayTicks(instance, immediate);
        Bukkit.getScheduler().runTaskLater(plugin, () -> showEndStatsOnce(instance), delay / 2);
        Bukkit.getScheduler().runTaskLater(plugin, () -> finishEndPhase(instance), delay);
    }

    /** Ends the match next tick when exactly one is live; a no-op otherwise. */
    public void finishLater(Role winner, String reason) {
        store.singleLiveInstance().ifPresent(instance -> finishLater(instance, winner, reason));
    }

    /** Ends one match on the next tick. */
    public void finishLater(GameInstance instance, Role winner, String reason) {
        Bukkit.getScheduler().runTask(plugin, () -> finish(instance, winner, reason));
    }

    /** Online match assignees watching without playing, including eliminated hunters. */
    private List<Player> instanceNonePlayers(GameInstance instance) {
        Set<UUID> assigned = instance.assignedPlayerIds();
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> playerStates.role(p).isWatching() && assigned.contains(p.getUniqueId()))
                .map(p -> (Player) p).toList();
    }

}
