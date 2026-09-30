package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.lobby.SubLobby;
import com.jruk8.jmanhunt.match.GameInstance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferTargetTest {

    @Test
    void oldestSiblingWinsAndEndingMatchIsExcluded() {
        GameInstance ending = live(1L, 0);
        GameInstance oldest = live(2L, 1);
        GameInstance newest = live(3L, 5);

        assertEquals(Optional.of(oldest), MatchFinishService.transferTarget(
                List.of(newest, ending, oldest), 1L));
    }

    @Test
    void emptyWhenNoSiblingRuns() {
        GameInstance ending = live(1L, 0);

        assertEquals(Optional.empty(), MatchFinishService.transferTarget(List.of(ending), 1L));
        assertEquals(Optional.empty(), MatchFinishService.transferTarget(List.of(), 9L));
    }

    @Test
    void skipsEndingInactiveAndParentHostedSiblings() {
        GameInstance ending = live(1L, 0);
        GameInstance finishing = live(2L, 1);
        finishing.setEnding(true);
        GameInstance inactive = live(3L, 2);
        inactive.setActive(false);
        GameInstance parentHosted = new GameInstance(4L, 0, OptionalLong.empty(), 1_000L);
        parentHosted.setActive(true);
        GameInstance oldest = live(5L, 7);

        assertEquals(Optional.of(oldest), MatchFinishService.transferTarget(
                List.of(ending, finishing, inactive, parentHosted, oldest), 1L));
    }

    private static GameInstance live(long matchId, int subId) {
        GameInstance instance = new GameInstance(matchId, 0, OptionalLong.empty(), 1_000L);
        instance.setActive(true);
        instance.setSubLobby(new SubLobby(0, subId));
        return instance;
    }
}
