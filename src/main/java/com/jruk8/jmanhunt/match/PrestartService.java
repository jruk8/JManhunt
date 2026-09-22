package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.stats.StatsManager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Runs the pre-start phase: headstart arming and countdowns plus the
 * waiting-for-damage reminders and expiry. The start/finish transitions
 * themselves stay on GameManager behind {@link MatchControl}.
 */
final class PrestartService {
    private final JManhuntPlugin plugin;
    private final ConfigService configService;
    private final MessageService messages;
    private final PlayerStateStore playerStates;
    private final StatsManager stats;
    private final GameStateCommandManager stateCommands;
    private final MatchStore store;
    private final MatchMessaging messaging;
    private final MatchControl control;

    PrestartService(JManhuntPlugin plugin, ConfigService configService, MessageService messages,
            PlayerStateStore playerStates, StatsManager stats, GameStateCommandManager stateCommands,
            MatchStore store, MatchMessaging messaging, MatchControl control) {
        this.plugin = plugin;
        this.configService = configService;
        this.messages = messages;
        this.playerStates = playerStates;
        this.stats = stats;
        this.stateCommands = stateCommands;
        this.store = store;
        this.messaging = messaging;
        this.control = control;
    }

    void armHeadstarts(GameInstance instance) {
        armHeadstartSide(instance, Role.HUNTER, Headstart.parse(plugin.getConfig(), "hunter"));
        armHeadstartSide(instance, Role.SPEEDRUNNER, Headstart.parse(plugin.getConfig(), "speedrunner"));
    }

    private void armHeadstartSide(GameInstance instance, Role role, Headstart side) {
        HeadstartState state = instance.headstart(role);
        boolean armed = side.enabled() && side.delaySeconds() > 0;
        state.setArmed(armed);
        state.setRemaining(armed ? side.delaySeconds() : 0);
    }

    /** Starts the countdown for every armed headstart side. */
    void beginHeadstarts(GameInstance instance) {
        beginHeadstart(instance, Role.HUNTER);
        beginHeadstart(instance, Role.SPEEDRUNNER);
    }

