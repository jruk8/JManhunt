package com.jruk8.jmanhunt.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jruk8.jmanhunt.world.border.BorderMode;

class BorderModeTest {

    @Test
    void noMatchesMeansNoBorders() {
        assertEquals(BorderMode.NONE, BorderMode.resolve(0, true, true));
    }

    @Test
    void singleMatchUsesRealBorder() {
        assertEquals(BorderMode.SINGLE, BorderMode.resolve(1, true, true));
    }

    @Test
    void concurrentMatchesUsePseudoBorders() {
        assertEquals(BorderMode.MULTI, BorderMode.resolve(2, true, true));
        assertEquals(BorderMode.MULTI, BorderMode.resolve(5, true, true));
    }

    @Test
    void disabledEngineOrBorderMeansNone() {
        assertEquals(BorderMode.NONE, BorderMode.resolve(1, false, true));
        assertEquals(BorderMode.NONE, BorderMode.resolve(2, true, false));
        assertEquals(BorderMode.NONE, BorderMode.resolve(2, false, false));
    }
}
