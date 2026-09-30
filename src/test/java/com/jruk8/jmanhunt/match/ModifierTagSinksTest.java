package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Role sink filtering: ALL covers both teams, one side otherwise. */
class ModifierTagSinksTest {

    @Test
    void roleMatchesCoversBothTeamsForAll() {
        assertTrue(ModifierTagSinks.roleMatches("ALL", Role.HUNTER));
        assertTrue(ModifierTagSinks.roleMatches("ALL", Role.SPEEDRUNNER));
        assertFalse(ModifierTagSinks.roleMatches("ALL", Role.SPECTATOR));
        assertFalse(ModifierTagSinks.roleMatches("ALL", Role.NONE));
    }

    @Test
    void roleMatchesSingleSideOtherwise() {
        assertTrue(ModifierTagSinks.roleMatches("HUNTER", Role.HUNTER));
        assertFalse(ModifierTagSinks.roleMatches("HUNTER", Role.SPEEDRUNNER));
        assertTrue(ModifierTagSinks.roleMatches("SPEEDRUNNER", Role.SPEEDRUNNER));
        assertFalse(ModifierTagSinks.roleMatches("SPEEDRUNNER", Role.HUNTER));
    }
}
