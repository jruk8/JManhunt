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
import com.jruk8.jmanhunt.config.DevConfig;
import com.jruk8.jmanhunt.config.DevConfigRegistrar;
import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.lobby.bounds.LobbyBoundsService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.LobbyConfigRegistrar;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.lobby.config.WinConditionsSettingsFacade;
import com.jruk8.jmanhunt.lobby.LobbyProtectionService;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.RolePadService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.TeamChatService;
import com.jruk8.jmanhunt.match.listeners.PlayerMovementListener;
import com.jruk8.jmanhunt.match.listeners.PlayerRespawnListener;
import com.jruk8.jmanhunt.match.listeners.PrestartTargetListener;
import com.jruk8.jmanhunt.match.listeners.TeamChatListener;
import com.jruk8.jmanhunt.match.WinConditionEngine;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.message.MessagesRegistrar;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.files.ModFileKind;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult;
import com.jruk8.jmanhunt.modifiers.files.ModifierFiles;
import com.jruk8.jmanhunt.modifiers.files.ModifierReloadCache;
import com.jruk8.jmanhunt.modifiers.files.ReloadDiff;
import com.jruk8.jmanhunt.modifiers.files.ModsDefaults;
import com.jruk8.jmanhunt.modifiers.files.ModsLoader;
import com.jruk8.jmanhunt.placeholders.PlaceholderConfigRegistrar;
import com.jruk8.jmanhunt.gui.menus.SpectatorMenus;
import com.jruk8.jmanhunt.player.FakeSpectatorListener;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.SpectatorSnowballListener;
import com.jruk8.jmanhunt.player.SpectatorSnowballService;
import com.jruk8.jmanhunt.player.SpectatorToolbarListener;
import com.jruk8.jmanhunt.player.SpectatorToolbarService;
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
import com.jruk8.jmanhunt.loot.BruteSpawnListener;
import com.jruk8.jmanhunt.loot.PiglinBarterListener;
import com.jruk8.jmanhunt.world.teleport.PortalRouter;
import com.jruk8.jmanhunt.world.WorldEngineService;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JManhuntPlugin extends JavaPlugin {
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
    private OverrideService overrideService;
    private ModifierStore modifierStore;
    private final ModifierReloadCache reloadCache = new ModifierReloadCache();
    private WorldEngineService worldEngine;
    private WinConditionEngine winConditionEngine;
    private JManhuntLogger logger;
    private DebugService debugService;
    private LobbyService lobbyService;
    private LobbyConfigRegistrar lobbyConfigs;
    private TutorialConfigRegistrar tutorialConfigs;
    private GuiConfigRegistrar guiConfigs;
    private PlaceholderConfigRegistrar placeholderConfigs;
    private DevConfigRegistrar devConfigs;
    private TutorialService tutorialService;
    private UpdateCheckService updateChecks;
    private UpdateCheckNotifier updateCheckNotifier;
    private RoleTeamService roleTeams;
    private FakeSpectatorService fakeSpectators;
    private PlayerRespawnListener respawnListener;
    private SpectatorToolbarService spectatorToolbar;
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
        if (configRegistrar.getRoot().isSendAnonymousStatistics()) {
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
        debugService = new DebugService();
        lobbyConfigs = new LobbyConfigRegistrar(this);
        lobbyConfigs.register();
        tutorialConfigs = new TutorialConfigRegistrar(this);
        tutorialConfigs.register();
        guiConfigs = new GuiConfigRegistrar(this);
        guiConfigs.register();
        placeholderConfigs = new PlaceholderConfigRegistrar(this);
        placeholderConfigs.register();
        devConfigs = new DevConfigRegistrar(this);
        devConfigs.register();
        configRegistrar = new ConfigRegistrar(this);
        configRegistrar.register();
        var root = configRegistrar.getRoot();
        spawnCamp = new SpawnCampService(this,
                root.getSettings().getServer().getAntiSpawnCamp(), messages, messages.game());
        lobbyService = new LobbyService(this, root.getAdvanced().getLobbies(),
                root.getWorldEngine(), messages.manhunt());
        messageConfigs = new MessagesRegistrar(this);
        messageConfigs.register();
        logger = new JManhuntLogger(getLogger(), debugService, messages,
                messageConfigs.getMessagesConfig().getDebug(), BukkitDebugSink.INSTANCE);
        reload();
    }

    /** Creates player, stats, config, sound, tutorial, compass, and world services. */
    private void bootstrapServices() {
        configService = new ConfigService(configRegistrar.getRoot(), modifierStore);
        overrideService = new OverrideService(configService, lobbyConfigs.getLobbyConfig(),
                lobbyConfigs.getLobbyConfig()::save);
        sounds = new SoundService(this, configRegistrar.getSounds());
        playerStates = new PlayerStateStore();
        roleTeams = new RoleTeamService(playerStates);
        fakeSpectators = new FakeSpectatorService(this, playerStates);
        setupStatistics();
        setupEngineState();
        stats = new StatsManager(this, messages, messages.game(), statistics);
        stats.loadLobbySessionsAsync();
        guiService = new GuiService(sounds);
        tutorialService = new TutorialService(tutorialConfigs.getTutorialConfig(),
                new JManhuntTutorialMessenger(messages),
                new JManhuntTutorialSounds(tutorialConfigs.getTutorialConfig(), sounds),
                new JManhuntTutorialCommands(sounds),
                new JManhuntTutorialLogger(logger));
        MessagesConfig texts = messageConfigs.getMessagesConfig();
        compass = new CompassManager(this, messages, texts.getCompass(), texts.getModifiers(),
                sounds, playerStates, new NamespacedKey(this, "hunters_compass"));
        var root = configRegistrar.getRoot();
        worldEngine = new WorldEngineService(this, messages, root.getWorldEngine(),
                root.getSettings().getMatch().getGameBoosts(),
                root.getAdvanced().getLobbies(), engineState, playerStates);
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
        var winConditions = new WinConditionsSettingsFacade(overrideService,
                configRegistrar.getRoot().getSettings().getMatch().getWinConditions());
        winConditionEngine = new WinConditionEngine(winConditions);
        MessagesConfig gameTexts = messageConfigs.getMessagesConfig();
        game = new GameManager(
                this, messages, gameTexts.getManhunt(), gameTexts.getGame(), gameTexts.getWincon(),
                sounds, playerStates, compass, stats,
                configService, worldEngine, winConditionEngine, lobbyService);
        game.loadCrashCleanup();
        compass.setGameManager(game);
        worldEngine.setMatchRunningSupplier(game::isActive);
        modifierStore.addToggleListener(name -> game.syncModifierToggles(List.of(name)));
        overrideService.addToggleListener(name -> game.syncModifierToggles(List.of(name)));
        setupListeners();
    }

    /** Scoreboard-team mirror of manhunt roles. */
    public RoleTeamService roleTeams() {
        return roleTeams;
    }

    /** Fake spectator mode: adventure plus flight plus hidden. */
    public FakeSpectatorService fakeSpectators() {
        return fakeSpectators;
    }

    /** Respawn routing and delayed spectator revives. */
    public PlayerRespawnListener respawnListener() {
        return respawnListener;
    }

    /** Rolling anti-spawn-camp guard. */
    public SpawnCampService spawnCamp() {
        return spawnCamp;
    }

    private void setupStatistics() {
        var statisticsConfig = configRegistrar.getRoot().getStatistics();
        if (!statisticsConfig.isEnabled()) {
            return;
        }
        try {
            statistics = StatisticsRepository.open(this, statisticsConfig);
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
                winConditionEngine, placeholderConfigs.getPlaceholderConfig(),
                game.matchSettings());
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
        if (!configRegistrar.getRoot().getWorldEngine().isEnabled()) {
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
        var root = configRegistrar.getRoot();
        var players = root.getSettings().getPlayers();
        var match = root.getSettings().getMatch();
        var advanced = root.getAdvanced();
        var piglinBarter = new PiglinBarterListener(this, game, match.getGameBoosts());
        settings.add(worldEngine);
        settings.add(piglinBarter);
        ManhuntCommand command = new ManhuntCommand(
                this, messages, configService, sounds, playerStates, game, worldEngine.teleportService(),
                debugService, lobbyService);
        getCommand("manhunt").setExecutor(command);
        getCommand("manhunt").setTabCompleter(command);
        var manager = getServer().getPluginManager();
        manager.registerEvents(new CompassProtectionListener(
                this, advanced.getMisc().getInterop(), compass, game), this);
        manager.registerEvents(new PortalRouter(this, game, worldEngine, root.getWorldEngine()), this);
        PlayerRespawnListener respawn = createRespawnListener();
        SpeedrunnerDisconnectTracker disconnects = new SpeedrunnerDisconnectTracker();
        Map<UUID, BukkitTask> disconnectTasks = new HashMap<>();
        var handling = advanced.getAdvancedMatchControls().getDisconnectHandling();
        var gameTexts = messageConfigs.getMessagesConfig().getGame();
        manager.registerEvents(game.connectionListener(players, handling, respawn,
                disconnects, disconnectTasks, compass, gameTexts), this);
        manager.registerEvents(game.combatListener(players, respawn, disconnects,
                disconnectTasks, compass, gameTexts), this);
        setupChatListeners();
        manager.registerEvents(new PlayerMovementListener(
                playerStates, game, winConditionEngine, worldEngine, fakeSpectators), this);
        setupTargetShielding();
        setupSpectatorToolbar();
        manager.registerEvents(respawn, this);
        manager.registerEvents(piglinBarter, this);
        manager.registerEvents(new BruteSpawnListener(match.getGameBoosts()), this);
        var lobbies = advanced.getLobbies();
        var pads = new RolePadService(this, lobbies, lobbyService, playerStates, game,
                messages, messages.manhunt(), sounds, worldEngine::lobbyWorldName);
        manager.registerEvents(pads, this);
        manager.registerEvents(new LobbyProtectionService(this, worldEngine::lobbyWorldName), this);
        manager.registerEvents(new LobbyBoundsService(this, lobbies.getBounds(), lobbyService,
                playerStates, game, messages, sounds, worldEngine::lobbyWorldName, debugService,
                command::boundPos1View, command::boundPos2View, command::devPos1View, command::devPos2View), this);
        manager.registerEvents(new GuiListener(guiService), this);
        manager.registerEvents(new UpdateCheckJoinListener(updateChecks, updateCheckNotifier), this);
    }

    /** Creates the respawn router and keeps it for roster skull lookups. */
    private PlayerRespawnListener createRespawnListener() {
        respawnListener = new PlayerRespawnListener(this, playerStates, game, compass,
                messageConfigs.getMessagesConfig().getGame());
        return respawnListener;
    }

    /** Registers mob-target shields: fake spectators plus the pre-start window. */
    private void setupTargetShielding() {
        getServer().getPluginManager().registerEvents(
                new FakeSpectatorListener(fakeSpectators, playerStates, game), this);
        getServer().getPluginManager().registerEvents(new PrestartTargetListener(game), this);
    }

    /** Registers chat listeners: team chat plus the setup tutorial. */
    private void setupChatListeners() {
        var teamChat = configRegistrar.getRoot().getSettings().getServer().getTeamChat();
        var chat = new TeamChatService(game, playerStates, fakeSpectators, teamChat,
                messages, messages.chat(), sounds);
        getServer().getPluginManager().registerEvents(new TeamChatListener(this, chat), this);
        getServer().getPluginManager().registerEvents(new TutorialChatListener(this, tutorialService), this);
    }

    /** Creates the spectator toolbar and registers its listener. */
    private void setupSpectatorToolbar() {
        // Leaving fake spectator mode restores collisions; lobby state
        // wins again for members still waiting in a lobby.
        fakeSpectators.addModeListener((player, enabled) -> {
            if (!enabled) {
                lobbyService.applyLobbyCollisions(player);
            }
        });
        spectatorToolbar = new SpectatorToolbarService(game.playersSettings(), messages, messages.spectator(),
                messages.command(), sounds, playerStates,
                fakeSpectators, game, lobbyService, new NamespacedKey(this, "spectator_toolbar"));
        SpectatorMenus menus = new SpectatorMenus(messages,
                messageConfigs.getMessagesConfig().getSpectator(), guiService, spectatorToolbar);
        getServer().getPluginManager().registerEvents(
                new SpectatorToolbarListener(spectatorToolbar, menus), this);
        SpectatorSnowballService snowballs = new SpectatorSnowballService(
                this, spectatorToolbar, messages, messages.spectator(), sounds);
        getServer().getPluginManager().registerEvents(new SpectatorSnowballListener(snowballs), this);
    }

    private void setupScheduling() {
        // Fast shared ticker: per-holder gating inside refreshAllCompasses
        // keeps each automatic interval strict (a slow shared beat would
        // quantize every holder to the same refresh moments). A -1
        // interval disables automatic refresh per lobby in the gate.
        Bukkit.getScheduler().runTaskTimer(this,
                () -> compass.refreshAllCompasses(game.isActive()), 5L, 5L);
        var actionbar = configRoot().getSettings().getCompass().getFeedback().getActionbar();
        long actionbarTicks = Math.max(1L, actionbar.getRefreshTicks());
        BukkitTask actionbars = Bukkit.getScheduler().runTaskTimer(this,
                () -> compass.showHeldActionbars(game.isActive()), 1L, actionbarTicks);
        Bukkit.getScheduler().runTaskTimer(this, game::broadcastAutostartShortfalls, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, spectatorToolbar::tickLocks, 5L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, tutorialService::checkTimeouts, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, worldEngine::careTick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, compass::sampleHotspots, 20L, 20L);
        Bukkit.getScheduler().runTaskAsynchronously(this,
                () -> updateChecks.checkNow(UpdateCheckSettings.fromRoot(
                        configRegistrar.getRoot().getUpdateChecker()),
                        getPluginMeta().getVersion()));
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

    /** Per-lobby overrides plus effective resolution. */
    public OverrideService overrides() {
        return overrideService;
    }

    /** Okaeri config root, for wiring narrow sections into consumers. */
    public JManhuntConfig configRoot() {
        return configRegistrar.getRoot();
    }

    /** Live match manager, null until bootstrap finishes. */
    public GameManager game() {
        return game;
    }

    /** Match and lifetime statistics. */
    public StatsManager stats() {
        return stats;
    }

    /** Always-SQLite engine store, null until bootstrap finishes. */
    public EngineStateRepository engineStates() {
        return engineState;
    }

    /** In-house `%jmanhunt_*%` values, with or without PlaceholderAPI. */
    public JManhuntPlaceholders placeholderValues() {
        return placeholderValues;
    }

    /** Internal GUI data: category items plus setting descriptions. */
    public GuiConfig guiConfig() {
        return guiConfigs == null ? null : guiConfigs.getGuiConfig();
    }

    /** Internal dev data: lobby presets for fresh lobby-world generation. */
    public DevConfig devConfig() {
        return devConfigs == null ? new DevConfig() : devConfigs.getDevConfig();
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
        if (configRegistrar.getRoot().getWorldEngine().isEnabled()) {
            markSetupDone();
        }
    }

    /** Synchronous full reload, used at enable. */
    public void reload() {
        reloadExceptModifiers();
        applyModifierReload(loadModsSnapshot());
        logger().info("JManhunt has been reloaded.");
    }

    /** Reloads everything except mods: the synchronous part of /mh reload. */
    public void reloadExceptModifiers() {
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

        reloadContentConfigs();

        if (overrideService != null) {
            overrideService.reload(configService);
        }

        reloadSettingsListeners();
    }

    /** Loads both mods dirs. Async-safe: file IO plus logger warnings only. */
    public ModLoadResult loadModsSnapshot() {
        return new ModsLoader(this::seedBundledMods, getLogger()).load(modsRoot());
    }

    /**
     * Applies a fresh mods load on the main thread: diffs against the
     * cache, swaps the store, refreshes the cache, and re-registers
     * interval chains for changed/removed modifiers only. Returns the
     * diff; the first load seeds the cache and reports nothing.
     */
    public ReloadDiff applyModifierReload(ModLoadResult fresh) {
        if (modifierStore == null) {
            modifierStore = new ModifierStore(
                    ModifierFiles.fromLoad(modsRoot(), fresh), getLogger());
            reloadCache.seed(fresh);
            return new ReloadDiff(List.of(), List.of(), List.of(), List.of());
        }
        ReloadDiff diff = reloadCache.diff(fresh);
        Set<String> changed = reloadCache.changedModifierIds(fresh);
        modifierStore.replaceAll(fresh);
        modifierStore.clearItemWarnings();
        reloadCache.replace(fresh);
        reregisterChangedIntervals(diff, changed);
        return diff;
    }

    /**
     * Re-registers interval chains for changed/removed modifiers on
     * begun, non-ending matches. Unbegun matches arm at begin and
     * ending matches stay quiet for teardown.
     */
    private void reregisterChangedIntervals(ReloadDiff diff, Set<String> changed) {
        if (game == null) {
            return;
        }
        Set<String> affected = new LinkedHashSet<>(diff.newModifiers());
        affected.addAll(diff.removedModifiers());
        affected.addAll(changed);
        if (affected.isEmpty()) {
            return;
        }
        for (GameInstance instance : game.liveInstances()) {
            if (!instance.begun() || instance.ending()) {
                continue;
            }
            game.stateCommands().reregisterIntervalModifiers(instance.matchId(), affected);
        }
    }

    /** Reloads lobby, tutorial, GUI, and dev-data content configs. */
    private void reloadContentConfigs() {
        if (lobbyConfigs != null) {
            lobbyConfigs.reload();
        }
        if (tutorialConfigs != null) {
            tutorialConfigs.reload();
        }
        if (guiConfigs != null) {
            guiConfigs.reload();
        }
        if (devConfigs != null) {
            devConfigs.reload();
        }
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

    private Path modsRoot() {
        return new File(getDataFolder(), "mods").toPath();
    }

    /**
     * Copies bundled defaults for one kind dir. The loader calls this
     * only when it creates the dir, so user deletions are never
     * restored. The resource path resolves the target, so {@code dir}
     * only documents the seeding contract.
     */
    private void seedBundledMods(ModFileKind kind, Path dir) {
        for (String id : ModsDefaults.ids(kind)) {
            saveResource("mods/" + kind.dirName() + "/" + id + ".yml", false);
        }
    }

}
