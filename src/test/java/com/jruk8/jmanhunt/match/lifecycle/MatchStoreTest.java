package com.jruk8.jmanhunt.match.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MatchStoreTest {

    @Test
    void activeMembersShowWhateverTheirRole() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);
        UUID player = UUID.randomUUID();
        instance.activate(player);

        assertTrue(MatchStore.showsInMatchStatus(instance, player, Role.HUNTER));
        assertTrue(MatchStore.showsInMatchStatus(instance, player, Role.NONE));
        assertTrue(MatchStore.showsInMatchStatus(instance, player, Role.AFK));
    }

    @Test
    void deactivatedParticipantsAndSpectatorsShow() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);
        UUID player = UUID.randomUUID();
        instance.activate(player);
        instance.deactivate(player);

        assertTrue(MatchStore.showsInMatchStatus(instance, player, Role.SPEEDRUNNER));
        assertTrue(MatchStore.showsInMatchStatus(instance, player, Role.SPECTATOR));
    }

    @Test
    void lobbyReturnedLeaversHide() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);
        UUID player = UUID.randomUUID();
        instance.activate(player);
        instance.deactivate(player);

        assertFalse(MatchStore.showsInMatchStatus(instance, player, Role.NONE));
        assertFalse(MatchStore.showsInMatchStatus(instance, player, Role.AFK));
    }

    @Test
    void audienceKeepsWatchersButDropsLobbyLeavers() {
        PlayerStateStore players = new PlayerStateStore();
        MatchStore store = new MatchStore(players);
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);
        Player active = named(UUID.randomUUID());
        Player eliminated = named(UUID.randomUUID());
        Player leaver = named(UUID.randomUUID());
        Player stranger = named(UUID.randomUUID());
        instance.assignAll(List.of(active.getUniqueId(), eliminated.getUniqueId(),
                leaver.getUniqueId()));
        instance.activate(active.getUniqueId());
        instance.activate(eliminated.getUniqueId());
        instance.deactivate(eliminated.getUniqueId());
        instance.activate(leaver.getUniqueId());
        instance.deactivate(leaver.getUniqueId());
        players.setRole(active.getUniqueId(), Role.HUNTER);
        players.setRole(eliminated.getUniqueId(), Role.SPECTATOR);
        players.setRole(leaver.getUniqueId(), Role.NONE);
        players.setRole(stranger.getUniqueId(), Role.HUNTER);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers)
                    .thenReturn(List.of(active, eliminated, leaver, stranger));

            assertEquals(List.of(active, eliminated), store.onlineMatchAudience(instance));
        }
    }

    private static Player named(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }
}
