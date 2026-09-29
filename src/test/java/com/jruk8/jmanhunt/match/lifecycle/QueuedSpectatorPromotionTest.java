package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueuedSpectatorPromotionTest {

    @Test
    void lobbySpectatorsOutsideMatchesPromote() {
        assertTrue(MatchStartService.isQueuedSpectator(Role.SPECTATOR, false));
    }

    @Test
    void inMatchSpectatorsStayPut() {
        assertFalse(MatchStartService.isQueuedSpectator(Role.SPECTATOR, true));
    }

    @Test
    void otherRolesNeverPromote() {
        for (Role role : Role.values()) {
            if (role == Role.SPECTATOR) {
                continue;
            }
            assertFalse(MatchStartService.isQueuedSpectator(role, false), role.name());
            assertFalse(MatchStartService.isQueuedSpectator(role, true), role.name());
        }
    }
}
