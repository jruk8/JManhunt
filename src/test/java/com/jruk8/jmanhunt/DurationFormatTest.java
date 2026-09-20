package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationFormatTest {

    @Test
    void zeroRendersAsZeroSeconds() {
        assertEquals("0s", DurationFormat.format(0));
    }

    @Test
    void negativeClampsToZero() {
        assertEquals("0s", DurationFormat.format(-5));
    }

    @Test
    void secondsOnly() {
        assertEquals("48s", DurationFormat.format(48));
    }

    @Test
    void minutesAndSeconds() {
        assertEquals("19m 25s", DurationFormat.format(19 * 60 + 25));
    }

    @Test
    void hoursMinutesAndSeconds() {
        assertEquals("1h 2m 3s", DurationFormat.format(3600 + 120 + 3));
    }

    @Test
    void zeroUnitsAreOmitted() {
        assertEquals("1h", DurationFormat.format(3600));
        assertEquals("1h 5s", DurationFormat.format(3605));
    }

    @Test
    void daysExtendThePattern() {
        assertEquals("1d 1h", DurationFormat.format(90000));
    }
}
