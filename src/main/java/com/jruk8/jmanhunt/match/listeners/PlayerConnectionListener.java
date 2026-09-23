package com.jruk8.jmanhunt.match.listeners;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.DisconnectDecision;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import com.jruk8.jmanhunt.world.WorldEngineService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;

/** Joins, quits, disconnect strikes, and rejoins. */
public final class PlayerConnectionListener implements Listener {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final MessageService messages;
    private final ConfigService config;
    private final LobbyService lobbies;
    private final LobbyTeleporter lobbyTeleporter;
    private final WorldEngineService worldEngine;
    private final SpeedrunnerDisconnectTracker disconnects;
    private final Map<UUID, BukkitTask> disconnectTasks;

    public PlayerConnectionListener(JManhuntPlugin plugin, PlayerStateStore playerStates, GameManager game,
            MessageService messages, ConfigService config, LobbyService lobbies,
            LobbyTeleporter lobbyTeleporter, WorldEngineService worldEngine,
            SpeedrunnerDisconnectTracker disconnects, Map<UUID, BukkitTask> disconnectTasks) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.game = game;
        this.messages = messages;
        this.config = config;
        this.lobbies = lobbies;
        this.lobbyTeleporter = lobbyTeleporter;
        this.worldEngine = worldEngine;
        this.disconnects = disconnects;
        this.disconnectTasks = disconnectTasks;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        worldEngine.careFor(player);
        playerStates.resetRolesIfAbsent(player);
        // Repair scoreboard teams in case roles and teams drifted apart.
        plugin.roleTeams().sync(player);
        lobbies.assignDefault(player.getUniqueId());

        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isPresent()) {
            // Rejoining a match in progress: active participants keep playing
            // where they left off and are never teleported to the lobby.
            if (playerStates.role(player) == Role.SPEEDRUNNER
                    || playerStates.role(player) == Role.HUNTER) {
                handleRejoin(player);
            }
            return;
        }
        int lobbyId = lobbyIdFor(player.getUniqueId());
        if (lobbyId >= 0 && game.instanceForLobby(lobbyId).isPresent()) {
            handleJoinDuringMatch(player, lobbyId);
        }
        // Everyone else keeps their queued role and is sent to their lobby.
        if (lobbyId >= 0) {
            lobbyTeleporter.teleportToLobby(List.of(player), lobbyId);
            lobbyTeleporter.setSpawnToLobbyQuiet(List.of(player), lobbyId);
        }
    }

    /** Parks a joiner whose lobby has a running match they are not part of. */
    private void handleJoinDuringMatch(Player player, int lobbyId) {
        // Their lobby has a running match they are not part of (a
        // newcomer or an eliminated player): wait in the lobby as a
        // spectator. Preserve the AFK role; only reset the rest to NONE.
        // With nowhere to wait (engine off or no lobby set), they join
        // the newest running match as a spectator instead.
        if (playerStates.role(player) != Role.AFK && !game.hasLobbyLocation(lobbyId)) {
            if (!game.joinLeastTimeMatch(player)) {
                playerStates.setRole(player.getUniqueId(), Role.NONE);
                plugin.roleTeams().sync(player);
            }
        } else {
            if (playerStates.role(player) != Role.AFK) {
                playerStates.setRole(player.getUniqueId(), Role.NONE);
                plugin.roleTeams().sync(player);
            }
            // Joining NONEs take spectator gamemode only with the toggle;
            // AFK players keep their role and their gamemode.
            if (playerStates.role(player) == Role.NONE
                    && config.getBoolean("settings.roles.turn-nones-spectator.enabled", false)) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.roleTeams().remove(player);
        int lobbyId = lobbyIdFor(player.getUniqueId());
        lobbies.remove(player.getUniqueId());
        if (config.getBoolean("settings.roles.reset-on-leave.enabled", true)
                && playerStates.role(player) != Role.AFK
                && (lobbyId < 0 || game.instanceForLobby(lobbyId).isEmpty())) {
            playerStates.setRole(player.getUniqueId(), Role.NONE);
        }
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isPresent()) {
            Role role = playerStates.role(player);
            // Pre-start quits disqualify instantly: no strikes, no grace.
            boolean preStart = !match.get().begun();
            if (role == Role.SPEEDRUNNER && playerStates.isActiveSpeedrunner(player.getUniqueId())) {
                if (preStart) {
                    eliminateDisconnectedPlayer(player.getUniqueId(), match.get().matchId(), role);
                } else {
                    handleDisconnect(player, Role.SPEEDRUNNER, match.get().matchId());
                }
            } else if (role == Role.HUNTER) {
                if (preStart) {
                    eliminateDisconnectedPlayer(player.getUniqueId(), match.get().matchId(), role);
                } else {
                    handleDisconnect(player, Role.HUNTER, match.get().matchId());
                }
            }
        }
        game.updateAutostartState();
    }

    private void handleDisconnect(Player player, Role role, long matchId) {
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty()) {
            return;
        }
        String roleKey = role == Role.SPEEDRUNNER ? "speedrunner" : "hunter";
        int maxStrikes = config.getInt("match.disconnect-handling." + roleKey + ".max-strikes", 3);
        int graceSeconds = Math.max(0,
                config.getInt("match.disconnect-handling." + roleKey + ".reconnect-grace-seconds", 60));
        DisconnectDecision decision =
                disconnects.registerDisconnect(player.getUniqueId(), matchId, maxStrikes);
        cancelDisconnectTask(disconnectTasks, player.getUniqueId());
        if (decision.forfeit()) {
            eliminateDisconnectedPlayer(player.getUniqueId(), matchId, role);
            return;
        }
        game.sendToInstance(match.get(), "game." + roleKey + "-disconnect-warning",
                Map.of("seconds", Integer.toString(graceSeconds)));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> eliminateDisconnectedPlayer(player.getUniqueId(), matchId, role), graceSeconds * 20L);
        disconnectTasks.put(player.getUniqueId(), task);
    }

    private void eliminateDisconnectedPlayer(UUID playerId, long matchId, Role role) {
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty() || !match.get().active() || !match.get().isActive(playerId)
                || playerStates.role(playerId) != role) {
            return;
        }
        GameInstance instance = match.get();
        cancelDisconnectTask(disconnectTasks, playerId);
        disconnects.clear(playerId);

        if (role == Role.SPEEDRUNNER) {
            playerStates.setSpeedrunnerAlive(playerId, false);
        }
        playerStates.setRole(playerId, Role.NONE);
        instance.deactivate(playerId);

        // Disconnect removal always lands on NONE; the toggle decides the
        // gamemode. AFK players are never tracked, so they keep theirs.
        Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer != null && config.getBoolean("settings.roles.turn-nones-spectator.enabled", false)) {
            onlinePlayer.setGameMode(GameMode.SPECTATOR);
        }
        if (onlinePlayer != null) {
            plugin.roleTeams().sync(onlinePlayer);
        }

        String roleKey = role == Role.SPEEDRUNNER ? "speedrunner" : "hunter";
        game.sendToInstance(instance, "game." + roleKey + "-disconnect-removed", Map.of());

        // No last-died lines: the win that follows is the announcement.
        // Unbegun matches never crown a winner: they cancel instead.
        boolean bucketEmpty = role == Role.SPEEDRUNNER
                ? game.activeRunnerCount(instance) == 0
                : game.activeHunterCount(instance) == 0;
        if (bucketEmpty) {
            if (instance.begun()) {
                game.finishLater(instance, role == Role.SPEEDRUNNER ? Role.HUNTER : Role.SPEEDRUNNER);
            } else {
                game.cancel(instance);
            }
        }

        String soundKey = role == Role.SPEEDRUNNER ? "game.speedrunner-death" : "game.hunter-death";
        game.playInstanceSound(instance, soundKey);
    }

    private void handleRejoin(Player player) {
        UUID playerId = player.getUniqueId();
        if (!disconnectTasks.containsKey(playerId)) {
            return;
        }
        cancelDisconnectTask(disconnectTasks, playerId);
        // Strikes are intentionally NOT cleared here so that repeated
        // disconnect/reconnect cycles accumulate toward the max-strikes limit.
        Role role = playerStates.role(player);
        String roleKey = role == Role.SPEEDRUNNER ? "speedrunner" : "hunter";
        Optional<GameInstance> match = game.instanceOf(playerId);
        if (match.isPresent()) {
            game.sendToInstance(match.get(), "game." + roleKey + "-disconnect-cancelled", Map.of());
        } else {
            messages.broadcast("game." + roleKey + "-disconnect-cancelled");
        }
    }

    /**
     * Lobby used for join/quit placement: the player's lobby, else the
     * default lobby (forced to 0 with the world engine off). Negative when
     * the player is lobby-less by configuration.
     */
    private int lobbyIdFor(UUID playerId) {
        return lobbies.lobbyOf(playerId).map(Lobby::id)
                .orElseGet(() -> lobbies.multiLobbyAllowed() ? lobbies.defaultLobbyId() : 0);
    }

    /** Cancels a pending disconnect grace task. Shared with combat deaths. */
    static void cancelDisconnectTask(Map<UUID, BukkitTask> tasks, UUID playerId) {
        BukkitTask task = tasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
}
