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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.jruk8.jmanhunt.match.CountdownService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.LimboFeedbackService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.GameMessages;

/** Respawn routing: vanilla respawn hooks and delayed spectator revives. */
public final class PlayerRespawnListener implements Listener {
    /** Role, fake-spectator state, countdowns, and limbo. */
    public record RespawnPlayers(PlayerStateStore states, FakeSpectatorService fakes,
            CountdownService countdowns, LimboFeedbackService limbo) {
    }

    /** Countdown key for one player's join hold or respawn delay. */
    private record HoldKey(UUID playerId) {
    }

    private final TaskScheduler tasks;
    private final RespawnPlayers players;
    private final GameManager game;
    private final CompassManager compass;
    private final GameMessages gameTexts;

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
        return players.countdowns().running(new HoldKey(playerId));
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
        // Holds own the trigger: a pending delay or headstart hold
        // fires ON_RESPAWN at its release, never at this vanilla
        // respawn.
        if (hasPendingRespawn(player.getUniqueId())
                || match.get().isHeadstartHeld(player.getUniqueId())) {
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
     * Routes through the spectator revive: the limbo countdown
     * already announces the wait, so no line sends here. A
     * non-positive delay revives immediately.
     */
    void scheduleRespawn(Player player, int delaySeconds, long matchId) {
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
     * Schedules a join hold through a lazily resolved listener. A null
     * listener (wiring not finished yet) skips with one log line and never
     * throws, so mid-match joins stay safe during startup ordering.
     */
    public static void scheduleHold(Supplier<PlayerRespawnListener> respawn, Player player,
            GameInstance instance, int waitSeconds, Consumer<String> skipLog) {
        PlayerRespawnListener listener = respawn.get();
        if (listener == null) {
            skipLog.accept("Skipping join hold for " + player.getName()
                    + ": respawn listener not wired yet.");
            return;
        }
        listener.scheduleJoinHold(player, instance, waitSeconds);
    }

    /**
     * Holds a match joiner in fake spectator mode for the WAIT timing,
     * then releases them into play without the respawn revive (no
     * teleport, no heal). The release runs the deferred ON_RESPAWN
     * catch-up when this life has not fired it yet. A non-positive
     * wait joins instantly with no hold. Replaces any pending task
     * for the player and cancels cleanly on leave or match end,
     * like a respawn schedule.
     */
    public void scheduleJoinHold(Player player, GameInstance instance, int waitSeconds) {
        UUID playerId = player.getUniqueId();
        HoldKey key = new HoldKey(playerId);
        players.countdowns().cancel(key);
        players.limbo().untrackHold(playerId);
        if (waitSeconds <= 0) {
            return;
        }
        long matchId = instance.matchId();
        tasks.run(() -> players.fakes().enable(player));
        players.limbo().trackHold(playerId);
        players.countdowns().start(key, waitSeconds,
                remaining -> holdTick(key, matchId, playerId, remaining),
                () -> {
                    players.limbo().untrackHold(playerId);
                    if (game.isActiveInInstance(matchId, playerId)) {
                        releaseJoinHold(player, matchId);
                    }
                });
    }

    /** Join-hold tick: polls the mark, then feeds the match limbo census. */
    private void holdTick(HoldKey key, long matchId, UUID playerId, int remaining) {
        boolean mark = players.countdowns().pollMark(key, remaining);
        game.instance(matchId).ifPresent(instance -> players.limbo().limboTick(instance, remaining, mark));
    }

    /**
     * Releases a join hold, keeping any headstart hold intact. The
     * player lands on a fresh origin spawn, the same selection a
     * match entrant gets, in case they flew off during the hold,
     * then runs the deferred ON_RESPAWN catch-up when this life
     * has not fired it yet.
     */
    private void releaseJoinHold(Player player, long matchId) {
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty() || match.get().isHeadstartHeld(player.getUniqueId())) {
            return;
        }
        if (player.isOnline()) {
            game.scatterEntrantToSpawn(match.get(), player);
        }
        players.fakes().disable(player);
        players.limbo().announceSpawned(match.get(), player);
        GameInstance instance = match.get();
        if (instance.markRespawnFired(player.getUniqueId(),
                instance.lifeOf(player.getUniqueId()))) {
            game.stateCommands().runRespawnForPlayer(matchId, player);
        }
    }

    /**
     * Puts the player in fake spectator mode, then revives them after the
     * given delay (in seconds). A delay of 0 or less revives immediately.
     */
    private void respawnParticipant(Player player, int delaySeconds, long matchId) {
        UUID playerId = player.getUniqueId();
        HoldKey key = new HoldKey(playerId);
        players.countdowns().cancel(key);
        players.limbo().untrackHold(playerId);
        tasks.run(() -> players.fakes().enable(player));
        if (delaySeconds <= 0) {
            tasks.run(() -> revivePlayer(player, matchId, false));
            return;
        }
        players.limbo().trackHold(playerId);
        players.countdowns().start(key, delaySeconds,
                remaining -> holdTick(key, matchId, playerId, remaining),
                () -> {
                    players.limbo().untrackHold(playerId);
                    if (game.isActiveInInstance(matchId, playerId)) {
                        revivePlayer(player, matchId, true);
                    }
                });
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
        if (!instance.begun() || !players.states().role(player).isParticipant()
                || instance.isHeadstartHeld(player.getUniqueId())) {
            return;
        }
        fireRespawnTriggers(player, matchId);
    }

    /**
     * Fires ON_RESPAWN plus the role split with the death-location
     * arg: live matches and live players only, once per life (shared
     * mark with the switch catch-up and hold releases).
     */
    private void fireRespawnTriggers(Player player, long matchId) {
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty()) {
            return;
        }
        GameInstance instance = match.get();
        UUID playerId = player.getUniqueId();
        if (!instance.active() || instance.ending() || !instance.isActive(playerId)) {
            return;
        }
        Role role = players.states().role(player);
        if (!role.isParticipant()) {
            return;
        }
        if (!instance.markRespawnFired(playerId, instance.lifeOf(playerId))) {
            return;
        }
        List<String> eventArgs = deathArgs(player);
        game.stateCommands().runEventModifiers("ON_RESPAWN", player, matchId, eventArgs);
        if (role == Role.HUNTER) {
            game.stateCommands().runEventModifiers("ON_HUNTER_RESPAWN", player, matchId, eventArgs);
        } else if (role == Role.SPEEDRUNNER) {
            game.stateCommands().runEventModifiers("ON_SPEEDRUNNER_RESPAWN", player, matchId,
                    eventArgs);
        }
    }

    private void cancelAllRespawnTasks() {
        for (UUID playerId : players.limbo().pendingHoldIds()) {
            players.countdowns().cancel(new HoldKey(playerId));
        }
        players.limbo().clearHolds();
    }
}
