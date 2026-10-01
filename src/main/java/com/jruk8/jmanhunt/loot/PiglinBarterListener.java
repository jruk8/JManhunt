package com.jruk8.jmanhunt.loot;

import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.MatchSettings;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PiglinBarterEvent;

/**
 * Listens for PiglinBarterEvent and replaces the default loot with custom loot from a JSON file.
 */
public class PiglinBarterListener extends LootTableListener<PiglinBarterEvent> {
    private final MatchSettings.GameBoosts boosts;

    public PiglinBarterListener(JManhuntPlugin plugin, GameManager game,
            MatchSettings.GameBoosts boosts) {
        super(plugin, game);
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
