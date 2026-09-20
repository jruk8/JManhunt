package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapLimitsTest {

    @Test
    void negativesMeanUncapped() {
        assertEquals(CapLimits.UNCAPPED, CapLimits.effectiveCap(-1));
        assertEquals(CapLimits.UNCAPPED, CapLimits.effectiveCap(-99));
    }

    @Test
    void zeroClampsToMinimumOne() {
        assertEquals(1, CapLimits.effectiveCap(0));
    }

    @Test
    void positivesPassThrough() {
        assertEquals(3, CapLimits.effectiveCap(3));
    }

    @Test
    void uncappedAlwaysAllows() {
        assertTrue(CapLimits.allows(99, -1));
    }

    @Test
    void underCapAllows() {
        assertTrue(CapLimits.allows(2, 3));
    }

    @Test
    void atCapDenies() {
        assertFalse(CapLimits.allows(3, 3));
    }

    @Test
    void overflowDenies() {
        assertFalse(CapLimits.allows(5, 3));
    }
}
