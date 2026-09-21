package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutostartShortfallTest {
    @Test
    void emptyShortfallMeansEligible() {
        assertTrue(GameManager.autostartShortfall(1, 1, 1, 1).isEmpty());
        assertTrue(GameManager.autostartShortfall(3, 2, 1, 1).isEmpty());
    }

    @Test
    void shortfallCountsMissingPlayersPerRole() {
        assertEquals(Map.of(Role.HUNTER, 1, Role.SPEEDRUNNER, 1),
                GameManager.autostartShortfall(0, 0, 1, 1));
        assertEquals(Map.of(Role.SPEEDRUNNER, 2),
                GameManager.autostartShortfall(2, 0, 1, 2));
        assertEquals(Map.of(Role.HUNTER, 3),
                GameManager.autostartShortfall(0, 4, 3, 1));
    }

    @Test
    void shortfallPartPluralizesAndClosesRoleColor() {
        assertEquals("<white>one</white> more <#de666e>Hunter<yellow>",
                GameManager.shortfallPart("one", "<#de666e>Hunter", 1));
        assertEquals("<white>two</white> more <#de666e>Hunters<yellow>",
                GameManager.shortfallPart("two", "<#de666e>Hunter", 2));
    }

    @Test
    void shortfallNagReachesOnlyAssignedTeams() {
        assertTrue(GameManager.receivesShortfall(Role.HUNTER));
        assertTrue(GameManager.receivesShortfall(Role.SPEEDRUNNER));
        assertFalse(GameManager.receivesShortfall(Role.NONE));
        assertFalse(GameManager.receivesShortfall(Role.AFK));
        assertFalse(GameManager.receivesShortfall(Role.SPECTATOR));
    }

    @Test
    void minimumsClampToHardMinimumOne() {
        assertEquals(Map.of(Role.HUNTER, 1, Role.SPEEDRUNNER, 1),
                GameManager.autostartShortfall(0, 0, 0, 0));
        assertEquals(Map.of(Role.HUNTER, 1, Role.SPEEDRUNNER, 1),
                GameManager.autostartShortfall(0, 0, -5, -5));
        assertEquals(Map.of(Role.SPEEDRUNNER, 1),
                GameManager.autostartShortfall(3, 0, 0, 1));
    }
}
