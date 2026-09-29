package com.jruk8.jmanhunt.compass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class CompassCacheTest {

    @Test
    void clampMaxTargetsHoldsOneToTwenty() {
        assertEquals(1, CompassCache.clampMaxTargets(-5));
        assertEquals(1, CompassCache.clampMaxTargets(0));
        assertEquals(1, CompassCache.clampMaxTargets(1));
        assertEquals(5, CompassCache.clampMaxTargets(5));
        assertEquals(20, CompassCache.clampMaxTargets(20));
        assertEquals(20, CompassCache.clampMaxTargets(99));
    }

    @Test
    void freshHolderHasNoSpots() {
        CompassCache cache = new CompassCache();

        assertTrue(cache.spotsFor(UUID.randomUUID()).isEmpty());
    }

    @Test
    void replaceStoresHunterAndRunnerSnapshots() {
        CompassCache cache = new CompassCache();
        UUID holder = UUID.randomUUID();
        UUID hunter = UUID.randomUUID();
        UUID runner = UUID.randomUUID();
        Location hunterSpot = new Location(null, 1.0, 64.0, 1.0);
        Location runnerSpot = new Location(null, 2.0, 64.0, 2.0);

        cache.replace(holder, new Location(null, 0.0, 64.0, 0.0),
                List.of(new CompassSnapshot(hunter, hunterSpot)),
                List.of(new CompassSnapshot(runner, runnerSpot)));

        assertEquals(hunterSpot, cache.spotsFor(holder).get(hunter));
        assertEquals(runnerSpot, cache.spotsFor(holder).get(runner));
    }

    @Test
    void replaceDropsStaleSnapshots() {
        CompassCache cache = new CompassCache();
        UUID holder = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        cache.replace(holder, new Location(null, 0.0, 64.0, 0.0),
                List.of(new CompassSnapshot(stale, new Location(null, 1.0, 64.0, 1.0))),
                List.of());

        cache.replace(holder, new Location(null, 9.0, 64.0, 9.0), List.of(), List.of());

        assertTrue(cache.spotsFor(holder).isEmpty());
    }

    @Test
    void replaceStoresAndClearDropsHolderSpot() {
        CompassCache cache = new CompassCache();
        UUID holder = UUID.randomUUID();
        Location spot = new Location(null, 4.0, 64.0, 4.0);
        cache.replace(holder, spot, List.of(), List.of());

        assertEquals(spot, cache.holderSpotFor(holder));

        cache.clear(holder);

        assertNull(cache.holderSpotFor(holder));
    }

    @Test
    void clearDropsHolderSnapshots() {
        CompassCache cache = new CompassCache();
        UUID holder = UUID.randomUUID();
        cache.replace(holder, new Location(null, 0.0, 64.0, 0.0),
                List.of(new CompassSnapshot(UUID.randomUUID(), new Location(null, 1.0, 64.0, 1.0))),
                List.of());

        cache.clear(holder);

        assertTrue(cache.spotsFor(holder).isEmpty());
    }
}
