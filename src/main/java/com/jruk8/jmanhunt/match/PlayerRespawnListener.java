package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Respawn routing: vanilla respawn hooks and delayed spectator revives. */
public final class PlayerRespawnListener implements Listener {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final CompassManager compass;
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();

    public PlayerRespawnListener(JManhuntPlugin plugin, PlayerStateStore playerStates, GameManager game,
            CompassManager compass) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.game = game;
        this.compass = compass;
        // Cancel any pending respawn tasks when a match ends so players
        // are not revived during the end sequence or after the match.
        game.addGameEndListener(instance -> cancelAllRespawnTasks());
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isPresent() && playerStates.role(player).isParticipant()) {
            Bukkit.getScheduler().runTask(plugin, () -> compass.giveCompass(player));
        }
        if (match.isEmpty() || !match.get().begun() || !playerStates.role(player).isParticipant()) {
            return;
        }
        long matchId = match.get().matchId();
        game.stateCommands().runEventModifiers("ON_RESPAWN", player, matchId);
        Role role = playerStates.role(player);
        if (role == Role.HUNTER) {
            game.stateCommands().runEventModifiers("ON_HUNTER_RESPAWN", player, matchId);
        } else if (role == Role.SPEEDRUNNER) {
            game.stateCommands().runEventModifiers("ON_SPEEDRUNNER_RESPAWN", player, matchId);
        }
    }

    /**
     * Announces a delayed respawn, then routes through the spectator
     * revive. A non-positive delay revives immediately without announcing.
     */
    void scheduleRespawn(Player player, GameInstance instance, boolean quiet,
            int delaySeconds, String scheduledKey, long matchId) {
        if (!quiet && delaySeconds > 0) {
            game.sendToInstance(instance, scheduledKey,
                    Map.of("player", player.getName(), "seconds", Integer.toString(delaySeconds)));
        }
        respawnParticipant(player, delaySeconds, matchId);
    }

    /**
     * Effective respawn delay in seconds: the configured delay when delayed
     * respawn is enabled and positive, otherwise immediate (0). Pure for
     * tests.
     */
    static int effectiveRespawnDelay(boolean enabled, int delaySeconds) {
        return enabled && delaySeconds > 0 ? delaySeconds : 0;
    }

    /**
     * Puts the player in spectator mode, then revives them after the given
     * delay (in seconds). A delay of 0 or less revives them immediately.
     */
    private void respawnParticipant(Player player, int delaySeconds, long matchId) {
        UUID playerId = player.getUniqueId();
        BukkitTask existing = respawnTasks.remove(playerId);
        if (existing != null) {
            existing.cancel();
        }
        Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
        if (delaySeconds <= 0) {
            Bukkit.getScheduler().runTask(plugin, () -> revivePlayer(player, matchId, false));
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            respawnTasks.remove(playerId);
            if (!game.isActiveInInstance(matchId, playerId)) {
                return;
            }
            revivePlayer(player, matchId, true);
        }, delaySeconds * 20L);
        respawnTasks.put(playerId, task);
    }

    private void revivePlayer(Player player, long matchId, boolean delayed) {
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty() || !match.get().isActive(player.getUniqueId())) {
            return;
        }
        GameInstance instance = match.get();
        if (playerStates.role(player) == Role.SPEEDRUNNER) {
            playerStates.setSpeedrunnerAlive(player.getUniqueId(), true);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        Location respawn = player.getBedSpawnLocation();
        if (respawn != null) {
            player.teleport(respawn);
        }
        else {
            player.teleport(player.getWorld().getSpawnLocation());
        }
        if (playerStates.role(player).isParticipant()) {
            compass.giveCompass(player);
            compass.refreshCompass(player);
            // Immediate revives stay silent, as before; only a waited-out
            // delay announces the return, for either role.
            if (delayed) {
                if (playerStates.role(player) == Role.HUNTER) {
                    game.sendToInstance(instance, "game.hunter-respawn-imminent", Map.of("player", player.getName()));
                } else if (playerStates.role(player) == Role.SPEEDRUNNER) {
                    game.sendToInstance(instance, "game.speedrunner-respawn-imminent",
                            Map.of("player", player.getName()));
                }
            }
        }
        if (!instance.begun() || !playerStates.role(player).isParticipant()) {
            return;
        }
        game.stateCommands().runEventModifiers("ON_RESPAWN", player, matchId);
        Role role = playerStates.role(player);
        if (role == Role.HUNTER) {
            game.stateCommands().runEventModifiers("ON_HUNTER_RESPAWN", player, matchId);
        } else if (role == Role.SPEEDRUNNER) {
            game.stateCommands().runEventModifiers("ON_SPEEDRUNNER_RESPAWN", player, matchId);
        }
    }

    private void cancelAllRespawnTasks() {
        for (BukkitTask task : respawnTasks.values()) {
            task.cancel();
        }
        respawnTasks.clear();
    }
}
