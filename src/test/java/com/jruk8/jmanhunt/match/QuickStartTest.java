package com.jruk8.jmanhunt.match;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QuickStartTest {

    @Test
    void zeroPercentStillAssignsOneSpeedrunner() {
        assertEquals(1, GameManager.quickStartSpeedrunnerCount(8, 0));
    }

    @Test
    void fiftyPercentOfSixteenIsEight() {
        assertEquals(8, GameManager.quickStartSpeedrunnerCount(16, 50));
    }

    @Test
    void fractionalResultsRoundToNearest() {
        assertEquals(2, GameManager.quickStartSpeedrunnerCount(3, 50));
    }

    @Test
    void fullPercentAssignsEveryone() {
        assertEquals(4, GameManager.quickStartSpeedrunnerCount(4, 100));
    }
}
