package com.jruk8.jmanhunt.match.listeners;

import com.jruk8.jmanhunt.match.GameInstance;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Join-hold scheduling through a lazily resolved respawn listener. */
class ScheduleHoldTest {

    @Test
    void liveListenerReceivesHoldAndLogsNothing() {
        PlayerRespawnListener listener = mock(PlayerRespawnListener.class);
        Player player = mock(Player.class);
        GameInstance instance = mock(GameInstance.class);
        List<String> logs = new ArrayList<>();

        PlayerRespawnListener.scheduleHold(() -> listener, player, instance, 30, logs::add);

        verify(listener).scheduleJoinHold(player, instance, 30);
        assertTrue(logs.isEmpty());
    }

    @Test
    void nullListenerSkipsWithOneLogLine() {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("DaVeltto");
        GameInstance instance = mock(GameInstance.class);
        List<String> logs = new ArrayList<>();

        assertDoesNotThrow(() ->
                PlayerRespawnListener.scheduleHold(() -> null, player, instance, 30, logs::add));

        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("DaVeltto"));
    }
}
