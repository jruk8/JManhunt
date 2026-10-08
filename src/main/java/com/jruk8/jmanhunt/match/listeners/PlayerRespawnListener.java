package com.jruk8.jmanhunt.match.listeners;

import com.jruk8.jmanhunt.command.TagLocations;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
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
    /** Role plus fake-spectator state. */
    public record RespawnPlayers(PlayerStateStore states, FakeSpectatorService fakes) {
    }

    private final TaskScheduler tasks;
    private final RespawnPlayers players;
    private final GameManager game;
    private final CompassManager compass;
    private final GameMessages gameTexts;
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();

    public PlayerRespawnListener(TaskScheduler tasks, RespawnPlayers players, GameManager game,
            CompassManager compass, GameMessages gameTexts) {
        this.tasks = tasks;
        this.players = players;
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
        if (match.isPresent() && players.states().role(player).isParticipant()
                && !hasPendingRespawn(player.getUniqueId())
                && !players.fakes().isFakeSpectator(player)) {
            tasks.run(() -> compass.giveCompass(player));
        }
        if (match.isEmpty() || !match.get().begun() || !players.states().role(player).isParticipant()) {
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
     * Holds a match joiner in fake spectator mode for the WAIT timing,
     * then releases them into play without the respawn revive (no
     * teleport, no heal, no triggers). A non-positive wait joins
     * instantly with no hold. Replaces any pending task for the
     * player and cancels cleanly on leave or match end, like a
     * respawn schedule.
     */
    public void scheduleJoinHold(Player player, GameInstance instance, int waitSeconds) {
        UUID playerId = player.getUniqueId();
        BukkitTask existing = respawnTasks.remove(playerId);
        if (existing != null) {
            existing.cancel();
        }
        if (waitSeconds <= 0) {
            return;
        }
        long matchId = instance.matchId();
        tasks.run(() -> players.fakes().enable(player));
        BukkitTask task = tasks.runLater(() -> {
            respawnTasks.remove(playerId);
            if (!game.isActiveInInstance(matchId, playerId)) {
                return;
            }
            releaseJoinHold(player, matchId);
        }, waitSeconds * 20L);
        respawnTasks.put(playerId, task);
    }

    /** Releases a join hold, keeping any headstart hold intact. */
    private void releaseJoinHold(Player player, long matchId) {
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty() || match.get().isHeadstartHeld(player.getUniqueId())) {
            return;
        }
        players.fakes().disable(player);
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
        tasks.run(() -> players.fakes().enable(player));
        if (delaySeconds <= 0) {
            tasks.run(() -> revivePlayer(player, matchId, false));
            return;
        }
        BukkitTask task = tasks.runLater(() -> {
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
        if (players.states().role(player) == Role.SPEEDRUNNER) {
            players.states().setSpeedrunnerAlive(player.getUniqueId(), true);
        }
        players.fakes().disable(player);
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
        if (players.states().role(player).isParticipant()) {
            compass.giveCompass(player);
            compass.refreshCompass(player);
            // Immediate revives stay silent, as before; only a waited-out
            // delay announces the return, for either role.
            if (delayed) {
                if (players.states().role(player) == Role.HUNTER) {
                    game.messaging().sendToInstance(instance, gameTexts.getHunterRespawnImminent(),
                            Map.of("player", player.getName()));
                } else if (players.states().role(player) == Role.SPEEDRUNNER) {
                    game.messaging().sendToInstance(instance, gameTexts.getSpeedrunnerRespawnImminent(),
                            Map.of("player", player.getName()));
                }
            }
        }
        if (!instance.begun() || !players.states().role(player).isParticipant()) {
            return;
        }
        fireRespawnTriggers(player, matchId);
    }

    /** Fires ON_RESPAWN plus the role split with the death-location arg. */
    private void fireRespawnTriggers(Player player, long matchId) {
        game.instance(matchId).ifPresent(instance ->
                instance.markRespawnFired(player.getUniqueId(),
                        instance.lifeOf(player.getUniqueId())));
        List<String> eventArgs = deathArgs(player);
        game.stateCommands().runEventModifiers("ON_RESPAWN", player, matchId, eventArgs);
        Role role = players.states().role(player);
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
