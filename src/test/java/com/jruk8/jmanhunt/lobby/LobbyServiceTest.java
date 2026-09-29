package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void midMatchPolicyParsesConfigWithSublobbyDefault() {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        ConfigService config = mock(ConfigService.class);
        when(plugin.configService()).thenReturn(config);
        when(config.getString("advanced.lobbies.mid-match-setplayer", "SUBLOBBY_WITH_SPECTATORS"))
                .thenReturn("hold");
        LobbyService lobbies = new LobbyService(plugin);

        assertEquals(MidMatchPolicy.HOLD, lobbies.midMatchPolicy());
    }

    private static LobbyService service() {
        return new LobbyService(null);
    }

    @Test
    void lobbyJoinDisablesCollisionsForMembers() {
        Fixture fixture = fixture(true);
        fixture.lobbies.setLobby(fixture.id, 0);

        fixture.lobbies.applyLobbyCollisions(fixture.player);

        verify(fixture.player).setCollidable(false);
    }

    @Test
    void lobbyJoinLeavesMatchMembersOutsidersAndToggledOffUntouched() {
        Fixture on = fixture(true);
        on.lobbies.setLobby(on.id, 0);
        when(on.game.instanceOf(on.id)).thenReturn(Optional.of(mock(GameInstance.class)));

        on.lobbies.applyLobbyCollisions(on.player);

        verify(on.player, never()).setCollidable(anyBoolean());

        Fixture off = fixture(false);
        off.lobbies.setLobby(off.id, 0);

        off.lobbies.applyLobbyCollisions(off.player);

        verify(off.player, never()).setCollidable(anyBoolean());

        Fixture outsider = fixture(true);

        outsider.lobbies.applyLobbyCollisions(outsider.player);

        verify(outsider.player, never()).setCollidable(anyBoolean());
    }

    @Test
    void restoreCollisionsSkipsFakeSpectators() {
        Fixture fixture = fixture(true);

        fixture.lobbies.restoreCollisions(fixture.player);

        verify(fixture.player).setCollidable(true);

        when(fixture.fakes.isFakeSpectator(fixture.player)).thenReturn(true);

        fixture.lobbies.restoreCollisions(fixture.player);

        verify(fixture.player, never()).setCollidable(false);
    }

    @Test
    void reapplyCollisionsSortsEveryoneByState() {
        Fixture fixture = fixture(false);
        when(fixture.game.instanceOf(fixture.id))
                .thenReturn(Optional.of(mock(GameInstance.class)));
        Player fake = mock(Player.class);
        UUID fakeId = UUID.randomUUID();
        when(fake.getUniqueId()).thenReturn(fakeId);
        when(fixture.fakes.isFakeSpectator(fake)).thenReturn(true);
        Player member = mock(Player.class);
        UUID memberId = UUID.randomUUID();
        when(member.getUniqueId()).thenReturn(memberId);
        fixture.lobbies.setLobby(memberId, 0);
        Player outsider = mock(Player.class);
        when(outsider.getUniqueId()).thenReturn(UUID.randomUUID());

        fixture.lobbies.reapplyCollisions(List.of(fake, fixture.player, member, outsider));

        verify(fake).setCollidable(false);
        verify(fixture.player).setCollidable(true);
        verify(member).setCollidable(true);
        verify(outsider, never()).setCollidable(anyBoolean());
    }

    /** Wired service with one lobby-less non-fake player outside any match. */
    private static Fixture fixture(boolean collisionsDisabled) {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        ConfigService config = mock(ConfigService.class);
        GameManager game = mock(GameManager.class);
        FakeSpectatorService fakes = mock(FakeSpectatorService.class);
        when(plugin.configService()).thenReturn(config);
        when(plugin.game()).thenReturn(game);
        when(plugin.fakeSpectators()).thenReturn(fakes);
        when(config.getBoolean(LobbyService.COLLISIONS_PATH, true))
                .thenReturn(collisionsDisabled);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(game.instanceOf(id)).thenReturn(Optional.empty());
        when(fakes.isFakeSpectator(player)).thenReturn(false);
        return new Fixture(new LobbyService(plugin), player, id, game, fakes);
    }

    private record Fixture(LobbyService lobbies, Player player, UUID id,
            GameManager game, FakeSpectatorService fakes) {
    }
}
