package com.jruk8.jmanhunt.command;

import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cooldown stamps over a fake clock: acquire, remaining, reset, teardown. */
class TagCooldownStoreTest {

    private static final class Clock implements LongSupplier {
        long now;

        @Override
        public long getAsLong() {
            return now;
        }
    }

    @Test
    void acquireBlocksUntilTheWindowElapses() {
        Clock clock = new Clock();
        TagCooldownStore store = new TagCooldownStore(clock);

        assertTrue(store.tryAcquire(7L, "dash", 5));
        assertFalse(store.tryAcquire(7L, "dash", 5));
        clock.now += 4999;
        assertFalse(store.tryAcquire(7L, "dash", 5));
        clock.now += 1;
        assertTrue(store.tryAcquire(7L, "dash", 5));
    }

    @Test
    void remainingCountsDownToZero() {
        Clock clock = new Clock();
        TagCooldownStore store = new TagCooldownStore(clock);

        assertEquals(0, store.remainingSeconds(7L, "dash", 5), 0.000001);
        assertTrue(store.tryAcquire(7L, "dash", 5));
        assertEquals(5, store.remainingSeconds(7L, "dash", 5), 0.000001);
        clock.now += 1500;
        assertEquals(3.5, store.remainingSeconds(7L, "dash", 5), 0.000001);
        clock.now += 3500;
        assertEquals(0, store.remainingSeconds(7L, "dash", 5), 0.000001);
    }

    @Test
    void resetAndTeardownClearStamps() {
        Clock clock = new Clock();
        TagCooldownStore store = new TagCooldownStore(clock);

        assertTrue(store.tryAcquire(7L, "dash", 5));
        store.reset(7L, "dash");
        assertTrue(store.tryAcquire(7L, "dash", 5));
        store.reset(7L, "missing");

        clock.now += 1000;
        assertTrue(store.tryAcquire(9L, "dash", 5));
        assertFalse(store.tryAcquire(7L, "dash", 5));
        store.clearMatch(7L);
        assertTrue(store.tryAcquire(7L, "dash", 5));
        assertFalse(store.tryAcquire(9L, "dash", 5));
    }
}
