package com.jruk8.jmanhunt.command;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Match-owned cooldown stamps behind {@code <cooldown>},
 * {@code <cooldown.get>}, and {@code <cooldown.reset>}: match id
 * to key to epoch-millis. The game manager owns one; tag runs
 * reach it through {@link TagContext#cooldowns()}. The clock is
 * injectable for tests. No Bukkit types.
 */
public final class TagCooldownStore {

    private final Map<Long, Map<String, Long>> stamps = new HashMap<>();
    private final LongSupplier clock;

    /** Live store reading the wall clock. */
    public TagCooldownStore() {
        this(System::currentTimeMillis);
    }

    /** Store reading the given epoch-millis clock. */
    public TagCooldownStore(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Stamps the key now and returns true when no stamp exists or
     * the window elapsed; returns false without touching the stamp
     * while the key still cools down.
     */
    public boolean tryAcquire(long matchId, String key, double seconds) {
        long now = clock.getAsLong();
        long window = (long) (seconds * 1000);
        Map<String, Long> match = stamps.computeIfAbsent(matchId, id -> new HashMap<>());
        Long stamp = match.get(key);
        if (stamp != null && now - stamp < window) {
            return false;
        }
        match.put(key, now);
        return true;
    }

    /** Remaining window seconds, or 0 when ready or unknown. */
    public double remainingSeconds(long matchId, String key, double seconds) {
        Map<String, Long> match = stamps.get(matchId);
        Long stamp = match == null ? null : match.get(key);
        if (stamp == null) {
            return 0;
        }
        double left = seconds - (clock.getAsLong() - stamp) / 1000.0;
        return Math.max(0, left);
    }

    /** Clears one key; unknown keys clear silently. */
    public void reset(long matchId, String key) {
        Map<String, Long> match = stamps.get(matchId);
        if (match != null) {
            match.remove(key);
        }
    }

    /** Drops every stamp of one match. */
    public void clearMatch(long matchId) {
        stamps.remove(matchId);
    }
}
