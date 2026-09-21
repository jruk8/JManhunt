package com.jruk8.jmanhunt.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BorderWarningTest {

    @Test
    void warningIsAHundredthOfAppliedSize() {
        assertEquals(100, WorldEngineService.warningDistance(10_000));
        assertEquals(200, WorldEngineService.warningDistance(20_000));
    }

    @Test
    void warningFloorsAtOneBlock() {
        assertEquals(1, WorldEngineService.warningDistance(22));
        assertEquals(1, WorldEngineService.warningDistance(2.75));
        assertEquals(12, WorldEngineService.warningDistance(10_000 / 8.0));
    }
}
