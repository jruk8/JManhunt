package com.jruk8.jmanhunt.loot;

import com.jruk8.jmanhunt.config.MatchSettings;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Cancels natural piglin brute spawns while the disable-brutes boost
 * is on, imitating 1.16.1 where brutes never spawned. Spawner eggs,
 * spawners, and commands still work. Checked live on every spawn.
 */
public final class BruteSpawnListener implements Listener {
    private final MatchSettings.GameBoosts boosts;

    public BruteSpawnListener(MatchSettings.GameBoosts boosts) {
        this.boosts = boosts;
    }

    @EventHandler public void onSpawn(CreatureSpawnEvent event) {
        boolean enabled = boosts.getDisableBrutes().isEnabled();
        if (shouldCancel(event.getEntityType(), event.getSpawnReason(), enabled)) {
            event.setCancelled(true);
        }
    }

    /** True for natural brute spawns while the boost is on. Pure for tests. */
    static boolean shouldCancel(EntityType type, CreatureSpawnEvent.SpawnReason reason,
            boolean enabled) {
        return enabled && type == EntityType.PIGLIN_BRUTE
                && reason == CreatureSpawnEvent.SpawnReason.NATURAL;
    }
}
