package com.jruk8.jmanhunt;

import com.jruk8.jmanhunt.command.ManhuntCommand;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.compass.CompassProtectionListener;
import com.jruk8.jmanhunt.core.BukkitDebugSink;
import com.jruk8.jmanhunt.core.DebugService;
import com.jruk8.jmanhunt.core.JManhuntExpansion;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.core.MetricsBootstrap;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.config.YamlFileUpdater;
import com.jruk8.jmanhunt.lobby.bounds.LobbyBoundsService;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.LobbyConfigRegistrar;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.RolePadService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.listeners.PlayerCombatListener;
import com.jruk8.jmanhunt.match.listeners.PlayerConnectionListener;
import com.jruk8.jmanhunt.match.listeners.PlayerMovementListener;
import com.jruk8.jmanhunt.match.listeners.PlayerRespawnListener;
import com.jruk8.jmanhunt.match.WinConditionEngine;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpawnCampService;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import com.jruk8.jmanhunt.stats.StatisticsRepository;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.tutorial.TutorialChatListener;
import com.jruk8.jmanhunt.tutorial.TutorialConfigRegistrar;
import com.jruk8.jmanhunt.tutorial.TutorialService;
import com.jruk8.jmanhunt.tutorial.jmanhunt.JManhuntTutorialCommands;
import com.jruk8.jmanhunt.tutorial.jmanhunt.JManhuntTutorialLogger;
import com.jruk8.jmanhunt.tutorial.jmanhunt.JManhuntTutorialMessenger;
import com.jruk8.jmanhunt.tutorial.jmanhunt.JManhuntTutorialSounds;
import com.jruk8.jmanhunt.api.JManhuntApi;
import com.jruk8.jmanhunt.api.JManhuntApiImpl;
import com.jruk8.jmanhunt.config.SettingsListener;
import com.jruk8.jmanhunt.loot.PiglinBarterListener;
import com.jruk8.jmanhunt.world.teleport.PortalRouter;
import com.jruk8.jmanhunt.world.WorldEngineService;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class JManhuntPlugin extends JavaPlugin {
    private static final int CONFIG_VERSION = 5;
    private static final int MESSAGES_VERSION = 8;
    /**
     * Relocated config paths, applied on reload. Every key must live under a
     * real category so the in-game config command can drill into it.
     */
    private static final Map<String, String> CONFIG_MOVES = Map.of(
            "database", "statistics",
            "game-end-delay", "match.end-delay",
            "start-reminder-interval", "match.start-reminder-interval",
            "end-statistics", "match.end-statistics",
            "disconnect-handling", "match.disconnect-handling");
    private MessageService messages;
    private SoundService sounds;
    private PlayerStateStore playerStates;
    private StatsManager stats;
    private CompassManager compass;
    private GameManager game;
    private StatisticsRepository statistics;
    private EngineStateRepository engineState;
    private JManhuntExpansion expansion;
    private ConfigService configService;
    private WorldEngineService worldEngine;
    private WinConditionEngine winConditionEngine;
    private JManhuntLogger logger;
    private DebugService debugService;
    private LobbyService lobbyService;
    private LobbyConfigRegistrar lobbyConfigs;
    private TutorialConfigRegistrar tutorialConfigs;
    private TutorialService tutorialService;
    private RoleTeamService roleTeams;
    private SpawnCampService spawnCamp;
    private final List<SettingsListener> settings = new ArrayList<>();

    @Override
    public void onEnable() {
        bootstrapCore();
        bootstrapServices();
        bootstrapGame();

        Bukkit.getServicesManager().register(JManhuntApi.class,
                new JManhuntApiImpl(game, playerStates, lobbyService), this, ServicePriority.High);

        setupScheduling();
        reload(); // reload again to ensure that settings are loaded after the game manager is initialized
        for (SettingsListener listener : settings) {
            listener.onStart();
        }

        // Initialize bStats unless anonymous statistics are disabled. This is
        // intentionally read once at startup: the toggle lives outside the
        // in-game configuration command and takes effect on server restart.
        if (getConfig().getBoolean("send-anonymous-statistics", true)) {
            var metricsBootstrap = new MetricsBootstrap(this);
            metricsBootstrap.register();
        }
        // Scoreboard teams do not survive restarts: recreate them and repair
        // every online player's membership from their current role.
        roleTeams.syncAll();
        game.validateLobbyWorldName();
    }

    /** Creates messaging, logging, lobby, and tutorial configuration services. */
    private void bootstrapCore() {
        messages = new MessageService();
        spawnCamp = new SpawnCampService(this, messages);
        debugService = new DebugService();
        logger = new JManhuntLogger(getLogger(), debugService, messages, BukkitDebugSink.INSTANCE);
        lobbyService = new LobbyService(this);
        lobbyConfigs = new LobbyConfigRegistrar(this);
        lobbyConfigs.register();
        tutorialConfigs = new TutorialConfigRegistrar(this);
        tutorialConfigs.register();
        reload();
        debugService.resetToDefaults(getConfig().getBoolean("debug.enabled", false));
    }

    /** Creates player, stats, config, sound, tutorial, compass, and world services. */
    private void bootstrapServices() {
        playerStates = new PlayerStateStore();
        roleTeams = new RoleTeamService(playerStates);
        setupStatistics();
        setupEngineState();
        stats = new StatsManager(this, messages, statistics);
        setupPlaceholderApi();

        configService = new ConfigService(this);
        sounds = new SoundService(this, configService);
        tutorialService = new TutorialService(tutorialConfigs.getTutorialConfig(),
                new JManhuntTutorialMessenger(messages),
                new JManhuntTutorialSounds(tutorialConfigs.getTutorialConfig(), sounds),
                new JManhuntTutorialCommands(),
                new JManhuntTutorialLogger(logger));
        compass = new CompassManager(this, messages, sounds, playerStates,
                new NamespacedKey(this, "hunters_compass"));
        worldEngine = new WorldEngineService(this, messages, configService, engineState);
        worldEngine.deleteOrphanedEndCells();
        checkCrashFlag();
    }

    /**
     * Reads the crash flag left by the previous run: a set flag means the
     * server crashed (disable never ran), so stale end reservations are
     * cleared after orphan deletion already consumed them. The flag is
     * then set for this run and cleared again on disable.
     */
    private void checkCrashFlag() {
        if (engineState == null) {
            return;
        }
        try {
            if (engineState.getCrashFlag()) {
                logger().warning("JManhunt did not shut down cleanly last run; "
                        + "clearing stale match reservations from the engine database.");
                engineState.clearEndReservations();
            }
            engineState.setCrashFlag(true);
        } catch (Exception exception) {
            logger().warning("Could not check the crash flag: " + exception.getMessage());
        }
    }

    /** Creates the game manager and wires it to the compass, world engine, and listeners. */
    private void bootstrapGame() {
        winConditionEngine = new WinConditionEngine(getConfig());
        game = new GameManager(
                this, messages, sounds, playerStates, compass, stats,
                configService, worldEngine, winConditionEngine, lobbyService);
        compass.setGameManager(game);
        worldEngine.setMatchRunningSupplier(game::isActive);
        setupListeners();
    }

    /** Scoreboard-team mirror of manhunt roles. */
    public RoleTeamService roleTeams() {
        return roleTeams;
    }

    /** Rolling anti-spawn-camp guard. */
    public SpawnCampService spawnCamp() {
        return spawnCamp;
    }

    private void setupStatistics() {
        if (!getConfig().getBoolean("statistics.enabled", true)) {
            return;
        }
        try {
            statistics = StatisticsRepository.open(this);
            logger().info("Career statistics database initialized.");
        } catch (Exception exception) {
            logger().severe(
                    "Career statistics are disabled because the database could not be initialized: "
                            + exception.getMessage());
        }
    }

    private void setupEngineState() {
        try {
            engineState = EngineStateRepository.open(getDataFolder());
            logger().info("Engine state database initialized.");
        } catch (Exception exception) {
            logger().severe(
                    "World-engine cell allocation will fall back to memory because "
                            + "the engine database could not be initialized: "
                            + exception.getMessage());
        }
    }

    private void setupPlaceholderApi() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new JManhuntExpansion(this, stats, messages);
            expansion.register();
            logger().info("Hooked into PlaceholderAPI as the %jmanhunt_<placeholder>% expansion.");
        } else {
            logger().warning(
                    "PlaceholderAPI is not installed; JManhunt placeholders will not be hooked into.");
        }
    }

    private void setupListeners() {
        var piglinBarter = new PiglinBarterListener(this, game);
        settings.add(worldEngine);
        settings.add(piglinBarter);

        ManhuntCommand command = new ManhuntCommand(
                this, messages, configService, sounds, playerStates, game, worldEngine.teleportService(),
                debugService, lobbyService);
        getCommand("manhunt").setExecutor(command);
        getCommand("manhunt").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new CompassProtectionListener(this, compass, game), this);
        getServer().getPluginManager().registerEvents(new PortalRouter(this, game, worldEngine), this);
        PlayerRespawnListener respawn = new PlayerRespawnListener(this, playerStates, game, compass);
        SpeedrunnerDisconnectTracker disconnects = new SpeedrunnerDisconnectTracker();
        Map<UUID, BukkitTask> disconnectTasks = new HashMap<>();
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(
                this, playerStates, game, messages, configService, lobbyService,
                worldEngine.teleportService(), worldEngine, disconnects, disconnectTasks), this);
        getServer().getPluginManager().registerEvents(new PlayerCombatListener(
                this, playerStates, game, configService, compass, stats, lobbyService,
                worldEngine, winConditionEngine, respawn, disconnects, disconnectTasks), this);
        getServer().getPluginManager().registerEvents(new PlayerMovementListener(
                playerStates, game, winConditionEngine, worldEngine), this);
        getServer().getPluginManager().registerEvents(respawn, this);
        getServer().getPluginManager().registerEvents(piglinBarter, this);
        getServer().getPluginManager().registerEvents(new RolePadService(
                this, lobbyService, playerStates, game, messages, sounds,
                worldEngine::lobbyWorldName), this);
        getServer().getPluginManager().registerEvents(new LobbyBoundsService(
                this, lobbyService, playerStates, game, messages, sounds,
                worldEngine::lobbyWorldName), this);
        getServer().getPluginManager().registerEvents(new TutorialChatListener(this, tutorialService), this);
    }

    private void setupScheduling() {
        double refreshInterval = getConfig().getDouble("settings.compass.refresh-interval", 10.0);
        if (refreshInterval != -1.0) {
            long ticks = Math.max(1L, Math.round(refreshInterval * 20.0));
            Bukkit.getScheduler().runTaskTimer(this,
                    () -> compass.refreshAllCompasses(game.isActive()), ticks, ticks);
        }
        BukkitTask actionbars = Bukkit.getScheduler().runTaskTimer(this,
                () -> compass.showHeldActionbars(game.isActive()), 1L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, game::broadcastAutostartShortfalls, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, tutorialService::checkTimeouts, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, worldEngine::careTick, 20L, 20L);
    }

    @Override public void onDisable() {
        if (game != null) {
            try {
                game.shutdownMatches();
            } catch (Exception exception) {
                getLogger().warning("Error while ending matches on shutdown: " + exception.getMessage());
            }
        }
        Bukkit.getServicesManager().unregister(JManhuntApi.class);
        if (expansion != null) {
            expansion.unregister();
        }
        if (stats != null) {
            stats.flush();
        }
        clearCrashFlag();
        if (statistics != null) {
            statistics.close();
        }
        if (engineState != null) {
            engineState.close();
        }
    }

    /** Marks a clean shutdown; runs before the engine database closes. */
    private void clearCrashFlag() {
        if (engineState == null) {
            return;
        }
        try {
            engineState.setCrashFlag(false);
        } catch (Exception exception) {
            getLogger().warning("Could not clear the crash flag: " + exception.getMessage());
        }
    }

    /** Single funnel for plugin console output; never null once onEnable starts. */
    public JManhuntLogger logger() {
        return logger;
    }

    public DebugService debugService() {
        return debugService;
    }

    public LobbyService lobbyService() {
        return lobbyService;
    }

    /** Okaeri lobby teleport/bounds store, generated on first load. */
    public LobbyConfig lobbyConfig() {
        return lobbyConfigs == null ? null : lobbyConfigs.getLobbyConfig();
    }

    /** Interactive setup tutorial engine. */
    public TutorialService tutorial() {
        return tutorialService;
    }

    public void reload() {
        YamlFileUpdater.update(this, "config.yml", "config-version", CONFIG_VERSION, CONFIG_MOVES);
        reloadConfig();
        YamlFileUpdater.update(this, "messages.yml", "messages-version", MESSAGES_VERSION);
        if (!new java.io.File(getDataFolder(), "placeholders.yml").exists()) {
            saveResource("placeholders.yml", false);
        }

        if (messages == null) {
            messages = new MessageService();
        }
        messages.reload(YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(), "messages.yml")),
                getConfig().getString("text-format", "minimessage"));

        if (lobbyConfigs != null) {
            lobbyConfigs.reload();
        }

        if (tutorialConfigs != null) {
            tutorialConfigs.reload();
        }

        if (winConditionEngine != null) {
            winConditionEngine.reload(getConfig());
        }

        // Cancel any running interval modifier tasks before reloading settings
        // so stale tasks do not keep firing against a partially updated config.
        if (game != null) {
            game.stateCommands().cancelAllIntervalModifiers();
        }
        for (SettingsListener listener : settings) {
            if (!new java.io.File(getDataFolder(), listener.getDataPath()).exists()) {
                saveResource(listener.getDataPath(), false);
            }
            listener.onReload();
        }
        logger().info("JManhunt has been reloaded.");
    }

}
