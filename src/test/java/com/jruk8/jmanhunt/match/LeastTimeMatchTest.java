package com.jruk8.jmanhunt.match;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LeastTimeMatchTest {

    @Test
    void picksNewestLiveMatch() {
        GameInstance oldMatch = live(1L, 1_000L);
        GameInstance newMatch = live(2L, 2_000L);

        assertEquals(Optional.of(newMatch), GameManager.leastTimeMatch(List.of(oldMatch, newMatch)));
        assertEquals(Optional.of(newMatch), GameManager.leastTimeMatch(List.of(newMatch, oldMatch)));
    }

    @Test
    void skipsEndingAndInactiveMatches() {
        GameInstance ending = live(1L, 3_000L);
        ending.setEnding(true);
        GameInstance inactive = new GameInstance(2L, 0, OptionalLong.empty(), 4_000L);
        inactive.setActive(false);
        GameInstance liveMatch = live(3L, 1_000L);

        assertEquals(Optional.of(liveMatch),
                GameManager.leastTimeMatch(List.of(ending, inactive, liveMatch)));
    }

    @Test
    void emptyWhenNothingRuns() {
        assertEquals(Optional.empty(), GameManager.leastTimeMatch(List.of()));
    }

    private static GameInstance live(long matchId, long startedAt) {
        GameInstance instance = new GameInstance(matchId, 0, OptionalLong.empty(), startedAt);
        instance.setActive(true);
        return instance;
    }
}
