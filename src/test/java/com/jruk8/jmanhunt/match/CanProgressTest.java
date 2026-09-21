package com.jruk8.jmanhunt.match;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanProgressTest {

    @Test
    void bothSidesFieldedProgresses() {
        assertTrue(GameManager.canProgress(1, 1));
        assertTrue(GameManager.canProgress(3, 2));
    }

    @Test
    void emptyHunterBucketCannotProgress() {
        assertFalse(GameManager.canProgress(0, 2));
    }

    @Test
    void emptyRunnerBucketCannotProgress() {
        assertFalse(GameManager.canProgress(3, 0));
    }

    @Test
    void doubleEmptyBucketCannotProgress() {
        assertFalse(GameManager.canProgress(0, 0));
    }
}
