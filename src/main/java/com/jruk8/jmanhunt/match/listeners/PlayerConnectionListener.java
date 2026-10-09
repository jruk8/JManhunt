package com.jruk8.jmanhunt.match.listeners;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.MatchConfig;
import com.jruk8.jmanhunt.config.PlayerSettings;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.DisconnectDecision;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import com.jruk8.jmanhunt.world.WorldEngineService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.LeaveDestination;

/** Joins, quits, disconnect strikes, and rejoins. */
public final class PlayerConnectionListener implements Listener {
    /** Role, visibility, join chat, and disconnect text. */
    public record ConnectReads(PlayerStateStore states, FakeSpectatorService fakes,
            MessageService messages, GameMessages gameTexts) {
    }

    /** Match, lobbies, compass, and disconnect tracking. */
    public record ConnectMatch(GameManager game, LobbyService lobbies, CompassManager compass,
            SpeedrunnerDisconnectTracker disconnects, Map<UUID, BukkitTask> disconnectTasks) {
    }

    /** Lobby teleports plus engine care. */
    public record ConnectWorld(LobbyTeleporter lobbyTeleporter, WorldEngineService worldEngine) {
    }

    /** Player plus disconnect-handling config. */
    public record ConnectConfig(PlayerSettings players,
            MatchConfig.DisconnectHandling disconnectHandling) {
    }

    /** Role teams, delayed-disconnect scheduler, and respawn holds. */
    public record ConnectEdge(RoleTeamService roleTeams, TaskScheduler tasks,
            PlayerRespawnListener respawn) {
    }

    private final ConnectReads reads;
    private final ConnectMatch match;
    private final ConnectWorld world;
    private final ConnectConfig config;
    private final ConnectEdge edge;
    /**
     * Players whose disconnect grace expired while they were offline:
     * fully out of the game, owed a wipe plus destination routing on
     * rejoin. Memory only, like the disconnect tasks.
     */
    private final Set<UUID> expiredOffline = new HashSet<>();

    public PlayerConnectionListener(ConnectReads reads, ConnectMatch match, ConnectWorld world,
            ConnectConfig config, ConnectEdge edge) {
        this.reads = reads;
        this.match = match;
        this.world = world;
        this.config = config;
        this.edge = edge;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        world.worldEngine().careFor(player);
        reads.states().resetRolesIfAbsent(player);
        // A match that ended while they were offline left a deferred
        // wipe behind: run it before anything else reads their state.
        this.match.game().applyPendingEndWipe(player);
        // A crash never ran teardown, so rows left in crash_cleanup get
        // the same wipe here; the join-time toolbar strip finishes it.
        this.match.game().applyPendingCrashWipe(player);
        // Repair scoreboard teams in case roles and teams drifted apart.
        edge.roleTeams().sync(player);
        this.match.lobbies().assignDefault(player.getUniqueId());

        Optional<GameInstance> match = this.match.game().instanceOf(player.getUniqueId());
        if (match.isPresent()) {
            // Rejoining a match in progress: active participants keep playing
            // where they left off and are never teleported to the lobby.
            if (reads.states().role(player) == Role.SPEEDRUNNER
                    || reads.states().role(player) == Role.HUNTER) {
                handleRejoin(player);
            }
            return;
        }
        if (expiredOffline.remove(player.getUniqueId())) {
            handleExpiredRejoin(player);
            return;
        }
        int lobbyId = lobbyIdFor(player.getUniqueId());
        if (lobbyId >= 0 && this.match.game().instanceForLobby(lobbyId).isPresent()) {
            handleJoinDuringMatch(player, lobbyId);
        }
        // Everyone else keeps their queued role and is sent to their lobby.
        if (lobbyId >= 0) {
            world.lobbyTeleporter().teleportToLobby(List.of(player), lobbyId);
            world.lobbyTeleporter().setSpawnToLobbyQuiet(List.of(player), lobbyId);
            this.match.lobbies().applyLobbyCollisions(player);
        }
    }

