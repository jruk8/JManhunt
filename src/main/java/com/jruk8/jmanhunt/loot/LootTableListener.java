package com.jruk8.jmanhunt.loot;

import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.config.SettingsListener;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import java.io.File;
import java.nio.file.Path;

public abstract class LootTableListener<T extends Event> implements Listener, SettingsListener {
    protected final JManhuntLogger log;
    protected final LootTableEngine engine;
    private final GameManager game;
    private final File customFile;

    public LootTableListener(Path dataFolder, JManhuntLogger log, GameManager game) {
        this.log = log;
        this.game = game;
        this.engine = new LootTableEngine();
        this.customFile = dataFolder.resolve("settings/loot-tables/" + getLootTableName() + ".json").toFile();
    }

    protected boolean validateEvent(T event) {
        if (!game.isActive()) {
            return false;
        }
        return isBoostEnabled() && customFile.exists();
    }

    protected abstract void handleEvent(T event);

    // As in resources/settings/loot-tables/<loot_table_name>.json.
    protected abstract String getLootTableName();

    /** True when this table's game boost is on. */
    protected abstract boolean isBoostEnabled();

    private void reloadTable() {
        String name = getLootTableName();
        if (!customFile.exists()) {
            log.warning("Custom loot table '%s.json' does not exist.".formatted(name));
            return;
        }

        boolean success = engine.loadFromFile(customFile, log);

        if (!success) {
            log.severe("Found loot table '%s.json' but failed to parse it!".formatted(name));
            return;
        }
        log.info("Successfully loaded custom loot table '%s.json'!".formatted(name));
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
