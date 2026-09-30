package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.match.lifecycle.MatchFinishService;
import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BucketWinnerTest {

    @Test
    void emptyHunterBucketCrownsSpeedrunners() {
        assertEquals(Optional.of(Role.SPEEDRUNNER), MatchFinishService.bucketWinner(0, 2));
    }

    @Test
    void emptyRunnerBucketCrownsHunters() {
        assertEquals(Optional.of(Role.HUNTER), MatchFinishService.bucketWinner(3, 0));
    }

    @Test
    void fullBucketsHaveNoWinner() {
        assertEquals(Optional.empty(), MatchFinishService.bucketWinner(2, 2));
    }

    @Test
    void doubleEmptyBucketHasNoWinner() {
        assertEquals(Optional.empty(), MatchFinishService.bucketWinner(0, 0));
    }
}
