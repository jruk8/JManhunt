package com.jruk8.jmanhunt.api.events;

import com.jruk8.jmanhunt.api.PlayerRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JMatchEventsTest {

    @Test
    void startEventExposesMatchId() {
        JMatchStartEvent event = new JMatchStartEvent(3L);
        assertEquals(3L, event.getMatchId());
    }

    @Test
    void beginEventExposesMatchId() {
        JGameBeginEvent event = new JGameBeginEvent(3L);
        assertEquals(3L, event.getMatchId());
    }

    @Test
    void endEventExposesMatchIdAndWinner() {
        JMatchEndEvent event = new JMatchEndEvent(3L, PlayerRole.SPEEDRUNNER);
        assertEquals(3L, event.getMatchId());
        assertEquals(PlayerRole.SPEEDRUNNER, event.getWinner());
    }
}