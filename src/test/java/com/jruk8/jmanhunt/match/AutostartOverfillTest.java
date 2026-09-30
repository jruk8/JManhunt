package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.match.autostart.AutostartService;
import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutostartOverfillTest {
    @Test
    void negativeMaximumDisablesRoleIndependently() {
        assertTrue(AutostartService.autostartOverfill(9, 9, -1, -1).isEmpty());
        assertEquals(Map.of(Role.SPEEDRUNNER, 1),
                AutostartService.autostartOverfill(9, 3, -1, 2));
        assertEquals(Map.of(Role.HUNTER, 2),
                AutostartService.autostartOverfill(4, 9, 2, -1));
    }

    @Test
    void exactMaximumIsEligible() {
        assertTrue(AutostartService.autostartOverfill(2, 1, 2, 1).isEmpty());
        assertTrue(AutostartService.autostartOverfill(0, 0, 0, 0).isEmpty());
    }

    @Test
    void excessCountedPerRole() {
        assertEquals(Map.of(Role.HUNTER, 1, Role.SPEEDRUNNER, 2),
                AutostartService.autostartOverfill(3, 4, 2, 2));
    }

    @Test
    void minimumAboveMaximumIsNeverEligible() {
        Map<Role, Integer> belowMax = AutostartService.autostartShortfall(2, 1, 3, 1);
        Map<Role, Integer> belowMaxExcess = AutostartService.autostartOverfill(2, 1, 2, -1);
        assertFalse(belowMax.isEmpty());
        assertTrue(belowMaxExcess.isEmpty());
        assertFalse(AutostartService.autostartEligible(belowMax, belowMaxExcess));

        Map<Role, Integer> aboveMax = AutostartService.autostartShortfall(3, 1, 3, 1);
        Map<Role, Integer> aboveMaxExcess = AutostartService.autostartOverfill(3, 1, 2, -1);
        assertTrue(aboveMax.isEmpty());
        assertFalse(aboveMaxExcess.isEmpty());
        assertFalse(AutostartService.autostartEligible(aboveMax, aboveMaxExcess));
    }

    @Test
    void overfillPartUsesExtraWording() {
        assertEquals("<white>two</white> extra <#de666e>Hunters<yellow>",
                AutostartService.overfillPart("two", "<#de666e>Hunter", 2));
        assertEquals("<white>one</white> extra <#de666e>Hunter<yellow>",
                AutostartService.overfillPart("one", "<#de666e>Hunter", 1));
    }
}
