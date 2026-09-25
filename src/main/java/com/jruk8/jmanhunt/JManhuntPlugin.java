package com.jruk8.jmanhunt;

import com.jruk8.jmanhunt.command.ManhuntCommand;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.compass.CompassProtectionListener;
import com.jruk8.jmanhunt.core.BukkitDebugSink;
import com.jruk8.jmanhunt.core.DebugService;
import com.jruk8.jmanhunt.core.JManhuntExpansion;
import com.jruk8.jmanhunt.core.JManhuntPlaceholders;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.core.MetricsBootstrap;
import com.jruk8.jmanhunt.core.StartupBanner;
import com.jruk8.jmanhunt.config.ConfigRegistrar;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.config.YamlFileUpdater;
import com.jruk8.jmanhunt.lobby.bounds.LobbyBoundsService;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.LobbyConfigRegistrar;
import com.jruk8.jmanhunt.lobby.LobbyProtectionService;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.RolePadService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.listeners.PlayerCombatListener;
import com.jruk8.jmanhunt.match.listeners.PlayerConnectionListener;
import com.jruk8.jmanhunt.match.listeners.PlayerMovementListener;
import com.jruk8.jmanhunt.match.listeners.PlayerRespawnListener;
import com.jruk8.jmanhunt.match.WinConditionEngine;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesRegistrar;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifiersRegistrar;
import com.jruk8.jmanhunt.placeholders.PlaceholderConfigRegistrar;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpawnCampService;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import com.jruk8.jmanhunt.stats.StatisticsRepository;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.tutorial.TutorialChatListener;
import com.jruk8.jmanhunt.tutorial.TutorialConfigRegistrar;
import com.jruk8.jmanhunt.tutorial.TutorialService;
import com.jruk8.jmanhunt.updatechecker.UpdateCheckJoinListener;
import com.jruk8.jmanhunt.updatechecker.UpdateCheckService;
import com.jruk8.jmanhunt.updatechecker.UpdateCheckSettings;
import com.jruk8.jmanhunt.updatechecker.jmanhunt.JManhuntUpdateCheckHttp;
import com.jruk8.jmanhunt.updatechecker.jmanhunt.JManhuntUpdateCheckLogger;
import com.jruk8.jmanhunt.updatechecker.jmanhunt.JManhuntUpdateCheckNotifier;
import com.jruk8.jmanhunt.updatechecker.ports.UpdateCheckNotifier;
import com.jruk8.jmanhunt.tutorial.jmanhunt.JManhuntTutorialCommands;
import com.jruk8.jmanhunt.tutorial.jmanhunt.JManhuntTutorialLogger;
import com.jruk8.jmanhunt.tutorial.jmanhunt.JManhuntTutorialMessenger;
import com.jruk8.jmanhunt.tutorial.jmanhunt.JManhuntTutorialSounds;
import com.jruk8.jmanhunt.api.JManhuntApi;
import com.jruk8.jmanhunt.api.JManhuntApiImpl;
import com.jruk8.jmanhunt.config.SettingsListener;
import com.jruk8.jmanhunt.gui.GuiConfig;
import com.jruk8.jmanhunt.gui.GuiConfigRegistrar;
import com.jruk8.jmanhunt.gui.GuiListener;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.loot.PiglinBarterListener;
import com.jruk8.jmanhunt.world.teleport.PortalRouter;
import com.jruk8.jmanhunt.world.WorldEngineService;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class JManhuntPlugin extends JavaPlugin {
    private static final int MODIFIERS_VERSION = 3;
    private MessageService messages;
    private MessagesRegistrar messageConfigs;
    private SoundService sounds;
    private PlayerStateStore playerStates;
    private StatsManager stats;
    private CompassManager compass;
    private GameManager game;
    private StatisticsRepository statistics;
    private EngineStateRepository engineState;
    private JManhuntExpansion expansion;
    private JManhuntPlaceholders placeholderValues;
    private ConfigRegistrar configRegistrar;
    private ConfigService configService;
    private ModifierStore modifierStore;
    private ModifiersRegistrar modifierConfigs;
    private WorldEngineService worldEngine;
    private WinConditionEngine winConditionEngine;
    private JManhuntLogger logger;
    private DebugService debugService;
    private LobbyService lobbyService;
    private LobbyConfigRegistrar lobbyConfigs;
    private TutorialConfigRegistrar tutorialConfigs;
    private GuiConfigRegistrar guiConfigs;
    private PlaceholderConfigRegistrar placeholderConfigs;
    private TutorialService tutorialService;
    private UpdateCheckService updateChecks;
    private UpdateCheckNotifier updateCheckNotifier;
    private RoleTeamService roleTeams;
    private SpawnCampService spawnCamp;
    private GuiService guiService;
    private final List<SettingsListener> settings = new ArrayList<>();

    @Override
    public void onEnable() {
        bootstrapCore();
        bootstrapServices();
        bootstrapGame();
        setupPlaceholderApi();

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
        if (configService.getBoolean("send-anonymous-statistics", true)) {
            var metricsBootstrap = new MetricsBootstrap(this);
            metricsBootstrap.register();
        }
        // Scoreboard teams do not survive restarts: recreate them and repair
        // every online player's membership from their current role.
        roleTeams.syncAll();
        game.validateLobbyWorldName();
        StartupBanner.print(logger, getPluginMeta().getVersion());
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
        guiConfigs = new GuiConfigRegistrar(this);
        guiConfigs.register();
        placeholderConfigs = new PlaceholderConfigRegistrar(this);
        placeholderConfigs.register();
        configRegistrar = new ConfigRegistrar(this);
        configRegistrar.register();
        messageConfigs = new MessagesRegistrar(this);
        messageConfigs.register();
        reload();
    }

    /** Creates player, stats, config, sound, tutorial, compass, and world services. */
    private void bootstrapServices() {
        configService = new ConfigService(configRegistrar.getRoot(), modifierStore);
        sounds = new SoundService(this, configRegistrar.getSounds());
        playerStates = new PlayerStateStore();
        roleTeams = new RoleTeamService(playerStates);
        setupStatistics();
        setupEngineState();
        stats = new StatsManager(this, messages, statistics);
        stats.loadLobbySessionsAsync();
        debugService.resetToDefaults(configService.getBoolean("debug.enabled", false));
        guiService = new GuiService(sounds);
        tutorialService = new TutorialService(tutorialConfigs.getTutorialConfig(),
                new JManhuntTutorialMessenger(messages),
                new JManhuntTutorialSounds(tutorialConfigs.getTutorialConfig(), sounds),
                new JManhuntTutorialCommands(sounds),
                new JManhuntTutorialLogger(logger));
        compass = new CompassManager(this, messages, sounds, playerStates,
                new NamespacedKey(this, "hunters_compass"));
        worldEngine = new WorldEngineService(this, messages, configService, engineState);
        worldEngine.deleteOrphanedEndCells();
        loadLobbyWorldOnBoot();
        checkCrashFlag();
        updateCheckNotifier = new JManhuntUpdateCheckNotifier(messages);
        updateChecks = new UpdateCheckService(new JManhuntUpdateCheckHttp("jruk8", "JManhunt"),
                updateCheckNotifier, new JManhuntUpdateCheckLogger(logger()));
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
        winConditionEngine = new WinConditionEngine(configService);
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
        if (!configService.getBoolean("statistics.enabled", true)) {
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
        placeholderValues = new JManhuntPlaceholders(stats, messages, game, playerStates,
                winConditionEngine, placeholderConfigs.getPlaceholderConfig(), configService);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new JManhuntExpansion(this, stats, messages, game, playerStates,
                    winConditionEngine, placeholderConfigs.getPlaceholderConfig());
            expansion.register();
            logger().info("Hooked into PlaceholderAPI as the %jmanhunt_<placeholder>% expansion.");
        } else {
            logger().warning(
                    "PlaceholderAPI is not installed; JManhunt placeholders will not be hooked into.");
        }
    }

    /**
     * Loads the lobby world when its folder survived a restart but Bukkit
     * has not loaded it. Never generates: creation stays behind tpto confirm.
     */
    private void loadLobbyWorldOnBoot() {
        if (!configService.getBoolean("world-engine.enabled", false)) {
            return;
        }
        String name = worldEngine.lobbyWorldName();
        if (Bukkit.getWorld(name) != null || !worldEngine.lobbyWorldExists()) {
            return;
        }
        worldEngine.ensureLobbyWorld().ifPresent(
                lobbyWorld -> logger().info("Loaded existing lobby world '" + name + "'."));
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
        getServer().getPluginManager().registerEvents(new LobbyProtectionService(
                this, worldEngine::lobbyWorldName), this);
        getServer().getPluginManager().registerEvents(new LobbyBoundsService(
                this, lobbyService, playerStates, game, messages, sounds,
                worldEngine::lobbyWorldName, debugService,
                command::boundPos1View, command::boundPos2View,
                command::devPos1View, command::devPos2View), this);
        getServer().getPluginManager().registerEvents(new TutorialChatListener(this, tutorialService), this);
        getServer().getPluginManager().registerEvents(new GuiListener(guiService), this);
        getServer().getPluginManager().registerEvents(
                new UpdateCheckJoinListener(updateChecks, updateCheckNotifier), this);
    }

    private void setupScheduling() {
        double refreshInterval = configService.getDouble("settings.compass.refresh-interval", 10.0);
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
        Bukkit.getScheduler().runTaskAsynchronously(this,
                () -> updateChecks.checkNow(updateCheckSettings(), getPluginMeta().getVersion()));
    }

    /** Update checker toggles from config.yml. */
    private UpdateCheckSettings updateCheckSettings() {
        return new UpdateCheckSettings(configService.getBoolean("update-checker.enabled", true),
                configService.getBoolean("update-checker.releases.major", true),
                configService.getBoolean("update-checker.releases.minor", true),
                configService.getBoolean("update-checker.releases.hotfix", false));
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

    /** Chest-menu service (open, render, click routing). */
    public GuiService guiService() {
        return guiService;
    }

    /** Chat and component message service. */
    public MessageService messages() {
        return messages;
    }

    /** Typed config and modifier service. */
    public ConfigService configService() {
        return configService;
    }

    /** Match and lifetime statistics. */
    public StatsManager stats() {
        return stats;
    }

    /** In-house `%jmanhunt_*%` values, with or without PlaceholderAPI. */
    public JManhuntPlaceholders placeholderValues() {
        return placeholderValues;
    }

    /** Internal GUI data: category items plus setting descriptions. */
    public GuiConfig guiConfig() {
        return guiConfigs == null ? null : guiConfigs.getGuiConfig();
    }

    /**
     * True once setup finished anywhere. A broken engine database fails
     * open to the GUI so a stats hiccup never blocks the admin surface.
     */
    public boolean isSetupDone() {
        if (engineState == null) {
            return true;
        }
        try {
            return engineState.getSetupDone();
        } catch (Exception exception) {
            logger().warning("Could not read the setup flag: " + exception.getMessage());
            return true;
        }
    }

    /** Marks setup finished forever: session start, dismiss, or engine seen on. */
    public void markSetupDone() {
        if (engineState == null) {
            return;
        }
        try {
            engineState.setSetupDone(true);
        } catch (Exception exception) {
            logger().warning("Could not write the setup flag: " + exception.getMessage());
        }
    }

    /**
     * Marks setup done when the world engine is enabled. Called on
     * reload (which also covers enable) and after config writes.
     */
    public void observeWorldEngine() {
        if (engineState == null || configService == null) {
            return;
        }
        if (configService.getBoolean("world-engine.enabled", false)) {
            markSetupDone();
        }
    }

    public void reload() {
        if (configRegistrar != null) {
            configRegistrar.reload();
        }
        observeWorldEngine();
        if (messageConfigs != null) {
            messageConfigs.reload();
        }
        if (placeholderConfigs != null) {
            placeholderConfigs.reload();
        }

        if (messages == null) {
            messages = new MessageService();
        }
        if (messageConfigs != null) {
            messages.reload(messageConfigs.getMessagesConfig());
        }

        reloadModifiers();

        if (lobbyConfigs != null) {
            lobbyConfigs.reload();
        }

        if (tutorialConfigs != null) {
            tutorialConfigs.reload();
        }

        if (guiConfigs != null) {
            guiConfigs.reload();
        }

        if (winConditionEngine != null) {
            winConditionEngine.reload(configService);
        }

        // Cancel any running interval modifier tasks before reloading settings
        // so stale tasks do not keep firing against a partially updated config.
        if (game != null) {
            game.stateCommands().cancelAllIntervalModifiers();
        }
        reloadSettingsListeners();
        logger().info("JManhunt has been reloaded.");
    }

    /** Re-runs every settings listener, generating missing data files first. */
    private void reloadSettingsListeners() {
        for (SettingsListener listener : settings) {
            if (!new java.io.File(getDataFolder(), listener.getDataPath()).exists()) {
                saveResource(listener.getDataPath(), false);
            }
            listener.onReload();
        }
    }

    private void reloadModifiers() {
        YamlFileUpdater.update(this, "modifiers.yml", "modifiers-version", MODIFIERS_VERSION);
        if (modifierConfigs == null) {
            modifierConfigs = new ModifiersRegistrar(this);
            modifierConfigs.register();
            modifierStore = new ModifierStore(modifierConfigs.getModifiersConfig(), getLogger());
        } else {
            modifierConfigs.reload();
            modifierStore.clearItemWarnings();
        }
    }

}
