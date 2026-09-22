package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreStartHitTest {
    @Test
    void hunterHitsOnSpeedrunnersNeverLand() {
        assertTrue(PlayerCombatListener.blockPreStartHit(true, Role.HUNTER, Role.SPEEDRUNNER));
    }

    @Test
    void otherPreStartHitsStillLand() {
        assertFalse(PlayerCombatListener.blockPreStartHit(true, Role.SPEEDRUNNER, Role.HUNTER));
        assertFalse(PlayerCombatListener.blockPreStartHit(true, Role.HUNTER, Role.HUNTER));
        assertFalse(PlayerCombatListener.blockPreStartHit(true, Role.SPEEDRUNNER, Role.SPEEDRUNNER));
        assertFalse(PlayerCombatListener.blockPreStartHit(false, Role.HUNTER, Role.SPEEDRUNNER));
    }
}
