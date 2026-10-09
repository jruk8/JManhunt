package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.config.MatchConfig;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.List;

/**
 * Default match state per phase: inventory wipes, gamemodes, vanilla
 * gamerules, and daytime, ahead of custom start commands and equipment.
 */
public final class MatchDefaultsService {
    /** Role plus fake-spectator state. */
    public record DefaultsStates(PlayerStateStore states, FakeSpectatorService fakes) {
    }

    private final OverrideService overrides;
    private final JManhuntLogger log;
    private final PlayerWipeService wipes;
    private final DefaultsStates states;

    public MatchDefaultsService(OverrideService overrides, JManhuntLogger log,
            PlayerWipeService wipes, DefaultsStates states) {
        this.overrides = overrides;
        this.log = log;
        this.wipes = wipes;
        this.states = states;
    }

    public void runDefault(String phase, List<Player> participants, List<Player> lobbySpectators,
            int lobbyId, boolean lastMatch) {
        if (!overrides.getBoolean(lobbyId, "advanced.advanced-match-controls.game-rules.enabled", true)) {
            return;
        }
        List<String> rules = overrides.getStringList(lobbyId,
                MatchConfig.GameRules.RULES_PATH);
        // Fake mode unwinds first: disabling restores held snapshots,
        // and the wipe below must clear the restored gear, not run
        // before the restore hands it back.
        applyDefaultGamemodes(phase, participants, lobbySpectators);
        if (wipes.endWipeEnabled(lobbyId)) {
            participants.forEach(wipes::resetPlayer);
        }
        applyWorldRules(Bukkit.getWorlds(), phase, lastMatch, rules);
        if (MatchConfig.GameRules.isRuleEnabled(rules, "SET_DAYTIME")) {
            Bukkit.getWorlds().forEach(this::setDaytime);
        }
    }

    /** Vanilla gamerule applications for one phase. */
    private void applyWorldRules(List<World> worlds, String phase,
            boolean lastMatch, List<String> rules) {
        boolean disableLocatorBar =
                MatchConfig.GameRules.isRuleEnabled(rules, "DISABLE_LOCATOR_BAR");
        worlds.forEach(world -> world.setGameRule(GameRules.LOCATOR_BAR, !disableLocatorBar));
        // Disable phantom spawning while a match runs and restore it when the
        // match ends. The gamerule is re-enabled on the end phase.
        boolean disablePhantoms =
                MatchConfig.GameRules.isRuleEnabled(rules, "DISABLE_PHANTOMS");
        worlds.forEach(world -> world.setGameRule(GameRules.SPAWN_PHANTOMS,
                gameruleRestored(phase, lastMatch, disablePhantoms)));
        worlds.forEach(world -> world.setGameRule(GameRules.IMMEDIATE_RESPAWN,
                MatchConfig.GameRules.isRuleEnabled(rules, "SET_RESPAWN_IMMEDIATE")));
        // Prevent spectators from generating chunks while the match is active.
        // This is the native gamerule equivalent of the old spectator chunk
        // generation toggle and avoids lag from spectators exploring.
        worlds.forEach(world -> world.setGameRule(GameRules.SPECTATORS_GENERATE_CHUNKS, false));
        // Pillager patrols never spawn while a match runs; restored when the last match ends.
        applySpawnGamerule(worlds, phase, lastMatch,
                MatchConfig.GameRules.isRuleEnabled(rules, "DISABLE_PILLAGER_PATROLS"),
                GameRules.SPAWN_PATROLS);
        // Wandering traders never spawn while a match runs; restored when the last match ends.
        applySpawnGamerule(worlds, phase, lastMatch,
                MatchConfig.GameRules.isRuleEnabled(rules, "DISABLE_WANDERING_TRADER"),
                GameRules.SPAWN_WANDERING_TRADERS);
    }

    /**
     * Default modes for a phase: participants to survival, and lobby
     * watchers back to survival on end. Nobody is teleported here:
     * match travel belongs to the cell teleports.
     */
    private void applyDefaultGamemodes(String phase, List<Player> participants,
            List<Player> lobbySpectators) {
        for (Player player : participants) {
            states.fakes().disable(player);
        }
        // AFK players are always left alone; everyone else keeps their
        // mode on start and drops fake spectator on end.
        for (Player player : lobbySpectators) {
            if (states.states().role(player) == Role.AFK) {
                continue; // AFK players are left alone
            }
            if (!phase.equals("start")) {
                states.fakes().disable(player);
            }
        }
    }

    private void setDaytime(World world) {
        try {
            world.setTime(0L);
            // Clear any ongoing rain/storm so a fresh match starts with clear
            // skies, matching the behaviour of a newly generated world.
            world.setStorm(false);
            world.setWeatherDuration(0);
        } catch (IllegalArgumentException exception) {
            log.fine("Skipping daytime reset in world without a world clock: " + world.getName());
        }
    }

    /**
     * True when a match-paused gamerule is back on: the end phase of the
     * last match, or any phase when its toggle is off. Pure for tests.
     */
    static boolean gameruleRestored(String phase, boolean lastMatch, boolean toggleEnabled) {
        return (phase.equals("end") && lastMatch) || !toggleEnabled;
    }

    /** Pauses a spawn gamerule while a match runs, restoring it after. */
    private void applySpawnGamerule(List<World> worlds, String phase,
            boolean lastMatch, boolean disabled, GameRule<Boolean> rule) {
        worlds.forEach(world -> world.setGameRule(rule,
                gameruleRestored(phase, lastMatch, disabled)));
    }
}
