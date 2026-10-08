package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.player.Role;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure join-timing rules: wait math, source gate, and lobby scoping. */
class JoinWaitTest {

    @Test
    void waitTakesTheLargerDuration() {
        assertEquals(30, MatchStartService.joinWaitSeconds(Role.HUNTER, true, 30, true, 15));
        assertEquals(30, MatchStartService.joinWaitSeconds(Role.HUNTER, true, 15, true, 30));
        assertEquals(20, MatchStartService.joinWaitSeconds(Role.SPEEDRUNNER, true, 20, true, 20));
    }

    @Test
    void disabledOrNegativeDurationsCountAsZero() {
        assertEquals(15, MatchStartService.joinWaitSeconds(Role.HUNTER, false, 30, true, 15));
        assertEquals(30, MatchStartService.joinWaitSeconds(Role.HUNTER, true, 30, false, 15));
        assertEquals(0, MatchStartService.joinWaitSeconds(Role.HUNTER, false, 30, false, 15));
        assertEquals(0, MatchStartService.joinWaitSeconds(Role.HUNTER, true, -1, true, -1));
        assertEquals(15, MatchStartService.joinWaitSeconds(Role.HUNTER, true, -1, true, 15));
    }

    @Test
    void nonParticipantTargetsNeverWait() {
        assertEquals(0, MatchStartService.joinWaitSeconds(Role.SPECTATOR, true, 30, true, 15));
        assertEquals(0, MatchStartService.joinWaitSeconds(Role.NONE, true, 30, true, 15));
        assertEquals(0, MatchStartService.joinWaitSeconds(Role.AFK, true, 30, true, 15));
    }

    @Test
    void onlyOutsidersNeedTiming() {
        assertTrue(MatchStartService.needsJoinTiming(Role.NONE));
        assertTrue(MatchStartService.needsJoinTiming(Role.SPECTATOR));
        assertTrue(MatchStartService.needsJoinTiming(Role.AFK));
        assertFalse(MatchStartService.needsJoinTiming(Role.HUNTER));
        assertFalse(MatchStartService.needsJoinTiming(Role.SPEEDRUNNER));
    }

    @Test
    void sourceRoleIsLobbySpecific() {
        assertEquals(Role.SPECTATOR,
                MatchStartService.sourceRoleIn(0, Optional.of(0), Role.SPECTATOR));
        assertEquals(Role.NONE,
                MatchStartService.sourceRoleIn(0, Optional.of(1), Role.SPEEDRUNNER));
        assertEquals(Role.NONE, MatchStartService.sourceRoleIn(0, Optional.empty(), Role.HUNTER));
    }
}
