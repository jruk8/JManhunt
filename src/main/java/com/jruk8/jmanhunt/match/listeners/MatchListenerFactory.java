package com.jruk8.jmanhunt.match.listeners;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.MatchConfig;
import com.jruk8.jmanhunt.config.PlayerSettings;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.GameWiring;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import java.util.Map;
import java.util.UUID;
import org.bukkit.scheduler.BukkitTask;

/**
 * Builds the connection and combat listeners from the game wiring.
 * GameManager delegates here so listener assembly lives next to the
 * listeners.
 */
public final class MatchListenerFactory {
    private final GameManager game;
    private final GameWiring.GameServices services;
    private final GameWiring.GameReads reads;
    private final GameWiring.GameTexts texts;
    private final GameWiring.GameEdge edge;

    public MatchListenerFactory(GameManager game, GameWiring.GameServices services,
            GameWiring.GameReads reads, GameWiring.GameTexts texts,
            GameWiring.GameEdge edge) {
        this.game = game;
        this.services = services;
        this.reads = reads;
        this.texts = texts;
        this.edge = edge;
    }

    /** Connection listener with typed player and disconnect sections. */
    public PlayerConnectionListener connectionListener(PlayerSettings players,
            MatchConfig.DisconnectHandling handling, PlayerRespawnListener respawn,
            SpeedrunnerDisconnectTracker disconnects,
            Map<UUID, BukkitTask> disconnectTasks, CompassManager compass,
            GameMessages gameTexts) {
        return new PlayerConnectionListener(
                new PlayerConnectionListener.ConnectReads(services.playerStates(), edge.fakes(),
                        texts.messages(), gameTexts),
                new PlayerConnectionListener.ConnectMatch(game, reads.lobbies(), compass, disconnects,
                        disconnectTasks),
                new PlayerConnectionListener.ConnectWorld(reads.worldEngine().teleportService(),
                        reads.worldEngine()),
                new PlayerConnectionListener.ConnectConfig(players, handling),
                new PlayerConnectionListener.ConnectEdge(edge.roleTeams(), edge.tasks(), respawn));
    }

    /** Combat listener with the typed player section. */
    public PlayerCombatListener combatListener(PlayerSettings players,
            PlayerRespawnListener respawn, SpeedrunnerDisconnectTracker disconnects,
            Map<UUID, BukkitTask> disconnectTasks, CompassManager compass,
            GameMessages gameTexts) {
        return new PlayerCombatListener(
                new PlayerCombatListener.CombatReads(services.playerStates(), edge.fakes(),
                        players, gameTexts, texts.messages(), texts.manhunt()),
                new PlayerCombatListener.CombatMatch(game, services.stats(), reads.winConditionEngine(),
                        disconnects, disconnectTasks),
                new PlayerCombatListener.CombatWorld(compass, reads.lobbies(), reads.worldEngine(), respawn),
                new PlayerCombatListener.CombatEdge(edge.spawnCamp(), edge.roleTeams(),
                        edge.log(), edge.lobbyConfig()),
                edge.tasks());
    }
}
