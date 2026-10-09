package com.jruk8.jmanhunt.match.prestart;

import com.jruk8.jmanhunt.config.DurationFormat;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.config.MatchSettings;
import com.jruk8.jmanhunt.lobby.config.MatchSettingsFacade;
import com.jruk8.jmanhunt.lobby.config.PlayersSettingsFacade;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.stats.StatsManager;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import com.jruk8.jmanhunt.match.CountdownService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.match.lifecycle.MatchControl;
import com.jruk8.jmanhunt.match.lifecycle.MatchMessaging;
import com.jruk8.jmanhunt.match.lifecycle.MatchStore;

/**
 * Runs the pre-start phase: headstart arming and countdowns plus the
 * waiting-for-damage reminders and expiry. The start/finish transitions
 * themselves live in the start and finish services behind {@link MatchControl}.
 */
public final class PrestartService {
    /** Headstarts, facades, and modifier overrides. */
    public record PrestartConfig(MatchSettings.Headstarts headstarts, MatchSettingsFacade match,
            PlayersSettingsFacade players, OverrideService overrides) {
    }

    /** States, stats, commands, store, control, fakes, scheduler, and countdowns. */
    public record PrestartServices(PlayerStateStore playerStates, StatsManager stats,
            GameStateCommandManager stateCommands, MatchStore store, MatchControl control,
            FakeSpectatorService fakes, TaskScheduler tasks, CountdownService countdowns) {
    }

    /** Countdown key for one side's headstart. */
    private record HeadstartKey(long matchId, Role role) {
    }

    /** Countdown key for the pre-start must-hit wait. */
    private record MustHitKey(long matchId) {
    }

    private final PrestartConfig config;
    private final PrestartServices services;
    private final MessageService messages;
    private final MatchMessaging messaging;
    private final ManhuntMessages manhunt;

    public PrestartService(PrestartConfig config, PrestartServices services,
            MessageService messages, MatchMessaging messaging, ManhuntMessages manhunt) {
        this.config = config;
        this.services = services;
        this.messages = messages;
        this.messaging = messaging;
        this.manhunt = manhunt;
    }

    public void armHeadstarts(GameInstance instance) {
        armHeadstartSide(instance, Role.HUNTER, Headstart.parse(config.headstarts(), "hunter"));
        armHeadstartSide(instance, Role.SPEEDRUNNER, Headstart.parse(config.headstarts(), "speedrunner"));
    }

    private void armHeadstartSide(GameInstance instance, Role role, Headstart side) {
        HeadstartState state = instance.headstart(role);
        boolean armed = side.enabled() && side.delaySeconds() > 0;
        state.setArmed(armed);
        state.setRemaining(armed ? side.delaySeconds() : 0);
    }

