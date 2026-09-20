package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidMatchPolicyTest {

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(MidMatchPolicy.HOLD, MidMatchPolicy.parse("hold"));
        assertEquals(MidMatchPolicy.JOIN_ANY, MidMatchPolicy.parse("Join_Any"));
        assertEquals(MidMatchPolicy.JOIN_SPECTATORS, MidMatchPolicy.parse("JOIN_SPECTATORS"));
        assertEquals(MidMatchPolicy.SUBLOBBY, MidMatchPolicy.parse("sublobby"));
    }

    @Test
    void parseFallsBackToSublobby() {
        assertEquals(MidMatchPolicy.SUBLOBBY, MidMatchPolicy.parse("bogus"));
        assertEquals(MidMatchPolicy.SUBLOBBY, MidMatchPolicy.parse(null));
        assertEquals(MidMatchPolicy.SUBLOBBY, MidMatchPolicy.parse(""));
    }

    @Test
    void holdNeverJoins() {
        for (Role role : Role.values()) {
            assertFalse(MidMatchPolicy.HOLD.joinsMidMatch(role), role.name());
        }
    }

    @Test
    void joinAnyAlwaysJoins() {
        for (Role role : Role.values()) {
            assertTrue(MidMatchPolicy.JOIN_ANY.joinsMidMatch(role), role.name());
        }
    }

    @Test
    void joinSpectatorsOnlyJoinsSpectators() {
        assertTrue(MidMatchPolicy.JOIN_SPECTATORS.joinsMidMatch(Role.SPECTATOR));
        assertFalse(MidMatchPolicy.JOIN_SPECTATORS.joinsMidMatch(Role.HUNTER));
        assertFalse(MidMatchPolicy.JOIN_SPECTATORS.joinsMidMatch(Role.SPEEDRUNNER));
        assertFalse(MidMatchPolicy.JOIN_SPECTATORS.joinsMidMatch(Role.AFK));
        assertFalse(MidMatchPolicy.JOIN_SPECTATORS.joinsMidMatch(Role.NONE));
    }

    @Test
    void sublobbyHoldsForNextSublobby() {
        for (Role role : Role.values()) {
            assertFalse(MidMatchPolicy.SUBLOBBY.joinsMidMatch(role), role.name());
        }
    }
}
