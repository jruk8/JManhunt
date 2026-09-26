package com.jruk8.jmanhunt.world.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

/** StructureSelectionPolicy: hit, miss budget, and last-pass fallback. */
class StructureSelectionPolicyTest {

    @Test
    void hitReturnsImmediately() {
        StructureSelectionPolicy policy = new StructureSelectionPolicy(3);
        CellOrigin origin = new CellOrigin(1, 2, 3);

        assertEquals(origin, policy.record(origin, true));
    }

    @Test
    void missesCountDownThenReturnLastRawPass() {
        StructureSelectionPolicy policy = new StructureSelectionPolicy(3);
        CellOrigin first = new CellOrigin(1, 1, 1);
        CellOrigin second = new CellOrigin(2, 2, 2);
        CellOrigin third = new CellOrigin(3, 3, 3);

        assertNull(policy.record(first, false));
        assertNull(policy.record(second, false));
        assertEquals(third, policy.record(third, false));
        assertEquals(third, policy.lastRawPass());
    }

    @Test
    void singleAttemptFallsBackAtOnce() {
        StructureSelectionPolicy policy = new StructureSelectionPolicy(1);
        CellOrigin origin = new CellOrigin(9, 9, 9);

        assertEquals(origin, policy.record(origin, false));
    }

    @Test
    void lastRawPassTracksLatestMiss() {
        StructureSelectionPolicy policy = new StructureSelectionPolicy(5);

        assertNull(policy.lastRawPass());
        CellOrigin first = new CellOrigin(1, 1, 1);
        CellOrigin second = new CellOrigin(2, 2, 2);
        policy.record(first, false);
        assertEquals(first, policy.lastRawPass());
        policy.record(second, false);
        assertEquals(second, policy.lastRawPass());
    }
}
