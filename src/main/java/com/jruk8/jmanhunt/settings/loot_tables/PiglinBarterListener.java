package com.jruk8.jmanhunt.settings.loot_tables;

import com.jruk8.jmanhunt.GameManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listens for PiglinBarterEvent and replaces the default loot with custom loot from a JSON file.
 */
public class PiglinBarterListener extends LootTableListener<PiglinBarterEvent> {
    public PiglinBarterListener(JavaPlugin plugin, GameManager game) {
        super(plugin, game);
    }

    @EventHandler
    public void onEvent(PiglinBarterEvent event) {
        if (validateEvent(event)) {
            handleEvent(event);
        }
    }

    @Override
    protected void handleEvent(PiglinBarterEvent event) {
        event.getOutcome().clear();
        event.getOutcome().addAll(engine.getRandomLoot());
    }

    @Override
    protected String getLootTableName() {
        return "piglin-barter";
    }

    @Override
    protected String getConfigKey() {
        return "custom-piglin-barter";
    }
}
