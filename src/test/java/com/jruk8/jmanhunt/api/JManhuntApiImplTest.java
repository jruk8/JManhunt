package com.jruk8.jmanhunt.api;

import com.jruk8.jmanhunt.GameManager;
import com.jruk8.jmanhunt.PlayerStateStore;
import com.jruk8.jmanhunt.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JManhuntApiImplTest {

    @Mock
    private GameManager game;

    @Mock
    private PlayerStateStore playerStates;

    private JManhuntApiImpl api;

    @BeforeEach
    void setUp() {
        api = new JManhuntApiImpl(game, playerStates);
    }

    @Test
    void reportsMatchStateFromGameManager() {
        when(game.isActive()).thenReturn(true);
        when(game.isGameBegun()).thenReturn(true);
        when(game.isEnding()).thenReturn(false);
        when(game.matchId()).thenReturn(7L);

        assertTrue(api.isMatchActive());
        assertTrue(api.hasGameBegun());
        assertFalse(api.isMatchEnding());
        assertEquals(7L, api.getMatchId());
    }

    @Test
    void mapsNoRoleToNone() {
        UUID id = UUID.randomUUID();
        when(playerStates.role(id)).thenReturn(Role.NONE);

        assertEquals(PlayerRole.NONE, api.getRole(id));
        assertFalse(api.isParticipant(id));
    }

    @Test
    void mapsHunterRoleToParticipant() {
        UUID id = UUID.randomUUID();
        when(playerStates.role(id)).thenReturn(Role.HUNTER);
        assertEquals(PlayerRole.HUNTER, api.getRole(id));
        assertTrue(api.isParticipant(id));
    }

    @Test
    void mapsSpeedrunnerRoleToParticipant() {
        UUID id = UUID.randomUUID();
        when(playerStates.role(id)).thenReturn(Role.SPEEDRUNNER);
        assertEquals(PlayerRole.SPEEDRUNNER, api.getRole(id));
        assertTrue(api.isParticipant(id));
    }

    @Test
    void mapsAfkRoleToNonParticipant() {
        UUID id = UUID.randomUUID();
        when(playerStates.role(id)).thenReturn(Role.AFK);
        assertEquals(PlayerRole.AFK, api.getRole(id));
        assertFalse(api.isParticipant(id));
    }

    @Test
    void nullPlayerIdResolvesToNone() {
        assertEquals(PlayerRole.NONE, api.getRole(null));
        assertFalse(api.isParticipant(null));
    }
}