    /** Parks a joiner whose lobby has a running match they are not part of. */
    private void handleJoinDuringMatch(Player player, int lobbyId) {
        // Their lobby has a running match they are not part of (a
        // newcomer, an eliminated player, or a held queuer): they wait
        // in the lobby under their current role, so held queues and
        // queued spectators survive the relog. With nowhere to wait
        // (engine off or no lobby set), they join the newest running
        // match as a spectator instead.
        if (reads.states().role(player) != Role.AFK && !this.match.game().hasLobbyLocation(lobbyId)) {
            this.match.game().joinLeastTimeMatch(player);
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        edge.roleTeams().remove(player);
        this.match.lobbies().remove(player.getUniqueId());
        Optional<GameInstance> match = this.match.game().instanceOf(player.getUniqueId());
        if (match.isPresent()) {
            Role role = reads.states().role(player);
            // Pre-start quits disqualify instantly: no strikes, no grace.
            boolean preStart = !match.get().begun();
            if (role == Role.SPEEDRUNNER && reads.states().isActiveSpeedrunner(player.getUniqueId())) {
                if (preStart) {
                    eliminateDisconnectedPlayer(player.getUniqueId(), match.get().matchId(), role, false);
                } else {
                    handleDisconnect(player, Role.SPEEDRUNNER, match.get().matchId());
                }
            } else if (role == Role.HUNTER) {
                if (preStart) {
                    eliminateDisconnectedPlayer(player.getUniqueId(), match.get().matchId(), role, false);
                } else {
                    handleDisconnect(player, Role.HUNTER, match.get().matchId());
                }
            }
            cancelIfAbandoned(match.get(), player.getUniqueId());
        }
        this.match.game().updateAutostartState();
    }

    /**
     * Cancels the match the moment its last active participant
     * disconnects: no waiting on reconnect grace or the time limit.
     * Spectators and eliminated players never keep a match alive. The
     * quitter still counts as online during the quit event, so they
     * are excluded from the check.
     */
    private void cancelIfAbandoned(GameInstance instance, UUID quitterId) {
        if (!instance.active()) {
            return;
        }
        boolean anyoneElse = this.match.game().onlineActivePlayers(instance).stream()
                .anyMatch(other -> !other.getUniqueId().equals(quitterId));
        if (anyoneElse) {
            return;
        }
        for (UUID assigned : instance.assignedPlayerIds()) {
            cancelDisconnectTask(this.match.disconnectTasks(), assigned);
            this.match.disconnects().clear(assigned);
        }
        this.match.game().cancel(instance, true);
    }

    private void handleDisconnect(Player player, Role role, long matchId) {
        Optional<GameInstance> match = this.match.game().instance(matchId);
        if (match.isEmpty()) {
            return;
        }
        var rules = role == Role.SPEEDRUNNER
                ? config.disconnectHandling().getSpeedrunner()
                : config.disconnectHandling().getHunter();
        int maxStrikes = rules.getMaxStrikes();
        int graceSeconds = Math.max(0, rules.getReconnectGraceSeconds());
        DisconnectDecision decision =
                this.match.disconnects().registerDisconnect(player.getUniqueId(), matchId, maxStrikes);
        cancelDisconnectTask(this.match.disconnectTasks(), player.getUniqueId());
        if (decision.forfeit()) {
            eliminateDisconnectedPlayer(player.getUniqueId(), matchId, role, true);
            return;
        }
        String warning = role == Role.SPEEDRUNNER ? reads.gameTexts().getSpeedrunnerDisconnectWarning()
                : reads.gameTexts().getHunterDisconnectWarning();
        this.match.game().messaging().sendToInstance(match.get(), warning,
                Map.of("seconds", Integer.toString(graceSeconds)));
        BukkitTask task = edge.tasks().runLater(
                () -> eliminateDisconnectedPlayer(player.getUniqueId(), matchId, role, true),
                graceSeconds * 20L);
        this.match.disconnectTasks().put(player.getUniqueId(), task);
    }

    /**
     * Removes a disconnected player from the match. Begun-match
     * removals drop any retained hold gear at the hold location and
     * mark the player expired for destination routing on rejoin.
     * Pre-start removals only discard the retention: that gear is
     * lobby gear and stays with the player.
     */
    private void eliminateDisconnectedPlayer(UUID playerId, long matchId, Role role, boolean begunMatch) {
        Optional<GameInstance> match = this.match.game().instance(matchId);
        if (match.isEmpty() || !match.get().active() || !match.get().isActive(playerId)
                || reads.states().role(playerId) != role) {
            return;
        }
        GameInstance instance = match.get();
        cancelDisconnectTask(this.match.disconnectTasks(), playerId);
        this.match.disconnects().clear(playerId);
        if (begunMatch) {
            reads.fakes().dropSnapshot(playerId);
            expiredOffline.add(playerId);
        } else {
            reads.fakes().discardSnapshot(playerId);
        }

        if (role == Role.SPEEDRUNNER) {
            reads.states().setSpeedrunnerAlive(playerId, false);
        }
        String playerName = Bukkit.getOfflinePlayer(playerId).getName();
        instance.recordDeath(playerId, playerName != null ? playerName : playerId.toString(), role);
        reads.states().setRole(playerId, Role.NONE);
        instance.deactivate(playerId);
        // Elimination is terminal for match membership: deactivated players
        // rejoin to the lobby, never the match, so the owed end wipe is
        // marked now, while the role still proves participant gear.
        if (this.match.game().stateCommands().endWipeEnabled(instance.originLobbyId())) {
            this.match.game().stateCommands().markPendingEndWipe(List.of(playerId));
        }
        this.match.compass().clearHotspotHistory(playerId);
        this.match.compass().reconcileTeammateModes(instance);
        if (playerName != null) {
            this.match.game().flagStore().removePlayer(matchId, playerName);
        }

        // Disconnect removal always lands on NONE and keeps the mode.
        // AFK players are never tracked, so they keep theirs.
        Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer != null) {
            edge.roleTeams().sync(onlinePlayer);
        }

        announceDisconnectRemoval(instance, role);
    }

