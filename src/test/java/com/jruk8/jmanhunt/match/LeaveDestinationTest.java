package com.jruk8.jmanhunt.match;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaveDestinationTest {

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(GameManager.LeaveDestination.LOBBY, GameManager.LeaveDestination.parse("lobby"));
        assertEquals(GameManager.LeaveDestination.LOBBY, GameManager.LeaveDestination.parse(" Lobby "));
        assertEquals(GameManager.LeaveDestination.SPECTATOR, GameManager.LeaveDestination.parse("SPECTATOR"));
    }

    @Test
    void parseFallsBackToSpectator() {
        assertEquals(GameManager.LeaveDestination.SPECTATOR, GameManager.LeaveDestination.parse("void"));
        assertEquals(GameManager.LeaveDestination.SPECTATOR, GameManager.LeaveDestination.parse(""));
        assertEquals(GameManager.LeaveDestination.SPECTATOR, GameManager.LeaveDestination.parse(null));
    }
}
