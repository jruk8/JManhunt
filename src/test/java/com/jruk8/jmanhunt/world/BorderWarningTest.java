package com.jruk8.jmanhunt.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jruk8.jmanhunt.world.border.WorldBorderService;

class BorderWarningTest {

    @Test
    void warningIsAHundredthOfAppliedSize() {
        assertEquals(100, WorldBorderService.warningDistance(10_000));
        assertEquals(200, WorldBorderService.warningDistance(20_000));
    }

    @Test
    void warningFloorsAtOneBlock() {
        assertEquals(1, WorldBorderService.warningDistance(22));
        assertEquals(1, WorldBorderService.warningDistance(2.75));
        assertEquals(12, WorldBorderService.warningDistance(10_000 / 8.0));
    }
}
