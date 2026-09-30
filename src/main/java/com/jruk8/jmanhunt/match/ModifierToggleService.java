package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigService;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Mid-match modifier toggle reconciliation: newly effective modifiers
 * fire ON_START, newly ineffective ones clean up. With
 * prevent-duplicate-toggle on (the default), each runs at most once
 * per match; with it off, every toggle runs. The command-running half
 * lives in GameStateCommandManager behind Commands.
 */
public final class ModifierToggleService {

    /** Command-running half of toggle sync. */
    public interface Commands {
        void fireBehavior(String name, int index, long matchId);
        void cleanModifier(String name, long matchId, List<Player> roster);
        boolean afterPrestart(String name, int index);
    }

    private final JManhuntPlugin plugin;
    private final ConfigService configService;
    private final GameManager game;
    private final IntervalDispatcher intervals;
    private final Commands commands;

    public ModifierToggleService(JManhuntPlugin plugin, ConfigService configService,
            GameManager game, IntervalDispatcher intervals, Commands commands) {
        this.plugin = plugin;
        this.configService = configService;
        this.game = game;
        this.intervals = intervals;
        this.commands = commands;
    }

    /**
     * Reconciles live matches with toggled modifiers. Newly effective
     * modifiers activate: ON_START fires honoring delays and INTERVAL
     * chains restart from fresh config. Newly ineffective ones run
     * cleanup; their interval tasks self-cancel at next firing. With
     * prevent-duplicate-toggle on, each modifier fires and cleans at
     * most once per match, so repeat toggles grant nothing; with it
     * off, every toggle runs. Ending matches are skipped: teardown
     * owns their end state.
     */
    public void syncModifierToggles(Collection<String> names) {
        for (GameInstance instance : game.liveInstances()) {
            if (instance.ending()) {
                continue;
            }
            long matchId = instance.matchId();
            Integer lobby = game.lobbyOf(matchId);
            boolean dedup = plugin.overrides().getBoolean(lobby,
                    "advanced.misc.modifier-editor.prevent-duplicate-toggle", true);
            List<String> activating = new ArrayList<>();
            for (String name : names) {
                if (plugin.overrides().modifierEnabled(lobby, name)) {
                    activating.add(name);
                } else if (!dedup || instance.markModifierCleaned(name)) {
                    runModifierCleanup(name, matchId);
                } else {
                    warnToggleRepeat(name, "cleaned");
                }
            }
            if (!instance.begun()) {
                for (String name : activating) {
                    if (!dedup || instance.markModifierStarted(name)) {
                        firePreStartModifier(name, matchId);
                    } else {
                        warnToggleRepeat(name, "enabled");
                    }
                }
                continue;
            }
            if (activating.stream().anyMatch(this::hasIntervalBehavior)) {
                intervals.cancelIntervalChains(matchId);
                intervals.scheduleIntervalModifiers(matchId);
            }
            for (String name : activating) {
                if (!dedup || instance.markModifierStarted(name)) {
                    fireModifierStart(name, matchId);
                } else {
                    warnToggleRepeat(name, "enabled");
                }
            }
        }
    }

    /** Warns that a repeat mid-match toggle was skipped by the once-only guard. */
    private void warnToggleRepeat(String name, String action) {
        plugin.logger().warning("Modifier '" + name + "' already " + action
                + " this match; ignoring repeat toggle.");
    }

    /** Cleanup commands for one modifier against a live match roster. */
    private void runModifierCleanup(String name, long matchId) {
        commands.cleanModifier(name, matchId, game.onlineParticipants(matchId));
    }

    /** All ON_START behaviors of one modifier; the prestart already passed. */
    private void fireModifierStart(String name, long matchId) {
        for (int index : configService.behaviorIndexes(name)) {
            if (ModifierTriggers.runsOn(configService.runsOn(name, index), "ON_START")) {
                commands.fireBehavior(name, index, matchId);
            }
        }
    }

    /** Non-deferred ON_START behaviors, mirroring match-start dispatch. */
    private void firePreStartModifier(String name, long matchId) {
        for (int index : configService.behaviorIndexes(name)) {
            if (ModifierTriggers.runsOn(configService.runsOn(name, index), "ON_START")
                    && !commands.afterPrestart(name, index)) {
                commands.fireBehavior(name, index, matchId);
            }
        }
    }

    /** True when any behavior of the modifier runs on a live INTERVAL. */
    private boolean hasIntervalBehavior(String name) {
        for (int index : configService.behaviorIndexes(name)) {
            if (ModifierTriggers.runsOn(configService.runsOn(name, index), "INTERVAL")
                    && configService.intervalSeconds(name, index) >= 0) {
                return true;
            }
        }
        return false;
    }
}
