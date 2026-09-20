package com.jruk8.jmanhunt.api.events;

import com.jruk8.jmanhunt.api.PlayerRole;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JMatchEventsTest {

    @Test
    void startEventExposesMatchId() {
        JMatchStartEvent event = new JMatchStartEvent(3L);
        assertEquals(3L, event.getMatchId());
    }

    @Test
    void startEventExposesLobbyAndCell() {
        JMatchStartEvent event = new JMatchStartEvent(3L, 1, OptionalLong.of(9L));

        assertEquals(3L, event.getMatchId());
        assertEquals(1, event.getOriginLobbyId());
        assertEquals(OptionalLong.of(9L), event.getCellIndex());
    }

    @Test
    void startEventDefaultsLobbyAndCell() {
        JMatchStartEvent event = new JMatchStartEvent(3L);

        assertEquals(-1, event.getOriginLobbyId());
        assertEquals(OptionalLong.empty(), event.getCellIndex());
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

    @Test
    void cancelEventExposesMatchId() {
        JMatchCancelEvent event = new JMatchCancelEvent(3L);
        assertEquals(3L, event.getMatchId());
    }

    @Test
    void joinEventExposesMatchPlayerAndRole() {
        UUID playerId = UUID.randomUUID();
        JPlayerJoinMatchEvent event = new JPlayerJoinMatchEvent(3L, playerId, PlayerRole.HUNTER);

        assertEquals(3L, event.getMatchId());
        assertEquals(playerId, event.getPlayerId());
        assertEquals(PlayerRole.HUNTER, event.getRole());
    }
}
