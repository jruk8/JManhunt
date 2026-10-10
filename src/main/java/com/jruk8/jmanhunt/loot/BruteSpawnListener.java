package com.jruk8.jmanhunt.loot;

import com.jruk8.jmanhunt.config.MatchSettings;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Cancels every piglin brute spawn while the disable-brutes boost
 * is on, imitating 1.16.1 where brutes never spawned: natural
 * spawns, spawner eggs, spawners, and commands all cancel.
 * Checked live on every spawn.
 */
public final class BruteSpawnListener implements Listener {
    private final MatchSettings.GameBoosts boosts;

    public BruteSpawnListener(MatchSettings.GameBoosts boosts) {
        this.boosts = boosts;
    }

    @EventHandler public void onSpawn(CreatureSpawnEvent event) {
        boolean enabled = boosts.getDisableBrutes().isEnabled();
        if (shouldCancel(event.getEntityType(), enabled)) {
            event.setCancelled(true);
        }
    }

    /** True for brute spawns from any source while the boost is on. Pure for tests. */
    static boolean shouldCancel(EntityType type, boolean enabled) {
        return enabled && type == EntityType.PIGLIN_BRUTE;
    }
}
