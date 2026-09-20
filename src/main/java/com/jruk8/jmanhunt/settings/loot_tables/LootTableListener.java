package com.jruk8.jmanhunt.settings.loot_tables;

import com.jruk8.jmanhunt.GameManager;
import com.jruk8.jmanhunt.settings.SettingsListener;
import com.jruk8.jmanhunt.JManhuntPlugin;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;

import java.io.File;

public abstract class LootTableListener<T extends Event> implements Listener, SettingsListener {
    protected final JManhuntPlugin plugin;
    protected final LootTableEngine engine;
    private final GameManager game;
    private final File customFile;

    public LootTableListener(JManhuntPlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
        this.engine = new LootTableEngine();
        this.customFile = new File(plugin.getDataFolder(), "settings/loot-tables/" + getLootTableName() + ".json");
    }

    protected boolean validateEvent(T event) {
        if (!game.isActive()) {
            return false;
        }
        return plugin.getConfig().getBoolean("settings.game-boosts.%s".formatted(getConfigKey()), true)
                && customFile.exists();
    }

    protected abstract void handleEvent(T event);

    // As in resources/settings/loot-tables/<loot_table_name>.json.
    protected abstract String getLootTableName();

    protected abstract String getConfigKey();

    private void reloadTable() {
        String name = getLootTableName();
        if (!customFile.exists()) {
            plugin.logger().warning("Custom loot table '%s.json' does not exist.".formatted(name));
            return;
        }

        boolean success = engine.loadFromFile(customFile);

        if (!success) {
            plugin.logger().severe("Found loot table '%s.json' but failed to parse it!".formatted(name));
            return;
        }
        plugin.logger().info("Successfully loaded custom loot table '%s.json'!".formatted(name));
    }

    public void onStart() {
        // unused
    }

    public void onReload() {
        reloadTable();
    }

    public String getDataPath() {
        return "settings/loot-tables/%s.json".formatted(getLootTableName());
    }
}
