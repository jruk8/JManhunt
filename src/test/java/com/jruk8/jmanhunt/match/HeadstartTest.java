package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.config.MatchSettings;
import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jruk8.jmanhunt.match.prestart.Headstart;

class HeadstartTest {

    @Test
    void defaultsAreRunnerTwentySeconds() {
        MatchSettings.Headstarts headstarts = new MatchSettings.Headstarts();

        Headstart hunter = Headstart.parse(headstarts, "hunter");
        Headstart runner = Headstart.parse(headstarts, "speedrunner");

        assertFalse(hunter.enabled());
        assertEquals(20, hunter.delaySeconds());
        assertTrue(runner.enabled());
        assertEquals(20, runner.delaySeconds());
    }

    @Test
    void sidesParseIndependently() {
        MatchSettings.Headstarts headstarts = new MatchSettings.Headstarts();
        headstarts.getHunter().setEnabled(true);
        headstarts.getHunter().setDelaySeconds(45);
        headstarts.getSpeedrunner().setEnabled(true);
        headstarts.getSpeedrunner().setDelaySeconds(10);

        assertEquals(new Headstart(true, 45),
                Headstart.parse(headstarts, "hunter"));
        assertEquals(new Headstart(true, 10),
                Headstart.parse(headstarts, "speedrunner"));
    }

    @Test
    void oppositeSwapsParticipantSides() {
        assertEquals(Role.SPEEDRUNNER, Role.HUNTER.opposite());
        assertEquals(Role.HUNTER, Role.SPEEDRUNNER.opposite());
        assertEquals(Role.NONE, Role.NONE.opposite());
    }
}
