package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;


public final class JManhuntPlugin extends JavaPlugin {
    private static final int CONFIG_VERSION = 2;
    private static final int MESSAGES_VERSION = 1;
    private MessageService messages;
    private PlayerStateStore playerStates;
    private StatsManager stats;
    private CompassManager compass;
    private GameManager game;
    private StatsRepository statsRepository;
    private JManhuntExpansion expansion;

    @Override
    public void onEnable() {
        YamlFileUpdater.update(this, "config.yml", "config-version", CONFIG_VERSION);
        YamlFileUpdater.update(this, "messages.yml", "messages-version", MESSAGES_VERSION);
        saveResource("placeholders.yml", false);

        messages = new MessageService();
        messages.reload(YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(), "messages.yml")),
                getConfig().getString("text-format", "minimessage"));
        playerStates = new PlayerStateStore();
        if (getConfig().getBoolean("database.enabled", true)) {
            try {
                statsRepository = StatsRepository.open(this);
                getLogger().info("Career statistics database initialized.");
            } catch (Exception exception) {
                getLogger().severe("Career statistics are disabled because the database could not be initialized: "
                        + exception.getMessage());
            }
        }
        stats = new StatsManager(this, messages, statsRepository);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new JManhuntExpansion(this, stats, messages);
            expansion.register();
            getLogger().info("Hooked into PlaceholderAPI as the %jmanhunt_<placeholder>% expansion.");
        } else {
            getLogger().warning("PlaceholderAPI is not installed; JManhunt placeholders will not be hooked into.");
        }
        compass = new CompassManager(this, messages, playerStates,
                new NamespacedKey(this, "hunters_compass"));
        game = new GameManager(this, messages, playerStates, compass, stats);

        ManhuntCommand command = new ManhuntCommand(this, messages, playerStates, game, compass);
        getCommand("manhunt").setExecutor(command);
        getCommand("manhunt").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new CompassProtectionListener(this, compass, game), this);
        getServer().getPluginManager().registerEvents(new GameplayListener(this, playerStates, game, compass, stats), this);

        double refreshInterval = getConfig().getDouble("compass-refresh.compass-refresh-interval", 10.0);
        if (refreshInterval != -1.0) {
            long ticks = Math.max(1L, Math.round(refreshInterval * 20.0));
            Bukkit.getScheduler().runTaskTimer(this,
                    () -> compass.refreshAllCompasses(game.isActive()), ticks, ticks);
        }
        BukkitTask actionbars = Bukkit.getScheduler().runTaskTimer(this,
                () -> compass.showHeldActionbars(game.isActive()), 1L, 20L);
    }

    @Override public void onDisable() {
        if (expansion != null) expansion.unregister();
        if (stats != null) stats.flush();
        if (statsRepository != null) statsRepository.close();
    }

}
