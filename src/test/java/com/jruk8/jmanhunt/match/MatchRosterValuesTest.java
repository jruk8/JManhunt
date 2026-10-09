package com.jruk8.jmanhunt.match;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Eliminated reads over assigned ids: only active names read false. */
class MatchRosterValuesTest {

    @Test
    void eliminatedReadsActiveFalseAndRestTrue() {
        GameManager game = mock(GameManager.class);
        GameInstance instance = mock(GameInstance.class);
        UUID activeId = UUID.randomUUID();
        UUID deadId = UUID.randomUUID();
        when(game.instance(7L)).thenReturn(Optional.of(instance));
        when(instance.assignedPlayerIds()).thenReturn(Set.of(activeId, deadId));
        when(instance.isActive(activeId)).thenReturn(true);
        when(instance.isActive(deadId)).thenReturn(false);
        Player active = mock(Player.class);
        when(active.getName()).thenReturn("Amy");
        Player dead = mock(Player.class);
        when(dead.getName()).thenReturn("Zed");
        MatchRosterValues roster =
                new MatchRosterValues(game, null, null, 7L);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(activeId)).thenReturn(active);
            bukkit.when(() -> Bukkit.getPlayer(deadId)).thenReturn(dead);

            assertFalse(roster.eliminated("Amy"));
            assertFalse(roster.eliminated("amy"));
            assertTrue(roster.eliminated("Zed"));
            assertTrue(roster.eliminated("Ghost"));
        }
    }

    @Test
    void eliminatedResolvesOfflineAssignees() {
        GameManager game = mock(GameManager.class);
        GameInstance instance = mock(GameInstance.class);
        UUID quitterId = UUID.randomUUID();
        when(game.instance(7L)).thenReturn(Optional.of(instance));
        when(instance.assignedPlayerIds()).thenReturn(Set.of(quitterId));
        when(instance.isActive(quitterId)).thenReturn(true);
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.getName()).thenReturn("Alex");
        MatchRosterValues roster =
                new MatchRosterValues(game, null, null, 7L);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(quitterId)).thenReturn(null);
            bukkit.when(() -> Bukkit.getOfflinePlayer(quitterId)).thenReturn(offline);

            assertFalse(roster.eliminated("Alex"));
        }
    }

    @Test
    void eliminatedReadsTrueWithoutMatch() {
        GameManager game = mock(GameManager.class);
        when(game.instance(7L)).thenReturn(Optional.empty());
        MatchRosterValues roster =
                new MatchRosterValues(game, null, null, 7L);

        assertTrue(roster.eliminated("Amy"));
    }
}
