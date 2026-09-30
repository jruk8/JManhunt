package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.match.lifecycle.MatchFinishService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanProgressTest {

    @Test
    void bothSidesFieldedProgresses() {
        assertTrue(MatchFinishService.canProgress(1, 1));
        assertTrue(MatchFinishService.canProgress(3, 2));
    }

    @Test
    void emptyHunterBucketCannotProgress() {
        assertFalse(MatchFinishService.canProgress(0, 2));
    }

    @Test
    void emptyRunnerBucketCannotProgress() {
        assertFalse(MatchFinishService.canProgress(3, 0));
    }

    @Test
    void doubleEmptyBucketCannotProgress() {
        assertFalse(MatchFinishService.canProgress(0, 0));
    }
}
