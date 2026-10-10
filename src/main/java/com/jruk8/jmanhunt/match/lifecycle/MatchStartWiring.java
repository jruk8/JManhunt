package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.config.MatchSettingsFacade;
import com.jruk8.jmanhunt.lobby.config.PlayersSettingsFacade;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.match.autostart.AutostartService;
import com.jruk8.jmanhunt.match.listeners.PlayerRespawnListener;
import com.jruk8.jmanhunt.match.prestart.PrestartService;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.WorldEngineService;
import java.util.function.Supplier;

/** Dependency bundles behind {@link MatchStartService}. */
public final class MatchStartWiring {
    private MatchStartWiring() {
    }

    /** Match/player settings, config service, engine settings, and logger. */
    public record StartReads(MatchSettingsFacade match, PlayersSettingsFacade players,
            ConfigService config,
            com.jruk8.jmanhunt.config.WorldEngineConfig engineSettings, JManhuntLogger log) {
    }

    /** States, compass, stats, commands, engine, lobbies, store, and phases. */
    public record StartMatch(PlayerStateStore playerStates, CompassManager compass,
            StatsManager stats, GameStateCommandManager stateCommands,
            WorldEngineService worldEngine, LobbyService lobbies, MatchStore store,
            TimeLimitService timeLimits, PrestartService prestart,
            AutostartService autostart) {
    }

    /** Fakes, role teams, respawn listener, sounds, and scheduler. */
    public record StartEdge(FakeSpectatorService fakes, RoleTeamService roleTeams,
            Supplier<PlayerRespawnListener> respawn, SoundService sounds, TaskScheduler tasks) {
    }

    /** Message bus, game/manhunt texts, and match messaging. */
    public record StartTexts(MessageService messages, GameMessages game,
            ManhuntMessages manhunt, MatchMessaging messaging) {
    }
}
