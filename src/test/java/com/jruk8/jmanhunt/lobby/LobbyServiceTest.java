package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.config.LobbiesConfig;
import com.jruk8.jmanhunt.config.WorldEngineConfig;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.player.RoleTeamService;
import java.util.List;
import java.util.Optional;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void midMatchPolicyReadsLobbySettings() {
        LobbiesConfig lobbySettings = new LobbiesConfig();
        lobbySettings.setMidMatchSetplayer(MidMatchPolicy.HOLD);
        LobbyService lobbies =
                new LobbyService(null, null, lobbySettings, new WorldEngineConfig(), null);

        assertEquals(MidMatchPolicy.HOLD, lobbies.midMatchPolicy());
    }

    private static LobbyService service() {
        return new LobbyService(null, null, null, null, null);
    }

    @Test
    void lobbyJoinMovesMembersToLobbyTeams() {
        Fixture fixture = fixture(true);
        fixture.lobbies.setLobby(fixture.id, 0);

        fixture.lobbies.applyLobbyCollisions(fixture.player);

        verify(fixture.roleTeams).moveToLobby(fixture.player);
        verify(fixture.player, never()).setCollidable(anyBoolean());
    }

    @Test
    void lobbyJoinLeavesMatchMembersOutsidersAndToggledOffUntouched() {
        Fixture on = fixture(true);
        on.lobbies.setLobby(on.id, 0);
        when(on.game.instanceOf(on.id)).thenReturn(Optional.of(mock(GameInstance.class)));

        on.lobbies.applyLobbyCollisions(on.player);

        verify(on.roleTeams, never()).moveToLobby(on.player);

        Fixture off = fixture(false);
        off.lobbies.setLobby(off.id, 0);

        off.lobbies.applyLobbyCollisions(off.player);

        verify(off.roleTeams, never()).moveToLobby(off.player);

        Fixture outsider = fixture(true);

        outsider.lobbies.applyLobbyCollisions(outsider.player);

        verify(outsider.roleTeams, never()).moveToLobby(outsider.player);
    }

    @Test
    void restoreCollisionsReturnsPlayerToRoleTeams() {
        Fixture fixture = fixture(true);

        fixture.lobbies.restoreCollisions(fixture.player);

        verify(fixture.roleTeams).removeLobbyEntry("Alex");
        verify(fixture.roleTeams).sync(fixture.player);
        verify(fixture.player, never()).setCollidable(anyBoolean());
    }

    @Test
    void reapplyCollisionsSortsEveryoneByState() {
        Fixture fixture = fixture(false);
        when(fixture.game.instanceOf(fixture.id))
                .thenReturn(Optional.of(mock(GameInstance.class)));
        Player member = mock(Player.class);
        UUID memberId = UUID.randomUUID();
        when(member.getUniqueId()).thenReturn(memberId);
        when(member.getName()).thenReturn("Blair");
        fixture.lobbies.setLobby(memberId, 0);
        Player outsider = mock(Player.class);
        when(outsider.getUniqueId()).thenReturn(UUID.randomUUID());

        fixture.lobbies.reapplyCollisions(List.of(fixture.player, member, outsider));

        verify(fixture.roleTeams).removeLobbyEntry("Alex");
        verify(fixture.roleTeams).removeLobbyEntry("Blair");
        verify(fixture.roleTeams, times(2)).sync(any(Player.class));
        verify(fixture.roleTeams, never()).moveToLobby(any(Player.class));
    }

    @Test
    void reapplyCollisionsAppliesForLobbyOccupantsWhenEnabled() {
        Fixture fixture = fixture(true);
        fixture.lobbies.setLobby(fixture.id, 0);

        fixture.lobbies.reapplyCollisions(List.of(fixture.player));

        verify(fixture.roleTeams).moveToLobby(fixture.player);
    }

    @Test
    void lobbyWorldOccupantWithoutMembershipLosesCollisions() {
        Fixture fixture = fixture(true);
        mockWorld(fixture.player, "jmh_lobby");

        fixture.lobbies.applyLobbyCollisions(fixture.player);

        verify(fixture.roleTeams).moveToLobby(fixture.player);
    }

    @Test
    void matchMemberInLobbyWorldLosesCollisions() {
        Fixture fixture = fixture(true);
        mockWorld(fixture.player, "jmh_lobby");
        when(fixture.game.instanceOf(fixture.id)).thenReturn(Optional.of(mock(GameInstance.class)));

        fixture.lobbies.applyLobbyCollisions(fixture.player);

        verify(fixture.roleTeams).moveToLobby(fixture.player);
    }

    @Test
    void gameWorldOccupantWithoutMembershipKeepsCollisions() {
        Fixture fixture = fixture(true);
        mockWorld(fixture.player, "world");

        fixture.lobbies.applyLobbyCollisions(fixture.player);

        verify(fixture.roleTeams, never()).moveToLobby(fixture.player);
        verify(fixture.player, never()).setCollidable(anyBoolean());
    }

    private static void mockWorld(Player player, String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(player.getWorld()).thenReturn(world);
    }

    /** Wired service with one lobby-less player outside any match. */
    private static Fixture fixture(boolean collisionsDisabled) {
        LobbiesConfig lobbySettings = new LobbiesConfig();
        lobbySettings.setDisablePlayerCollisions(collisionsDisabled);
        GameManager game = mock(GameManager.class);
        RoleTeamService roleTeams = mock(RoleTeamService.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Alex");
        when(game.instanceOf(id)).thenReturn(Optional.empty());
        return new Fixture(
                new LobbyService(new LobbyService.LobbyPlayers(() -> game), null,
                        lobbySettings, new WorldEngineConfig(), roleTeams),
                player, id, game, roleTeams);
    }

    private record Fixture(LobbyService lobbies, Player player, UUID id,
            GameManager game, RoleTeamService roleTeams) {
    }
}
