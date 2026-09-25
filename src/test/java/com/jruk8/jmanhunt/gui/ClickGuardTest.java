package com.jruk8.jmanhunt.gui;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Fast-click guard: one accept per player per gap, independent players,
 * and re-accept once the gap passes.
 */
class ClickGuardTest {

    @Test
    void firstClickAcceptsAndImmediateRepeatRejects() {
        AtomicLong clock = new AtomicLong(1_000);
        ClickGuard guard = new ClickGuard(clock::get);
        UUID player = UUID.randomUUID();

        assertTrue(guard.accept(player));
        assertFalse(guard.accept(player));
    }

    @Test
    void acceptReopensAfterTheGap() {
        AtomicLong clock = new AtomicLong(1_000);
        ClickGuard guard = new ClickGuard(clock::get);
        UUID player = UUID.randomUUID();

        assertTrue(guard.accept(player));
        clock.addAndGet(ClickGuard.ACCEPT_GAP_MS - 1);
        assertFalse(guard.accept(player));
        clock.addAndGet(1);
        assertTrue(guard.accept(player));
    }

    @Test
    void playersAreIndependent() {
        AtomicLong clock = new AtomicLong(1_000);
        ClickGuard guard = new ClickGuard(clock::get);

        assertTrue(guard.accept(UUID.randomUUID()));
        assertTrue(guard.accept(UUID.randomUUID()));
    }
}
