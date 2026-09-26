package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.lobby.SubLobby;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OldestSubLobbyTest {

    @Test
    void picksLowestNumberedLiveSublobby() {
        GameInstance first = live(1L, 0);
        GameInstance second = live(2L, 3);

        assertEquals(Optional.of(first), GameManager.oldestSubLobby(List.of(second, first)));
        assertEquals(Optional.of(first), GameManager.oldestSubLobby(List.of(first, second)));
    }

    @Test
    void skipsEndingInactiveAndParentHostedMatches() {
        GameInstance ending = live(1L, 0);
        ending.setEnding(true);
        GameInstance inactive = live(2L, 1);
        inactive.setActive(false);
        GameInstance parentHosted = new GameInstance(3L, 0, OptionalLong.empty(), 1_000L);
        parentHosted.setActive(true);
        GameInstance liveMatch = live(4L, 5);

        assertEquals(Optional.of(liveMatch), GameManager.oldestSubLobby(
                List.of(ending, inactive, parentHosted, liveMatch)));
    }

    @Test
    void emptyWhenNothingRuns() {
        assertEquals(Optional.empty(), GameManager.oldestSubLobby(List.of()));
    }

    private static GameInstance live(long matchId, int subId) {
        GameInstance instance = new GameInstance(matchId, 0, OptionalLong.empty(), 1_000L);
        instance.setActive(true);
        instance.setSubLobby(new SubLobby(0, subId));
        return instance;
    }
}
