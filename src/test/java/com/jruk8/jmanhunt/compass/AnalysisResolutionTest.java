package com.jruk8.jmanhunt.compass;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SUCCESS cost stamping: failures never touch the click clock. */
class AnalysisResolutionTest {

    @Test
    void failedSuccessCostLeavesClickClockUntouched() {
        AnalysisHost host = mock(AnalysisHost.class);
        Player holder = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(host.trySuccessCost(holder)).thenReturn(false);
        Map<UUID, Long> sharedClicks = new HashMap<>();

        assertFalse(AnalysisResolution.stampClickOnSuccessfulCost(host, sharedClicks, holder, id));

        assertTrue(sharedClicks.isEmpty());
        verify(host, times(1)).cancelAnalysisSnapshots(id);
    }

    @Test
    void paidSuccessCostStampsClickClock() {
        AnalysisHost host = mock(AnalysisHost.class);
        Player holder = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(host.trySuccessCost(holder)).thenReturn(true);
        Map<UUID, Long> sharedClicks = new HashMap<>();

        assertTrue(AnalysisResolution.stampClickOnSuccessfulCost(host, sharedClicks, holder, id));

        assertTrue(sharedClicks.containsKey(id));
    }
}
