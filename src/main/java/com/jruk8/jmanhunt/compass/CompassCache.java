package com.jruk8.jmanhunt.compass;

import org.bukkit.Location;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-holder snapshots of trackable player locations. Refresh events
 * (automatic, right-click, analysis, instance, reconcile) are the only
 * writers; left-click cycling and shift-left switching only ever read
 * from here, so browsing targets can never fetch a live position.
 */
public final class CompassCache {
    /** Lowest supported max-targets value. */
    public static final int MIN_TARGETS = 1;
    /** Highest supported max-targets value. */
    public static final int MAX_TARGETS = 20;

    /** Holder id to target id to snapshotted location. */
    private final Map<UUID, Map<UUID, Location>> spots = new HashMap<>();

    /** max-targets clamped to its supported range. Pure for tests. */
    public static int clampMaxTargets(int raw) {
        return Math.clamp(raw, MIN_TARGETS, MAX_TARGETS);
    }

    /** Replaces one holder's snapshots with the freshly refreshed sets. */
    public void replace(UUID holderId, List<CompassSnapshot> hunters,
            List<CompassSnapshot> runners) {
        Map<UUID, Location> fresh = new HashMap<>();
        for (CompassSnapshot snapshot : hunters) {
            fresh.put(snapshot.id(), snapshot.location());
        }
        for (CompassSnapshot snapshot : runners) {
            fresh.put(snapshot.id(), snapshot.location());
        }
        spots.put(holderId, fresh);
    }

    /** Snapshots for one holder; empty when never refreshed. */
    public Map<UUID, Location> spotsFor(UUID holderId) {
        return spots.getOrDefault(holderId, Map.of());
    }

    /** Drops one holder's snapshots, for example on match leave. */
    public void clear(UUID holderId) {
        spots.remove(holderId);
    }
}
