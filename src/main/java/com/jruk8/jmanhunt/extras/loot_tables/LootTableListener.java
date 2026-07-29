package com.jruk8.jmanhunt.extras.loot_tables;

import com.jruk8.jmanhunt.ExtrasListener;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public abstract class LootTableListener<T extends Event> implements Listener, ExtrasListener {
    protected final JavaPlugin plugin;
    protected final LootTableEngine engine;
    private final File customFile;

    public LootTableListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.engine = new LootTableEngine();
        this.customFile = new File(plugin.getDataFolder(), "extras/loot-tables/" + getLootTableName() + ".json");
    }

    protected boolean validateEvent(T event) {
        return plugin.getConfig().getBoolean("extras.loot-tables.%s".formatted(getConfigKey()), true) && customFile.exists();
    }

    protected abstract void handleEvent(T event);

    // As in resources/extras/loot-tables/<loot_table_name>.json.
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

    public void onReload() {
        reloadTable();
    }

    public String getDataPath() {
        return "loot-tables/%s.json".formatted(getLootTableName());
    }
}