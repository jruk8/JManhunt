package com.jruk8.jmanhunt.loot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.Test;

/** Brute spawn gate: natural brutes cancel only while enabled. */
class BruteSpawnListenerTest {

    @Test
    void naturalBruteCancelsWhenEnabled() {
        assertTrue(BruteSpawnListener.shouldCancel(EntityType.PIGLIN_BRUTE,
                CreatureSpawnEvent.SpawnReason.NATURAL, true));
    }

    @Test
    void gatePassesThrough() {
        assertFalse(BruteSpawnListener.shouldCancel(EntityType.PIGLIN_BRUTE,
                CreatureSpawnEvent.SpawnReason.NATURAL, false));
        assertFalse(BruteSpawnListener.shouldCancel(EntityType.PIGLIN_BRUTE,
                CreatureSpawnEvent.SpawnReason.SPAWNER_EGG, true));
        assertFalse(BruteSpawnListener.shouldCancel(EntityType.PIGLIN_BRUTE,
                CreatureSpawnEvent.SpawnReason.SPAWNER, true));
        assertFalse(BruteSpawnListener.shouldCancel(EntityType.PIGLIN,
                CreatureSpawnEvent.SpawnReason.NATURAL, true));
        assertFalse(BruteSpawnListener.shouldCancel(EntityType.ZOMBIE,
                CreatureSpawnEvent.SpawnReason.NATURAL, true));
    }
}
