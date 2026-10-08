package com.jruk8.jmanhunt.core;

import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import net.luckperms.api.context.ContextConsumer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoleContextsTest {

    @Test
    void valueForMapsRoles() {
        assertEquals("hunter", RoleContexts.valueFor(Role.HUNTER));
        assertEquals("speedrunner", RoleContexts.valueFor(Role.SPEEDRUNNER));
        assertEquals("spectator", RoleContexts.valueFor(Role.SPECTATOR));
        assertEquals("afk", RoleContexts.valueFor(Role.AFK));
        assertEquals("none", RoleContexts.valueFor(Role.NONE));
        assertEquals("none", RoleContexts.valueFor(null));
    }

    @Test
    void calculateExposesLiveRole() {
        PlayerStateStore states = new PlayerStateStore();
        UUID playerId = UUID.randomUUID();
        states.setRole(playerId, Role.SPEEDRUNNER);

        Map<String, String> contexts = calculate(states, playerId);

        assertEquals(Map.of("jmh-role", "speedrunner"), contexts);
    }

    @Test
    void unassignedPlayersReadNone() {
        Map<String, String> contexts = calculate(new PlayerStateStore(), UUID.randomUUID());

        assertEquals(Map.of("jmh-role", "none"), contexts);
    }

    @Test
    void stopBeforeStartIsSafe() {
        new RoleContexts(new PlayerStateStore()).stop();
    }

    private Map<String, String> calculate(PlayerStateStore states, UUID playerId) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        Map<String, String> contexts = new HashMap<>();
        ContextConsumer consumer = (key, value) -> contexts.put(key, value);
        new RoleContexts(states).calculate(player, consumer);
        return contexts;
    }
}
