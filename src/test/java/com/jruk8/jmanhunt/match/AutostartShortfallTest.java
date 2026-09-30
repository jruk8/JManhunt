package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.match.autostart.AutostartService;
import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutostartShortfallTest {
    @Test
    void emptyShortfallMeansEligible() {
        assertTrue(AutostartService.autostartShortfall(1, 1, 1, 1).isEmpty());
        assertTrue(AutostartService.autostartShortfall(3, 2, 1, 1).isEmpty());
    }

    @Test
    void shortfallCountsMissingPlayersPerRole() {
        assertEquals(Map.of(Role.HUNTER, 1, Role.SPEEDRUNNER, 1),
                AutostartService.autostartShortfall(0, 0, 1, 1));
        assertEquals(Map.of(Role.SPEEDRUNNER, 2),
                AutostartService.autostartShortfall(2, 0, 1, 2));
        assertEquals(Map.of(Role.HUNTER, 3),
                AutostartService.autostartShortfall(0, 4, 3, 1));
    }

    @Test
    void shortfallPartPluralizesAndClosesRoleColor() {
        assertEquals("<white>one</white> more <#de666e>Hunter<yellow>",
                AutostartService.shortfallPart("one", "<#de666e>Hunter", 1));
        assertEquals("<white>two</white> more <#de666e>Hunters<yellow>",
                AutostartService.shortfallPart("two", "<#de666e>Hunter", 2));
    }

    @Test
    void shortfallNagReachesOnlyAssignedTeams() {
        assertTrue(AutostartService.receivesShortfall(Role.HUNTER));
        assertTrue(AutostartService.receivesShortfall(Role.SPEEDRUNNER));
        assertFalse(AutostartService.receivesShortfall(Role.NONE));
        assertFalse(AutostartService.receivesShortfall(Role.AFK));
        assertFalse(AutostartService.receivesShortfall(Role.SPECTATOR));
    }

    @Test
    void teamGrewDetectsNewAssigneesOnly() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        assertFalse(AutostartService.teamGrew(null, Set.of(alice)));
        assertTrue(AutostartService.teamGrew(Set.of(), Set.of(alice)));
        assertTrue(AutostartService.teamGrew(Set.of(alice), Set.of(alice, bob)));
        assertFalse(AutostartService.teamGrew(Set.of(alice), Set.of(alice)));
        assertFalse(AutostartService.teamGrew(Set.of(alice, bob), Set.of(alice)));
        assertFalse(AutostartService.teamGrew(Set.of(), Set.of()));
    }

    @Test
    void minimumsClampToHardMinimumOne() {
        assertEquals(Map.of(Role.HUNTER, 1, Role.SPEEDRUNNER, 1),
                AutostartService.autostartShortfall(0, 0, 0, 0));
        assertEquals(Map.of(Role.HUNTER, 1, Role.SPEEDRUNNER, 1),
                AutostartService.autostartShortfall(0, 0, -5, -5));
        assertEquals(Map.of(Role.SPEEDRUNNER, 1),
                AutostartService.autostartShortfall(3, 0, 0, 1));
    }

    @Test
    void nagDueFiresOncePerInterval() {
        assertTrue(AutostartService.nagDue(100_000L, null, 60));
        assertTrue(AutostartService.nagDue(160_000L, 100_000L, 60));
        assertFalse(AutostartService.nagDue(159_999L, 100_000L, 60));
        assertFalse(AutostartService.nagDue(100_000L, 100_000L, 60));
    }
}
