package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionEnterTrackerTest {

    @Test
    void firstEntryFiresPerPlayerAndGlobal() {
        DimensionEnterTracker tracker = new DimensionEnterTracker();

        EnumSet<DimensionEnterTracker.Fire> fire =
                tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, UUID.randomUUID(), 1L);

        assertEquals(EnumSet.of(
                DimensionEnterTracker.Fire.PER_PLAYER,
                DimensionEnterTracker.Fire.GLOBAL_FIRST), fire);
    }

    @Test
    void repeatEntryBySamePlayerFiresNothing() {
        DimensionEnterTracker tracker = new DimensionEnterTracker();
        UUID player = UUID.randomUUID();

        tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, player, 1L);

        assertTrue(tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, player, 1L).isEmpty());
    }

    @Test
    void secondPlayerFiresPerPlayerOnly() {
        DimensionEnterTracker tracker = new DimensionEnterTracker();

        tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, UUID.randomUUID(), 1L);

        assertEquals(EnumSet.of(DimensionEnterTracker.Fire.PER_PLAYER),
                tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, UUID.randomUUID(), 1L));
    }

    @Test
    void netherAndEndTrackedIndependently() {
        DimensionEnterTracker tracker = new DimensionEnterTracker();
        UUID player = UUID.randomUUID();

        tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, player, 1L);

        assertEquals(EnumSet.of(
                DimensionEnterTracker.Fire.PER_PLAYER,
                DimensionEnterTracker.Fire.GLOBAL_FIRST),
                tracker.onEnter(DimensionEnterTracker.Dimension.END, player, 1L));
    }

    @Test
    void dropMatchRestoresFirstEntrySemantics() {
        DimensionEnterTracker tracker = new DimensionEnterTracker();
        UUID player = UUID.randomUUID();

        tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, player, 1L);
        tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, UUID.randomUUID(), 1L);
        tracker.dropMatch(1L);

        assertEquals(EnumSet.of(
                DimensionEnterTracker.Fire.PER_PLAYER,
                DimensionEnterTracker.Fire.GLOBAL_FIRST),
                tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, player, 1L));
    }

    @Test
    void concurrentMatchesTrackIndependently() {
        DimensionEnterTracker tracker = new DimensionEnterTracker();
        UUID player = UUID.randomUUID();

        tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, player, 1L);

        assertEquals(EnumSet.of(
                DimensionEnterTracker.Fire.PER_PLAYER,
                DimensionEnterTracker.Fire.GLOBAL_FIRST),
                tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, player, 2L));
        assertTrue(tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, player, 1L).isEmpty());
    }
}
