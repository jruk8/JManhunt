package com.jruk8.jmanhunt.match.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.player.Role;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
}
