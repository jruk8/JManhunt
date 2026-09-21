package com.jruk8.jmanhunt.match;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RespawnDelayTest {
    @Test
    void delayAppliesOnlyWhenEnabledAndPositive() {
        assertEquals(15, GameplayListener.effectiveRespawnDelay(true, 15));
        assertEquals(0, GameplayListener.effectiveRespawnDelay(false, 15));
        assertEquals(0, GameplayListener.effectiveRespawnDelay(true, 0));
        assertEquals(0, GameplayListener.effectiveRespawnDelay(true, -1));
        assertEquals(0, GameplayListener.effectiveRespawnDelay(false, -1));
    }
}
