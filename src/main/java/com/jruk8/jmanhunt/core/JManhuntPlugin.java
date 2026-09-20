package com.jruk8.jmanhunt.core;

import com.jruk8.jmanhunt.command.ManhuntCommand;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.compass.CompassProtectionListener;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.config.YamlFileUpdater;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.RolePadService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.GameplayListener;
import com.jruk8.jmanhunt.match.WinConditionEngine;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpawnCampService;
import com.jruk8.jmanhunt.stats.StatisticsRepository;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.api.JManhuntApi;
import com.jruk8.jmanhunt.api.JManhuntApiImpl;
import com.jruk8.jmanhunt.config.SettingsListener;
import com.jruk8.jmanhunt.loot.PiglinBarterListener;
import com.jruk8.jmanhunt.world.PortalRouter;
import com.jruk8.jmanhunt.world.WorldEngineService;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private RoleTeamService roleTeams;
    private SpawnCampService spawnCamp;
    private final List<SettingsListener> settings = new ArrayList<>();

    @Override
    public void onEnable() {
        messages = new MessageService();
        spawnCamp = new SpawnCampService(this, messages);
        debugService = new DebugService();
        logger = new JManhuntLogger(getLogger(), debugService, messages, BukkitDebugSink.INSTANCE);
        lobbyService = new LobbyService(this);
        reload();
        debugService.resetToDefaults(getConfig().getBoolean("debug.enabled", false));

        playerStates = new PlayerStateStore();
        roleTeams = new RoleTeamService(playerStates);
        setupStatistics();
        setupEngineState();
        stats = new StatsManager(this, messages, statistics);
        setupPlaceholderApi();

        compass = new CompassManager(this, messages, playerStates,
                new NamespacedKey(this, "hunters_compass"));
        configService = new ConfigService(this);
        sounds = new SoundService(this, configService);
        worldEngine = new WorldEngineService(this, configService, engineState);
        winConditionEngine = new WinConditionEngine(getConfig());
        game = new GameManager(
                this, messages, sounds, playerStates, compass, stats,
                configService, worldEngine, winConditionEngine, lobbyService);
        compass.setGameManager(game);
        worldEngine.setMatchRunningSupplier(game::isActive);
        setupListeners();

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
                this, messages, configService, sounds, playerStates, game, worldEngine, debugService,
                lobbyService);
        getCommand("manhunt").setExecutor(command);
        getCommand("manhunt").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new CompassProtectionListener(this, compass, game), this);
        getServer().getPluginManager().registerEvents(new PortalRouter(this, game, worldEngine), this);
        getServer().getPluginManager().registerEvents(new GameplayListener(
                this, playerStates, game, messages, configService, sounds,
                compass, stats, worldEngine, winConditionEngine, lobbyService, worldEngine), this);
        getServer().getPluginManager().registerEvents(piglinBarter, this);
        getServer().getPluginManager().registerEvents(new RolePadService(
                this, lobbyService, playerStates, game, messages, sounds,
                worldEngine::lobbyWorldName), this);
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
    }

    @Override public void onDisable() {
        Bukkit.getServicesManager().unregister(JManhuntApi.class);
        if (expansion != null) {
            expansion.unregister();
        }
        if (stats != null) {
            stats.flush();
        }
        if (statistics != null) {
            statistics.close();
        }
        if (engineState != null) {
            engineState.close();
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
