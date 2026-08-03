package com.jruk8.jmanhunt.settings.loot_tables;

import com.jruk8.jmanhunt.GameManager;
import com.jruk8.jmanhunt.settings.SettingsListener;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public abstract class LootTableListener<T extends Event> implements Listener, SettingsListener {
    protected final JavaPlugin plugin;
    protected final LootTableEngine engine;
    private final GameManager game;
    private final File customFile;

    public LootTableListener(JavaPlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
        this.engine = new LootTableEngine();
        this.customFile = new File(plugin.getDataFolder(), "settings/loot-tables/" + getLootTableName() + ".json");
    }

    protected boolean validateEvent(T event) {
        if (!game.isActive()) return false;
        return plugin.getConfig().getBoolean("settings.loot-tables.%s".formatted(getConfigKey()), true) && customFile.exists();
    }

    protected abstract void handleEvent(T event);

    // As in resources/settings/loot-tables/<loot_table_name>.json.
    protected abstract String getLootTableName();

    protected abstract String getConfigKey();

    private void reloadTable() {
        String name = getLootTableName();
        if (!customFile.exists()) {
            plugin.getLogger().warning("Custom loot table '%s.json' does not exist.".formatted(name));
            return;
        }

        boolean success = engine.loadFromFile(customFile);

        if (!success) {
            plugin.getLogger().severe("Found loot table '%s.json' but failed to parse it!".formatted(name));
            return;
        }
        plugin.getLogger().info("Successfully loaded custom loot table '%s.json'!".formatted(name));
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
