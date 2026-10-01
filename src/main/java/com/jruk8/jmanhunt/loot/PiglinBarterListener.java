package com.jruk8.jmanhunt.loot;

import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.config.MatchSettings;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PiglinBarterEvent;
import java.nio.file.Path;

/**
 * Listens for PiglinBarterEvent and replaces the default loot with custom loot from a JSON file.
 */
public class PiglinBarterListener extends LootTableListener<PiglinBarterEvent> {
    private final MatchSettings.GameBoosts boosts;

    public PiglinBarterListener(Path dataFolder, JManhuntLogger log, GameManager game,
            MatchSettings.GameBoosts boosts) {
        super(dataFolder, log, game);
        this.boosts = boosts;
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
    protected boolean isBoostEnabled() {
        return boosts.isCustomPiglinBarter();
    }
}
