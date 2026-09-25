package com.jruk8.jmanhunt.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Fast-click guard for menu slots.
 *
 * <p>Accepts at most one click per player per gap so rapid clicking never
 * toggles twice or fires ghost actions. Main thread only, like every other
 * inventory click path.
 */
public final class ClickGuard {

    /** Minimum gap between two accepted clicks of one player. */
    public static final long ACCEPT_GAP_MS = 120;

    /** Idle entries older than this are dropped opportunistically. */
    private static final long RETIRE_MS = 60_000;

    private final LongSupplier clock;
    private final Map<UUID, Long> lastAccepted = new HashMap<>();

    public ClickGuard(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * True when the click may run; records the accept. Rejects clicks
     * arriving within the gap of the player's last accepted click.
     */
    public boolean accept(UUID playerId) {
        long now = clock.getAsLong();
        Long last = lastAccepted.get(playerId);
        if (last != null && now - last < ACCEPT_GAP_MS) {
            return false;
        }
        lastAccepted.put(playerId, now);
        if (lastAccepted.size() > 64) {
            lastAccepted.entrySet().removeIf(entry -> now - entry.getValue() > RETIRE_MS);
        }
        return true;
    }
}