    /** Announces a disconnect removal and finishes when its bucket emptied. */
    private void announceDisconnectRemoval(GameInstance instance, Role role) {
        String removed = role == Role.SPEEDRUNNER ? reads.gameTexts().getSpeedrunnerDisconnectRemoved()
                : reads.gameTexts().getHunterDisconnectRemoved();
        this.match.game().messaging().sendToInstance(instance, removed, Map.of());

        // No last-died lines: the win that follows is the announcement.
        // Unbegun matches never crown a winner: they cancel instead.
        boolean bucketEmpty = role == Role.SPEEDRUNNER
                ? this.match.game().activeRunnerCount(instance) == 0
                : this.match.game().activeHunterCount(instance) == 0;
        if (bucketEmpty) {
            if (instance.begun()) {
                this.match.game().finishLater(instance, role == Role.SPEEDRUNNER ? Role.HUNTER : Role.SPEEDRUNNER,
                        role == Role.SPEEDRUNNER
                                ? "All speedrunners disconnected"
                                : "All hunters disconnected");
            } else {
                this.match.game().cancel(instance);
            }
        }

        String soundKey = role == Role.SPEEDRUNNER ? "game.speedrunner-death" : "game.hunter-death";
        this.match.game().messaging().playInstanceSound(instance, soundKey);
    }

    private void handleRejoin(Player player) {
        UUID playerId = player.getUniqueId();
        expiredOffline.remove(playerId);
        if (!this.match.disconnectTasks().containsKey(playerId)) {
            return;
        }
        cancelDisconnectTask(this.match.disconnectTasks(), playerId);
        // Strikes are intentionally NOT cleared here so that repeated
        // disconnect/reconnect cycles accumulate toward the max-strikes limit.
        Role role = reads.states().role(player);
        String cancelled = role == Role.SPEEDRUNNER ? reads.gameTexts().getSpeedrunnerDisconnectCancelled()
                : reads.gameTexts().getHunterDisconnectCancelled();
        Optional<GameInstance> match = this.match.game().instanceOf(playerId);
        if (match.isPresent()) {
            this.match.game().messaging().sendToInstance(match.get(), cancelled, Map.of());
        } else {
            reads.messages().broadcastRaw(cancelled);
        }
        // A hold that survived the disconnect re-applies; an expired
        // hold just drops the retention.
        if (holdActive(playerId)) {
            reads.fakes().enable(player);
        } else {
            reads.fakes().discardSnapshot(playerId);
        }
    }

    /** True while a headstart or join hold still waits on the player. */
    private boolean holdActive(UUID playerId) {
        Optional<GameInstance> match = this.match.game().instanceOf(playerId);
        if (match.isPresent() && match.get().isHeadstartHeld(playerId)) {
            return true;
        }
        return edge.respawn().hasPendingRespawn(playerId);
    }

    /**
     * Returns a player whose disconnect grace expired: fully out of
     * the game, so the live inventory wipes (held gear already dropped
     * at the hold location on expiry) and the leave destination
     * decides between spectating and the lobby. With nowhere to
     * spectate they fall back to the lobby.
     */
    private void handleExpiredRejoin(Player player) {
        this.match.game().stateCommands().resetPlayer(player);
        int lobbyId = lobbyIdFor(player.getUniqueId());
        if (LeaveDestination.forLobby(this.match.game().matchSettings(), lobbyId)
                == LeaveDestination.SPECTATOR
                && this.match.game().joinLeastTimeMatch(player)) {
            return;
        }
        if (lobbyId >= 0) {
            world.lobbyTeleporter().teleportToLobby(List.of(player), lobbyId);
            world.lobbyTeleporter().setSpawnToLobbyQuiet(List.of(player), lobbyId);
            this.match.lobbies().applyLobbyCollisions(player);
        }
    }

    /**
     * Lobby used for join/quit placement: the player's lobby, else the
     * default lobby (forced to 0 with the world engine off). Negative when
     * the player is lobby-less by configuration.
     */
    private int lobbyIdFor(UUID playerId) {
        return this.match.lobbies().lobbyOf(playerId).map(Lobby::id)
                .orElseGet(() -> this.match.lobbies().multiLobbyAllowed() ? this.match.lobbies().defaultLobbyId() : 0);
    }

    /** Cancels a pending disconnect grace task. Shared with combat deaths. */
    static void cancelDisconnectTask(Map<UUID, BukkitTask> tasks, UUID playerId) {
        BukkitTask task = tasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
}
