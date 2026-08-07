package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaitingReminderTest {

    @Test
    void clampsZeroToMinimum() {
        assertEquals(5, WaitingReminder.clampDelay(0));
    }

    @Test
    void clampsValuesBelowMinimum() {
        assertEquals(5, WaitingReminder.clampDelay(4));
        assertEquals(5, WaitingReminder.clampDelay(1));
    }

    @Test
    void preservesValuesAtOrAboveMinimum() {
        assertEquals(5, WaitingReminder.clampDelay(5));
        assertEquals(30, WaitingReminder.clampDelay(30));
        assertEquals(45, WaitingReminder.clampDelay(45));
    }

    @Test
    void preservesIndefiniteDelay() {
        assertEquals(-1, WaitingReminder.clampDelay(-1));
    }

    @Test
    void slicesThirtySecondsIntoThree() {
        assertEquals(10, WaitingReminder.sliceSeconds(30));
    }

    @Test
    void slicesFortyFiveSecondsIntoThree() {
        assertEquals(15, WaitingReminder.sliceSeconds(45));
    }

    @Test
    void slicesMinimumDelay() {
        assertEquals(2, WaitingReminder.sliceSeconds(5));
    }

    @Test
    void neverReturnsZero() {
        assertEquals(1, WaitingReminder.sliceSeconds(1));
    }
}