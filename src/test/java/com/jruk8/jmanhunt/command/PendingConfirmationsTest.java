package com.jruk8.jmanhunt.command;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingConfirmationsTest {

    @Test
    void secondRunWithinTimeoutConfirms() {
        AtomicLong clock = new AtomicLong(1_000L);
        PendingConfirmations confirms = new PendingConfirmations(clock::get);

        assertFalse(confirms.confirm("key"));
        clock.set(1_000L + PendingConfirmations.DEFAULT_TIMEOUT_MILLIS - 1L);
        assertTrue(confirms.confirm("key"));
    }

    @Test
    void expiredArmStartsOver() {
        AtomicLong clock = new AtomicLong(1_000L);
        PendingConfirmations confirms = new PendingConfirmations(clock::get);

        assertFalse(confirms.confirm("key"));
        clock.set(1_000L + PendingConfirmations.DEFAULT_TIMEOUT_MILLIS + 1L);
        assertFalse(confirms.confirm("key"));
    }

    @Test
    void keysAreIndependent() {
        AtomicLong clock = new AtomicLong(1_000L);
        PendingConfirmations confirms = new PendingConfirmations(clock::get);

        assertFalse(confirms.confirm("one"));
        assertFalse(confirms.confirm("two"));
        assertTrue(confirms.confirm("two"));
        assertTrue(confirms.confirm("one"));
    }
}