    /**
     * Starts one side's headstart countdown. A headstart configured for a
     * side holds the OPPOSITE side: a hunter headstart freezes speedrunners
     * so the hunters get a head start. Held players stay in spectator mode
     * until the delay expires, then are teleported back to their recorded
     * spawnpoints and restored to survival.
     */
    private void beginHeadstart(GameInstance instance, Role role) {
        HeadstartState state = instance.headstart(role);
        if (!state.armed() || state.task() != null) {
            return;
        }
        Role held = role.opposite();
        // Each held player's current location is recorded so endHeadstart()
        // can return them to their spawnpoint even if they flew elsewhere,
        // including across dimensions since the location carries its world.
        state.returnPoints().clear();
        for (Player player : store.onlineActivePlayers(instance)) {
            if (playerStates.role(player) == held) {
                state.returnPoints().put(player.getUniqueId(), player.getLocation());
                player.setGameMode(GameMode.SPECTATOR);
            }
        }
        messaging.sendToInstance(instance, "manhunt.headstart-active",
                Map.of("seconds", String.valueOf(state.remaining()), "role", messages.roleName(held)));
        long headstartMatchId = instance.matchId();
        state.setTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (store.instance(headstartMatchId).orElse(null) != instance || !instance.active()) {
                cancelHeadstartTask(state);
                return;
            }
            state.setRemaining(state.remaining() - 1);
            if (state.remaining() <= 0) {
                endHeadstart(instance, role);
            } else if (state.remaining() <= 5) {
                messaging.sendToInstance(instance, "manhunt.headstart-ending",
                        Map.of("seconds", String.valueOf(state.remaining()),
                                "role", messages.roleName(role.opposite())));
                messaging.playInstanceSound(instance, "game.autostart-countdown");
            }
        }, 20L, 20L));
    }

    /**
     * Ends one side's headstart, returning held players to their recorded
     * spawnpoints and restoring them to survival mode.
     */
    private void endHeadstart(GameInstance instance, Role role) {
        HeadstartState state = instance.headstart(role);
        cancelHeadstartTask(state);
        state.setArmed(false);
        Role held = role.opposite();
        for (Player player : store.onlineActivePlayers(instance)) {
            if (playerStates.role(player) == held) {
                Location returnPoint = state.returnPoints().remove(player.getUniqueId());
                if (returnPoint != null && returnPoint.getWorld() != null) {
                    player.teleport(returnPoint);
                }
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
        state.returnPoints().clear();
        messaging.sendToInstance(instance, "manhunt.headstart-ended", Map.of("role", messages.roleName(held)));
        messaging.playInstanceNeutral(instance);
    }

    /**
     * Drops both headstart holds, restoring held players to survival so a
     * match ending mid-headstart never strands them in spectator.
     */
    void cancelHeadstarts(GameInstance instance) {
        for (Role role : List.of(Role.HUNTER, Role.SPEEDRUNNER)) {
            HeadstartState state = instance.headstart(role);
            cancelHeadstartTask(state);
            state.setArmed(false);
            state.returnPoints().clear();
            Role held = role.opposite();
            for (Player player : store.onlineActivePlayers(instance)) {
                if (playerStates.role(player) == held && player.getGameMode() == GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
        }
    }

    private void cancelHeadstartTask(HeadstartState state) {
        if (state.task() != null) {
            state.task().cancel();
            state.setTask(null);
        }
    }

    void scheduleWaitingReminder(GameInstance instance) {
        instance.setWaitingStartTime(System.currentTimeMillis());
        int configured = instance.waitingDelayConfigured();
        if (configured > 0) {
            scheduleFiniteWaitingReminders(instance, configured);
        } else if (!scheduleIndefiniteWaitingReminders(instance)) {
            return;
        }

        // schedule expiry task which ends the waiting period if no damage occurs
        if (configured > 0) {
            scheduleWaitingExpiry(instance, configured);
        }
    }

    /** Broadcasts the three finite-delay reminders at the delay and two slices. */
    private void scheduleFiniteWaitingReminders(GameInstance instance, int configured) {
        // Finite delay: broadcast exactly three reminders at the delay and
        // two equally-sized slices (e.g. 30s -> 30, 20, 10).
        int slice = WaitingReminder.sliceSeconds(configured);
        List<Integer> checkpoints = List.of(configured,
                Math.max(1, configured - slice),
                Math.max(1, configured - 2 * slice));
        messaging.sendToInstance(instance, "manhunt.waiting-for-damage",
                Map.of("seconds", String.valueOf(configured)));
        instance.setWaitingReminderTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (store.instance(instance.matchId()).orElse(null) != instance || instance.begun()) {
                return;
            }
            long elapsedMillis = System.currentTimeMillis() - instance.waitingStartTime();
            int remaining = (int) Math.round(configured - elapsedMillis / 1000.0);
            if (remaining > 0 && checkpoints.contains(remaining)) {
                messaging.sendToInstance(instance, "manhunt.waiting-for-damage",
                        Map.of("seconds", String.valueOf(remaining)));
            }
        }, 20L, 20L));
    }

    /** Schedules indefinite-waiting reminders. Returns false when reminders are disabled. */
    private boolean scheduleIndefiniteWaitingReminders(GameInstance instance) {
        // Indefinite waiting (-1): use the configured reminder interval and
        // never schedule an expiry.
        double interval = configService.getFloat("match.start-reminder-interval", 10.0f);
        if (interval == -1.0) {
            return false;
        }
        long delay = Math.max(1L, Math.round(interval * 20.0));
        messaging.sendToInstance(instance, "manhunt.waiting-for-damage-indefinite", Map.of());
        instance.setWaitingReminderTask(Bukkit.getScheduler().runTaskTimer(plugin,
                () -> {
                    if (store.instance(instance.matchId()).orElse(null) == instance && !instance.begun()) {
                        messaging.sendToInstance(instance, "manhunt.waiting-for-damage-indefinite", Map.of());
                    }
                }, delay, delay));
        return true;
    }

    /** Schedules the expiry task that cancels or force-starts the waiting match. */
    private void scheduleWaitingExpiry(GameInstance instance, int configured) {
        long expiryTicks = Math.max(1L, Math.round(configured * 20.0));
        instance.setWaitingExpiryTask(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // only cancel if still active and game hasn't begun and match unchanged
            if (store.instance(instance.matchId()).orElse(null) == instance
                    && instance.active() && !instance.begun()) {
                if (instance.waitingReminderTask() != null) {
                    instance.waitingReminderTask().cancel();
                    instance.setWaitingReminderTask(null);
                }
                boolean forceStart = plugin.getConfig()
                        .getString("settings.start-on-speedrunner-damage.on-expire", "CANCEL")
                        .equals("FORCE_START");
                if (forceStart) {
                    messaging.sendToInstance(instance, WaitingReminder.expiryMessageKey(true), Map.of());
                } else {
                    messaging.sendToInstance(instance, WaitingReminder.expiryMessageKey(false),
                            Map.of("seconds", String.valueOf(configured)));
                }
                // end match as cancelled if configured
                if (!forceStart) {
                    expireWaitingMatch(instance);
                } else {
                    // force start the game
                    control.beginGame(instance);
                }
            }
        }, expiryTicks));
    }

    /** Aborts a match whose pre-start wait expired, without saving stats. */
    private void expireWaitingMatch(GameInstance instance) {
        // do not save stats
        stats.clearMatch(instance.matchId());
        stateCommands.cancelIntervalModifiers(instance.matchId());
        stateCommands.runConsoleCleanup();
        stateCommands.runPlayerCleanup(store.onlineActivePlayers(instance));
        List<Player> assigned = store.onlineAssignedPlayers(instance);
        control.teardownNow(instance);
        if (configService.getBoolean("settings.invulnerability.on-game-end.enabled", true)) {
            assigned.forEach(p -> p.setInvulnerable(true));
        }
    }

    void cancelWaitingTasks(GameInstance instance) {
        if (instance.waitingReminderTask() != null) {
            instance.waitingReminderTask().cancel();
            instance.setWaitingReminderTask(null);
        }
        if (instance.waitingExpiryTask() != null) {
            instance.waitingExpiryTask().cancel();
            instance.setWaitingExpiryTask(null);
        }
    }
}
