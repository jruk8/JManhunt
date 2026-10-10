package com.jruk8.jmanhunt.loot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

/** Brute spawn gate: brutes from any source cancel only while enabled. */
class BruteSpawnListenerTest {

    @Test
    void bruteCancelsWhenEnabled() {
        assertTrue(BruteSpawnListener.shouldCancel(EntityType.PIGLIN_BRUTE, true));
    }

    @Test
    void gatePassesThrough() {
        assertFalse(BruteSpawnListener.shouldCancel(EntityType.PIGLIN_BRUTE, false));
        assertFalse(BruteSpawnListener.shouldCancel(EntityType.PIGLIN, true));
        assertFalse(BruteSpawnListener.shouldCancel(EntityType.ZOMBIE, true));
    }
}
