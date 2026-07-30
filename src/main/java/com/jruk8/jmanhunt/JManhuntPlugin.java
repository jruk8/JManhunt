package com.jruk8.jmanhunt;

import com.jruk8.jmanhunt.extras.loot_tables.PiglinBarterListener;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;


public final class JManhuntPlugin extends JavaPlugin {
    private static final int CONFIG_VERSION = 6;
    private static final int MESSAGES_VERSION = 3;
    private MessageService messages;
    private PlayerStateStore playerStates;
    private StatsManager stats;
    private CompassManager compass;
    private GameManager game;
    private StatsRepository statsRepository;
    private JManhuntExpansion expansion;
    private final List<ExtrasListener> extras = new ArrayList<>();

    @Override
    public void onEnable() {
        var piglinBarter = new PiglinBarterListener(this);
        extras.add(piglinBarter);
        Reload();

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
        getServer().getPluginManager().registerEvents(piglinBarter, this);

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

    public void Reload() {
        YamlFileUpdater.update(this, "config.yml", "config-version", CONFIG_VERSION);
        reloadConfig();
        migrateConfig();
        YamlFileUpdater.update(this, "messages.yml", "messages-version", MESSAGES_VERSION);
        saveResource("placeholders.yml", false);

        if (messages == null) {
            messages = new MessageService();
        }
        messages.reload(YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(), "messages.yml")),
                getConfig().getString("text-format", "minimessage"));

        for (ExtrasListener listener : extras) {
            var dataPath = "extras/" + listener.getDataPath();
            saveResource(dataPath, false);
            listener.onReload();
        }
        getLogger().info("JManhunt has been reloaded.");
    }

    private void migrateConfig() {
        FileConfiguration config = getConfig();
        boolean changed = false;
        if (config.contains("settings")) {
            if (config.contains("settings.start-on-speedrunner-damage")
                    && !config.contains("extras.start-on-speedrunner-damage.enabled")) {
                config.set("extras.start-on-speedrunner-damage.enabled",
                        config.getBoolean("settings.start-on-speedrunner-damage", true));
            }
            config.set("settings", null);
            changed = true;
        }
        if (config.contains("settings.start-debuffs") && !config.contains("extras.start-debuffs.enabled")) {
            config.set("extras.start-debuffs.enabled", config.getBoolean("settings.start-debuffs", false));
            changed = true;
        }
        if (config.contains("start-debuffs") && !config.contains("extras.start-debuffs.effects")) {
            copySection(config, "start-debuffs", "extras.start-debuffs");
            changed = true;
        }
        if (config.contains("drop-compass-on-death") && !config.contains("extras.drop-compass-on-death.enabled")) {
            config.set("extras.drop-compass-on-death.enabled", config.getBoolean("drop-compass-on-death", false));
            changed = true;
        }
        if (changed) saveConfig();
    }

    private void copySection(FileConfiguration config, String fromPath, String toPath) {
        ConfigurationSection from = config.getConfigurationSection(fromPath);
        if (from == null) return;
        if (config.contains(toPath)) config.set(toPath, null);
        config.createSection(toPath);
        for (String key : from.getKeys(false)) {
            String childPath = toPath + "." + key;
            ConfigurationSection childSection = from.getConfigurationSection(key);
            if (childSection != null) {
                copySection(config, fromPath + "." + key, childPath);
            } else {
                config.set(childPath, from.get(key));
            }
        }
    }
}
