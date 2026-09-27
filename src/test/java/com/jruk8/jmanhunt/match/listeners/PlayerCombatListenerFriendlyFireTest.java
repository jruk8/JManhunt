package com.jruk8.jmanhunt.match.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;

class PlayerCombatListenerFriendlyFireTest {

    @Test
    void sameParticipantRoleCountsAsFriendlyFire() {
        assertTrue(PlayerCombatListener.isFriendlyFireKill(Role.HUNTER, Role.HUNTER, false));
        assertTrue(PlayerCombatListener.isFriendlyFireKill(
                Role.SPEEDRUNNER, Role.SPEEDRUNNER, false));
    }

    @Test
    void crossTeamSuicideAndNonParticipantKillsAreExcluded() {
        assertFalse(PlayerCombatListener.isFriendlyFireKill(Role.HUNTER, Role.SPEEDRUNNER, false));
        assertFalse(PlayerCombatListener.isFriendlyFireKill(Role.SPEEDRUNNER, Role.HUNTER, false));
        assertFalse(PlayerCombatListener.isFriendlyFireKill(Role.HUNTER, Role.HUNTER, true));
        assertFalse(PlayerCombatListener.isFriendlyFireKill(Role.SPECTATOR, Role.SPECTATOR, false));
        assertFalse(PlayerCombatListener.isFriendlyFireKill(Role.NONE, Role.NONE, false));
        assertFalse(PlayerCombatListener.isFriendlyFireKill(Role.AFK, Role.AFK, false));
    }

    @Test
    void friendlyFireKeysCycleThreeLines() {
        assertEquals("game.friendly-fire-1", PlayerCombatListener.friendlyFireKey(0));
        assertEquals("game.friendly-fire-2", PlayerCombatListener.friendlyFireKey(1));
        assertEquals("game.friendly-fire-3", PlayerCombatListener.friendlyFireKey(2));
        assertEquals("game.friendly-fire-1", PlayerCombatListener.friendlyFireKey(3));
    }
}
