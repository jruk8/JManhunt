package com.jruk8.jmanhunt.match;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaveDestinationTest {

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(LeaveDestination.LOBBY, LeaveDestination.parse("lobby"));
        assertEquals(LeaveDestination.LOBBY, LeaveDestination.parse(" Lobby "));
        assertEquals(LeaveDestination.SPECTATOR, LeaveDestination.parse("SPECTATOR"));
    }

    @Test
    void parseFallsBackToSpectator() {
        assertEquals(LeaveDestination.SPECTATOR, LeaveDestination.parse("void"));
        assertEquals(LeaveDestination.SPECTATOR, LeaveDestination.parse(""));
        assertEquals(LeaveDestination.SPECTATOR, LeaveDestination.parse(null));
    }
}