    /** Starts the countdown for every armed headstart side. */
    public void beginHeadstarts(GameInstance instance) {
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
    /** True while the side's headstart countdown runs. */
    public boolean isCounting(GameInstance instance, Role role) {
        return services.countdowns().running(new HeadstartKey(instance.matchId(), role));
    }

    private void beginHeadstart(GameInstance instance, Role role) {
        HeadstartState state = instance.headstart(role);
        HeadstartKey key = new HeadstartKey(instance.matchId(), role);
        if (!state.armed() || services.countdowns().running(key)) {
            return;
        }
        Role held = role.opposite();
        // Each held player's current location is recorded so endHeadstart()
        // can return them to their spawnpoint even if they flew elsewhere,
        // including across dimensions since the location carries its world.
        state.returnPoints().clear();
        for (Player player : services.store().onlineActivePlayers(instance)) {
            if (services.playerStates().role(player) == held) {
                state.returnPoints().put(player.getUniqueId(), player.getLocation());
                services.fakes().enable(player);
            }
        }
        messaging.sendToInstance(instance, manhunt.getHeadstartActive(),
                Map.of("time", DurationFormat.format(state.remaining()),
                        "role", messages.roleName(held)));
        services.countdowns().start(key, state.remaining(),
                remaining -> tickHeadstart(instance, role, state, remaining),
                () -> endHeadstart(instance, role));
    }

    /** One headstart tick: drop dead matches, else announce ladder marks. */
    private void tickHeadstart(GameInstance instance, Role role, HeadstartState state, int remaining) {
        if (services.store().instance(instance.matchId()).orElse(null) != instance
                || !instance.active()) {
            services.countdowns().cancel(new HeadstartKey(instance.matchId(), role));
            return;
        }
        state.setRemaining(remaining);
        if (CountdownService.onLadder(remaining)) {
            messaging.sendToInstance(instance, manhunt.getHeadstartEnding(),
                    Map.of("time", DurationFormat.format(remaining),
                            "role", messages.roleName(role.opposite())));
            messaging.playInstanceSound(instance, "game.autostart-countdown");
        }
    }

    /**
     * Ends one side's headstart, returning held players to their recorded
     * spawnpoints and restoring them to survival mode.
     */
    private void endHeadstart(GameInstance instance, Role role) {
        HeadstartState state = instance.headstart(role);
        services.countdowns().cancel(new HeadstartKey(instance.matchId(), role));
        state.setArmed(false);
        Role held = role.opposite();
        for (Player player : services.store().onlineActivePlayers(instance)) {
            if (services.playerStates().role(player) == held) {
                Location returnPoint = state.returnPoints().remove(player.getUniqueId());
                if (returnPoint != null && returnPoint.getWorld() != null) {
                    player.teleport(returnPoint);
                }
                services.fakes().disable(player);
            }
        }
        state.returnPoints().clear();
        messaging.sendToInstance(instance, manhunt.getHeadstartEnded(), Map.of("role", messages.roleName(held)));
        messaging.playInstanceNeutral(instance);
    }

    /**
     * Drops both headstart holds, restoring held players to survival so a
     * match ending mid-headstart never strands them spectating.
     */
    public void cancelHeadstarts(GameInstance instance) {
        for (Role role : List.of(Role.HUNTER, Role.SPEEDRUNNER)) {
            HeadstartState state = instance.headstart(role);
            services.countdowns().cancel(new HeadstartKey(instance.matchId(), role));
            state.setArmed(false);
            state.returnPoints().clear();
            Role held = role.opposite();
            for (Player player : services.store().onlineActivePlayers(instance)) {
                if (services.playerStates().role(player) == held
                        && services.fakes().isFakeSpectator(player)) {
                    services.fakes().disable(player);
                }
            }
        }
    }

    public void scheduleWaitingReminder(GameInstance instance) {
        int configured = instance.waitingDelayConfigured();
        if (configured > 0) {
            scheduleFiniteWaitingCountdown(instance, configured);
        } else {
            scheduleIndefiniteWaitingReminders(instance);
        }
    }

    /** Finite must-hit wait: ladder ticks with expiry as the countdown end. */
    private void scheduleFiniteWaitingCountdown(GameInstance instance, int configured) {
        messaging.sendToInstance(instance, manhunt.getWaitingForDamage(),
                Map.of("time", DurationFormat.format(configured)));
        MustHitKey key = new MustHitKey(instance.matchId());
        services.countdowns().start(key, configured,
                remaining -> tickWaitingCountdown(instance, key, remaining),
                () -> expireWaitingCountdown(instance, configured));
    }

    /** One must-hit tick: drop started matches, else announce ladder marks. */
    private void tickWaitingCountdown(GameInstance instance, MustHitKey key, int remaining) {
        if (services.store().instance(instance.matchId()).orElse(null) != instance
                || instance.begun()) {
            services.countdowns().cancel(key);
            return;
        }
        if (CountdownService.onLadder(remaining)) {
            messaging.sendToInstance(instance, manhunt.getWaitingForDamage(),
                    Map.of("time", DurationFormat.format(remaining)));
        }
    }

    /** Schedules indefinite-waiting reminders. Returns false when reminders are disabled. */
    private boolean scheduleIndefiniteWaitingReminders(GameInstance instance) {
        // Indefinite waiting (-1): use the configured reminder interval and
        // never schedule an expiry.
        double interval = config.match().startReminderInterval(instance.originLobbyId());
        if (interval == -1.0) {
            return false;
        }
        long delay = Math.max(1L, Math.round(interval * 20.0));
        messaging.sendToInstance(instance, manhunt.getWaitingForDamageIndefinite(), Map.of());
        instance.setWaitingReminderTask(services.tasks().runTimer(
                () -> {
                    if (services.store().instance(instance.matchId()).orElse(null) == instance && !instance.begun()) {
                        messaging.sendToInstance(instance, manhunt.getWaitingForDamageIndefinite(), Map.of());
                    }
                }, delay, delay));
        return true;
    }

    /** Must-hit expiry: force-start or cancel once the countdown ends. */
    private void expireWaitingCountdown(GameInstance instance, int configured) {
        // only cancel if still active and game hasn't begun and match unchanged
        if (services.store().instance(instance.matchId()).orElse(null) == instance
                && instance.active() && !instance.begun()) {
            if (instance.waitingReminderTask() != null) {
                instance.waitingReminderTask().cancel();
                instance.setWaitingReminderTask(null);
            }
            boolean forceStart = config.match().startOnDamageOnExpire(instance.originLobbyId())
                    == OnExpire.FORCE_START;
            if (forceStart) {
                messaging.sendToInstance(instance, manhunt.getWaitingForDamageForceStarted(), Map.of());
            } else {
                messaging.sendToInstance(instance, manhunt.getWaitingForDamageExhausted(),
                        Map.of("time", DurationFormat.format(configured)));
            }
            // end match as cancelled if configured
            if (!forceStart) {
                expireWaitingMatch(instance);
            } else {
                // force start the game
                services.control().beginGame(instance);
            }
        }
    }

    /** Aborts a match whose pre-start wait expired, without saving services.stats(). */
    private void expireWaitingMatch(GameInstance instance) {
        // do not save stats
        services.stats().clearMatch(instance.matchId());
        services.stateCommands().cancelIntervalModifiers(instance.matchId());
        services.stateCommands().runConsoleCleanup(instance.matchId());
        services.stateCommands().runPlayerCleanup(instance.matchId(), services.store().onlineActivePlayers(instance));
        List<Player> assigned = services.store().onlineAssignedPlayers(instance);
        services.control().teardownNow(instance);
        if (config.players().invulnerabilityOnGameEnd(instance.originLobbyId())) {
            assigned.forEach(p -> p.setInvulnerable(true));
        }
    }

    public void cancelWaitingTasks(GameInstance instance) {
        if (instance.waitingReminderTask() != null) {
            instance.waitingReminderTask().cancel();
            instance.setWaitingReminderTask(null);
        }
        services.countdowns().cancel(new MustHitKey(instance.matchId()));
    }
}
