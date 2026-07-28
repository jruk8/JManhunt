package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public final class JManhuntPlugin extends JavaPlugin {
    private static final int CONFIG_VERSION = 3;
    private static final int MESSAGES_VERSION = 3;
    private MessageService messages;
    private PlayerStateStore playerStates;
    private StatsManager stats;
    private CompassManager compass;
    private GameManager game;

    @Override
    public void onEnable() {
        migrateLegacyConfigKeys();
        YamlFileUpdater.update(this, "config.yml", "config-version", CONFIG_VERSION);
        YamlFileUpdater.update(this, "messages.yml", "messages-version", MESSAGES_VERSION);
        saveResource("placeholders.yml", false);

        messages = new MessageService();
        messages.reload(YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(), "messages.yml")),
                getConfig().getString("text-format", "minimessage"));
        playerStates = new PlayerStateStore();
        stats = new StatsManager(this, messages);
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

    private void migrateLegacyConfigKeys() {
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        changed |= copyIfMissing(config, "start-commands", "gamestate-commands.start");
        changed |= copyIfMissing(config, "end-commands", "gamestate-commands.end");
        changed |= copyIfMissing(config, "compass-refresh-interval", "compass-refresh.compass-refresh-interval");
        changed |= copyIfMissing(config, "compass-refresh.refresh-on-right-click",
                "compass-refresh.right-click.refresh-on-right-click");
        changed |= copyIfMissing(config, "compass-refresh.compass-right-click-cooldown",
                "compass-refresh.right-click.right-click-cooldown");
        if (!changed) return;
        try {
            config.save(file);
        } catch (IOException exception) {
            getLogger().warning("Could not migrate config.yml: " + exception.getMessage());
        }
    }

    private boolean copyIfMissing(YamlConfiguration config, String oldPath, String newPath) {
        if (!config.contains(oldPath) || config.contains(newPath)) return false;
        config.set(newPath, config.get(oldPath));
        return true;
    }
}
