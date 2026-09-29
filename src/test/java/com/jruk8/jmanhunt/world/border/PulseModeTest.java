package com.jruk8.jmanhunt.world.border;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PulseModeTest {

    @Test
    void parseMatchesSineWaveCaseInsensitively() {
        assertEquals(PulseMode.SINE_WAVE, PulseMode.parse("SINE_WAVE"));
        assertEquals(PulseMode.SINE_WAVE, PulseMode.parse("sine_wave"));
    }

    @Test
    void parseFallsBackToSineWave() {
        assertEquals(PulseMode.INTERVAL, PulseMode.parse("INTERVAL"));
        assertEquals(PulseMode.SINE_WAVE, PulseMode.parse("bogus"));
        assertEquals(PulseMode.SINE_WAVE, PulseMode.parse(null));
    }
}
