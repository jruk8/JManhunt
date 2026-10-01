package com.jruk8.jmanhunt.match.listeners;

import com.jruk8.jmanhunt.command.TagLocations;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.GameMessages;

/** Respawn routing: vanilla respawn hooks and delayed spectator revives. */
public final class PlayerRespawnListener implements Listener {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final CompassManager compass;
    private final GameMessages gameTexts;
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();

    public PlayerRespawnListener(JManhuntPlugin plugin, PlayerStateStore playerStates, GameManager game,
            CompassManager compass, GameMessages gameTexts) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.game = game;
        this.compass = compass;
        this.gameTexts = gameTexts;
        // Cancel any pending respawn tasks when a match ends so players
        // are not revived during the end sequence or after the match.
        game.addGameEndListener(instance -> cancelAllRespawnTasks());
    }

    /** True when the player has a delayed revive pending. */
    public boolean hasPendingRespawn(UUID playerId) {
        return respawnTasks.containsKey(playerId);
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        // Delayed revives re-give after leaving spectator mode; handing one
        // here would land it in the spectator inventory.
        if (match.isPresent() && playerStates.role(player).isParticipant()
                && !hasPendingRespawn(player.getUniqueId())
                && !plugin.fakeSpectators().isFakeSpectator(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> compass.giveCompass(player));
        }
        if (match.isEmpty() || !match.get().begun() || !playerStates.role(player).isParticipant()) {
            return;
        }
        long matchId = match.get().matchId();
        fireRespawnTriggers(player, matchId);
    }

    /**
     * Respawn event args: the death location as one 6-element list
     * arg, or empty when the server kept no death location (then
     * every {@code <args>} index reads {@code "null"}).
     */
    private static List<String> deathArgs(Player player) {
        Location death = player.getLastDeathLocation();
        if (death == null || death.getWorld() == null) {
            return List.of();
        }
        return List.of(TagLocations.formatLocation(death));
    }

    /**
     * Announces a delayed respawn, then routes through the spectator
     * revive. A non-positive delay revives immediately without announcing.
     */
    void scheduleRespawn(Player player, GameInstance instance, boolean quiet,
            int delaySeconds, String scheduledTemplate, long matchId) {
        if (!quiet && delaySeconds > 0) {
            game.messaging().sendToInstance(instance, scheduledTemplate,
                    Map.of("player", player.getName(), "seconds", Integer.toString(delaySeconds)));
        }
        respawnParticipant(player, delaySeconds, matchId);
    }

    /**
     * Effective respawn delay in seconds: the configured delay when delayed
     * respawn is enabled and positive, otherwise immediate (0). Pure for
     * tests.
     */
    public static int effectiveRespawnDelay(boolean enabled, int delaySeconds) {
        return enabled && delaySeconds > 0 ? delaySeconds : 0;
    }

    /**
     * Puts the player in fake spectator mode, then revives them after the
     * given delay (in seconds). A delay of 0 or less revives immediately.
     */
    private void respawnParticipant(Player player, int delaySeconds, long matchId) {
        UUID playerId = player.getUniqueId();
        BukkitTask existing = respawnTasks.remove(playerId);
        if (existing != null) {
            existing.cancel();
        }
        Bukkit.getScheduler().runTask(plugin, () -> plugin.fakeSpectators().enable(player));
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
        plugin.fakeSpectators().disable(player);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        Location respawn = player.getBedSpawnLocation();
        if (respawn != null) {
            player.teleport(respawn);
        }
        else {
            player.teleport(player.getWorld().getSpawnLocation());
        }
        // The fake-spectator fall before the revive must not convert
        // into damage on arrival.
        player.setFallDistance(0F);
        if (playerStates.role(player).isParticipant()) {
            compass.giveCompass(player);
            compass.refreshCompass(player);
            // Immediate revives stay silent, as before; only a waited-out
            // delay announces the return, for either role.
            if (delayed) {
                if (playerStates.role(player) == Role.HUNTER) {
                    game.messaging().sendToInstance(instance, gameTexts.getHunterRespawnImminent(),
                            Map.of("player", player.getName()));
                } else if (playerStates.role(player) == Role.SPEEDRUNNER) {
                    game.messaging().sendToInstance(instance, gameTexts.getSpeedrunnerRespawnImminent(),
                            Map.of("player", player.getName()));
                }
            }
        }
        if (!instance.begun() || !playerStates.role(player).isParticipant()) {
            return;
        }
        fireRespawnTriggers(player, matchId);
    }

    /** Fires ON_RESPAWN plus the role split with the death-location arg. */
    private void fireRespawnTriggers(Player player, long matchId) {
        List<String> eventArgs = deathArgs(player);
        game.stateCommands().runEventModifiers("ON_RESPAWN", player, matchId, eventArgs);
        Role role = playerStates.role(player);
        if (role == Role.HUNTER) {
            game.stateCommands().runEventModifiers("ON_HUNTER_RESPAWN", player, matchId, eventArgs);
        } else if (role == Role.SPEEDRUNNER) {
            game.stateCommands().runEventModifiers("ON_SPEEDRUNNER_RESPAWN", player, matchId,
                    eventArgs);
        }
    }

    private void cancelAllRespawnTasks() {
        for (BukkitTask task : respawnTasks.values()) {
            task.cancel();
        }
        respawnTasks.clear();
    }
}
