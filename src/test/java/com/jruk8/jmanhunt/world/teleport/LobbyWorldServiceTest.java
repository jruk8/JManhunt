package com.jruk8.jmanhunt.world.teleport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LobbyWorldServiceTest {

    @Test
    void lobbyWorldNameMatchesLobbyButNeverGameWorld() {
        assertTrue(LobbyWorldService.isLobbyWorldName("lobby", "lobby", "world"));
        assertFalse(LobbyWorldService.isLobbyWorldName("world", "lobby", "world"));
        assertFalse(LobbyWorldService.isLobbyWorldName("world", "world", "world"));
    }
}
