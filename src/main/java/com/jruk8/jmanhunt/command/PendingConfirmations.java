package com.jruk8.jmanhunt.command;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Run-twice confirmation for dangerous commands (match leave, AFK role
 * changes): the first call arms a key, a matching second call within the
 * timeout confirms it. Keys are namespaced by the caller.
 */
public final class PendingConfirmations {
    /** Default window in which the second run must arrive: 10 seconds. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 10_000L;

    private final Map<String, Long> expiries = new HashMap<>();
    private final LongSupplier clock;

    public PendingConfirmations() {
        this(System::currentTimeMillis);
    }

    PendingConfirmations(LongSupplier clock) {
        this.clock = clock;
    }

    /** Arms (first call) or confirms (second call in time). True only when confirmed. */
    public boolean confirm(String key) {
        return confirm(key, DEFAULT_TIMEOUT_MILLIS);
    }

    /** Arms (first call) or confirms (second call within the timeout). True only when confirmed. */
    public boolean confirm(String key, long timeoutMillis) {
        prune();
        Long expiry = expiries.get(key);
        if (expiry != null && expiry > clock.getAsLong()) {
            expiries.remove(key);
            return true;
        }
        expiries.put(key, clock.getAsLong() + timeoutMillis);
        return false;
    }

    private void prune() {
        long now = clock.getAsLong();
        expiries.values().removeIf(expiry -> expiry <= now);
    }
}
