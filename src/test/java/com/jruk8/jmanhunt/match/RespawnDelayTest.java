package com.jruk8.jmanhunt.match;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jruk8.jmanhunt.match.listeners.PlayerRespawnListener;

class RespawnDelayTest {
    @Test
    void delayAppliesOnlyWhenEnabledAndPositive() {
        assertEquals(15, PlayerRespawnListener.effectiveRespawnDelay(true, 15));
        assertEquals(0, PlayerRespawnListener.effectiveRespawnDelay(false, 15));
        assertEquals(0, PlayerRespawnListener.effectiveRespawnDelay(true, 0));
        assertEquals(0, PlayerRespawnListener.effectiveRespawnDelay(true, -1));
        assertEquals(0, PlayerRespawnListener.effectiveRespawnDelay(false, -1));
    }
}
