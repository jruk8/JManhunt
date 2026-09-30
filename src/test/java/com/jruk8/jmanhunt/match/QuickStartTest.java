package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.match.lifecycle.QuickStartService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QuickStartTest {

    @Test
    void zeroPercentStillAssignsOneSpeedrunner() {
        assertEquals(1, QuickStartService.quickStartSpeedrunnerCount(8, 0));
    }

    @Test
    void fiftyPercentOfSixteenIsEight() {
        assertEquals(8, QuickStartService.quickStartSpeedrunnerCount(16, 50));
    }

    @Test
    void fractionalResultsRoundToNearest() {
        assertEquals(2, QuickStartService.quickStartSpeedrunnerCount(3, 50));
    }

    @Test
    void fullPercentAssignsEveryone() {
        assertEquals(4, QuickStartService.quickStartSpeedrunnerCount(4, 100));
    }
}
