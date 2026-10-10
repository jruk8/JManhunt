package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.core.JManhuntPlaceholders;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.match.listeners.PlayerRespawnListener;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.message.WinconMessages;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpawnCampService;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.WorldEngineService;
import java.util.function.Supplier;

/** Dependency bundles behind {@link GameManager}. */
public final class GameWiring {
    private GameWiring() {
    }

    /** States, compass, and stats. */
    public record GameServices(PlayerStateStore playerStates, CompassManager compass,
            StatsManager stats) {
    }

    /** Modifier reads, engine, win engine, and lobbies. */
    public record GameReads(ConfigService configService, WorldEngineService worldEngine,
            WinConditionEngine winConditionEngine, LobbyService lobbies) {
    }

    /** Message bus, texts, and sounds. */
    public record GameTexts(MessageService messages, ManhuntMessages manhunt,
            GameMessages gameTexts, WinconMessages wincon, SoundService sounds) {
    }

    /** Plugin-owned edges, narrowed. */
    public record GameEdge(EngineStateRepository engineStates, FakeSpectatorService fakes,
            JManhuntLogger log, OverrideService overrides, JManhuntPlaceholders placeholders,
            Supplier<PlayerRespawnListener> respawn, RoleTeamService roleTeams,
            SpawnCampService spawnCamp, LobbyConfig lobbyConfig, TaskScheduler tasks,
            Supplier<JManhuntConfig> configRoot) {
    }

    /**
     * Live-applies config toggles: autostart, world-engine reloads,
     * lobby collisions, and name colors refresh without restart.
     */
    public static void subscribeSettingChanges(ConfigService config, LobbyService lobbies,
            RoleTeamService roleTeams, WorldEngineService worldEngine, Runnable autostart) {
        config.onChange("settings.match.autostart.enabled",
                (oldValue, newValue) -> autostart.run());
        config.onChange("world-engine.enabled", (oldValue, newValue) -> worldEngine.onReload());
        // Structure datapacks refresh exactly like the world-engine datapack:
        // toggling in-game applies or removes the files immediately instead of
        // waiting for a restart.
        config.onChange("settings.match.game-boosts.nether-structures.enabled",
                (oldValue, newValue) -> worldEngine.onReload());
        config.onChange("settings.match.game-boosts.overworld-structures.enabled",
                (oldValue, newValue) -> worldEngine.onReload());
        config.onChange(LobbyService.COLLISIONS_PATH,
                (oldValue, newValue) -> lobbies.reapplyCollisions());
        config.onChange("settings.players.name-colors.enabled",
                (oldValue, newValue) -> roleTeams.applyColors());
    }
}
