package com.jruk8.jmanhunt.api;

import com.jruk8.jmanhunt.GameInstance;
import com.jruk8.jmanhunt.GameManager;
import com.jruk8.jmanhunt.Lobby;
import com.jruk8.jmanhunt.LobbyService;
import com.jruk8.jmanhunt.PlayerStateStore;
import com.jruk8.jmanhunt.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
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

    @Mock
    private LobbyService lobbies;

    private JManhuntApiImpl api;

    @BeforeEach
    void setUp() {
        api = new JManhuntApiImpl(game, playerStates, lobbies);
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

    @Test
    void listsLiveMatchIdsOldestFirst() {
        when(game.liveInstances()).thenReturn(List.of(
                new GameInstance(3L, 0, OptionalLong.of(9L), 0L),
                new GameInstance(5L, 1, OptionalLong.empty(), 0L)));

        assertEquals(List.of(3L, 5L), api.getLiveMatchIds());
    }

    @Test
    void reportsPerMatchState() {
        GameInstance begun = new GameInstance(3L, 0, OptionalLong.empty(), 0L);
        begun.setBegun(true);
        GameInstance ending = new GameInstance(5L, 1, OptionalLong.empty(), 0L);
        ending.setEnding(true);
        when(game.instance(3L)).thenReturn(Optional.of(begun));
        when(game.instance(5L)).thenReturn(Optional.of(ending));
        when(game.instance(9L)).thenReturn(Optional.empty());

        assertTrue(api.isMatchLive(3L));
        assertTrue(api.hasMatchBegun(3L));
        assertFalse(api.isMatchEnding(3L));
        assertTrue(api.isMatchEnding(5L));
        assertFalse(api.isMatchLive(9L));
        assertFalse(api.hasMatchBegun(9L));
        assertFalse(api.isMatchEnding(9L));
    }

    @Test
    void resolvesPlayerMatchAndMembers() {
        UUID hunter = UUID.randomUUID();
        UUID runner = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        GameInstance instance = new GameInstance(3L, 2, OptionalLong.empty(), 0L);
        instance.activate(hunter);
        instance.activate(runner);
        when(game.instanceOf(hunter)).thenReturn(Optional.of(instance));
        when(game.instanceOf(outsider)).thenReturn(Optional.empty());
        when(game.instance(3L)).thenReturn(Optional.of(instance));
        when(game.instance(9L)).thenReturn(Optional.empty());

        assertEquals(3L, api.getMatchId(hunter));
        assertEquals(-1L, api.getMatchId(outsider));
        assertEquals(-1L, api.getMatchId(null));
        assertEquals(Set.of(hunter, runner), api.getMatchPlayers(3L));
        assertEquals(Set.of(), api.getMatchPlayers(9L));
        assertEquals(2, api.getOriginLobbyId(3L));
        assertEquals(-1, api.getOriginLobbyId(9L));
    }

    @Test
    void resolvesLobbies() {
        UUID member = UUID.randomUUID();
        UUID lobbyLess = UUID.randomUUID();
        Lobby lobby = new Lobby(2);
        lobby.add(member);
        when(lobbies.lobbyIds()).thenReturn(Set.of(0, 2));
        when(lobbies.get(2)).thenReturn(Optional.of(lobby));
        when(lobbies.get(9)).thenReturn(Optional.empty());
        when(lobbies.lobbyOf(member)).thenReturn(Optional.of(lobby));
        when(lobbies.lobbyOf(lobbyLess)).thenReturn(Optional.empty());

        assertEquals(Set.of(0, 2), api.getLobbyIds());
        assertEquals(Set.of(member), api.getLobbyPlayers(2));
        assertEquals(Set.of(), api.getLobbyPlayers(9));
        assertEquals(2, api.getPlayerLobbyId(member));
        assertEquals(-1, api.getPlayerLobbyId(lobbyLess));
        assertEquals(-1, api.getPlayerLobbyId(null));
    }
}
