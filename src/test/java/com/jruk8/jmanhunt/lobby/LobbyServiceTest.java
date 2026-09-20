package com.jruk8.jmanhunt.lobby;

import org.junit.jupiter.api.Test;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyServiceTest {

    @Test
    void validIdsSpanZeroToIntMax() {
        assertTrue(LobbyService.isValidId(0));
        assertTrue(LobbyService.isValidId(7));
        assertTrue(LobbyService.isValidId(Integer.MAX_VALUE));
        assertFalse(LobbyService.isValidId(-1));
        assertFalse(LobbyService.isValidId((long) Integer.MAX_VALUE + 1));
    }

    @Test
    void parseAcceptsOnlyInRangeIntegers() {
        assertEquals(OptionalInt.of(3), LobbyService.parseId("3"));
        assertEquals(OptionalInt.of(0), LobbyService.parseId(" 0 "));
        assertEquals(OptionalInt.empty(), LobbyService.parseId("-1"));
        assertEquals(OptionalInt.empty(), LobbyService.parseId("2147483648"));
        assertEquals(OptionalInt.empty(), LobbyService.parseId("abc"));
        assertEquals(OptionalInt.empty(), LobbyService.parseId(""));
        assertEquals(OptionalInt.empty(), LobbyService.parseId(null));
    }

    @Test
    void joinCreatesMissingLobby() {
        LobbyService lobbies = service();

        Lobby lobby = lobbies.setLobby(UUID.randomUUID(), 4);

        assertEquals(4, lobby.id());
        assertTrue(lobbies.get(4).isPresent());
    }

    @Test
    void moveSwitchesMembership() {
        LobbyService lobbies = service();
        UUID player = UUID.randomUUID();
        lobbies.setLobby(player, 0);

        lobbies.setLobby(player, 2);

        assertEquals(2, lobbies.lobbyOf(player).orElseThrow().id());
        assertTrue(lobbies.get(0).isEmpty());
        assertTrue(lobbies.get(2).isPresent());
    }

    @Test
    void emptyLobbyIsDeletedOnLeave() {
        LobbyService lobbies = service();
        UUID player = UUID.randomUUID();
        lobbies.setLobby(player, 1);

        lobbies.remove(player);

        assertTrue(lobbies.get(1).isEmpty());
        assertTrue(lobbies.lobbyOf(player).isEmpty());
    }

    @Test
    void sharedLobbySurvivesOneLeave() {
        LobbyService lobbies = service();
        UUID leaving = UUID.randomUUID();
        UUID staying = UUID.randomUUID();
        lobbies.setLobby(leaving, 1);
        lobbies.setLobby(staying, 1);

        lobbies.remove(leaving);

        assertTrue(lobbies.get(1).isPresent());
        assertEquals(1, lobbies.lobbyOf(staying).orElseThrow().id());
    }

    @Test
    void lobbyIdsListsLiveLobbies() {
        LobbyService lobbies = service();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        lobbies.setLobby(first, 3);
        lobbies.setLobby(second, 7);
        lobbies.remove(first);

        assertEquals(Set.of(7), lobbies.lobbyIds());
        assertTrue(lobbies.get(9).isEmpty());
    }

    @Test
    void negativeDefaultLeavesPlayerLobbyLess() {
        LobbyService lobbies = service();
        UUID player = UUID.randomUUID();
        lobbies.setLobby(player, 0);

        lobbies.assignDefault(player, -1);

        assertTrue(lobbies.lobbyOf(player).isEmpty());
    }

    @Test
    void defaultAssignsConfiguredLobby() {
        LobbyService lobbies = service();
        UUID player = UUID.randomUUID();

        lobbies.assignDefault(player, 6);

        assertEquals(6, lobbies.lobbyOf(player).orElseThrow().id());
    }

    @Test
    void subIdsStartAtZeroAndNeverRepeat() {
        LobbyService lobbies = service();

        assertEquals(0, lobbies.nextSubId(2));
        assertEquals(1, lobbies.nextSubId(2));
        assertEquals(0, lobbies.nextSubId(3));
        assertEquals(2, lobbies.nextSubId(2));
    }

    private static LobbyService service() {
        return new LobbyService(null);
    }
}
