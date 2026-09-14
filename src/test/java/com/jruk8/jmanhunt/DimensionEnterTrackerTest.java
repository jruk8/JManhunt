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
                tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, UUID.randomUUID());

        assertEquals(EnumSet.of(
                DimensionEnterTracker.Fire.PER_PLAYER,
                DimensionEnterTracker.Fire.GLOBAL_FIRST), fire);
    }

    @Test
    void repeatEntryBySamePlayerFiresNothing() {
        DimensionEnterTracker tracker = new DimensionEnterTracker();
        UUID player = UUID.randomUUID();

        tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, player);

        assertTrue(tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, player).isEmpty());
    }

    @Test
    void secondPlayerFiresPerPlayerOnly() {
        DimensionEnterTracker tracker = new DimensionEnterTracker();

        tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, UUID.randomUUID());

        assertEquals(EnumSet.of(DimensionEnterTracker.Fire.PER_PLAYER),
                tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, UUID.randomUUID()));
    }

    @Test
    void netherAndEndTrackedIndependently() {
        DimensionEnterTracker tracker = new DimensionEnterTracker();
        UUID player = UUID.randomUUID();

        tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, player);

        assertEquals(EnumSet.of(
                DimensionEnterTracker.Fire.PER_PLAYER,
                DimensionEnterTracker.Fire.GLOBAL_FIRST),
                tracker.onEnter(DimensionEnterTracker.Dimension.END, player));
    }

    @Test
    void resetRestoresFirstEntrySemantics() {
        DimensionEnterTracker tracker = new DimensionEnterTracker();
        UUID player = UUID.randomUUID();

        tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, player);
        tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, UUID.randomUUID());
        tracker.reset();

        assertEquals(EnumSet.of(
                DimensionEnterTracker.Fire.PER_PLAYER,
                DimensionEnterTracker.Fire.GLOBAL_FIRST),
                tracker.onEnter(DimensionEnterTracker.Dimension.NETHER, player));
    }
}
