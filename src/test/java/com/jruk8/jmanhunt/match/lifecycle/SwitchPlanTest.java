package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure pswitch roster plans: flips, alive flags, and deferred edges. */
class SwitchPlanTest {

    @Test
    void participantToParticipantKeepsRosterAndEdges() {
        MatchStartService.SwitchPlan plan = MatchStartService.planSwitch(Role.HUNTER,
                Role.SPEEDRUNNER, true, false);

        assertFalse(plan.activate());
        assertFalse(plan.deactivate());
        assertEquals(Boolean.TRUE, plan.runnerAlive());
        assertTrue(plan.participantEdge());
        assertFalse(plan.watcherEdge());
    }

    @Test
    void leavingRunnerClearsAlive() {
        MatchStartService.SwitchPlan plan = MatchStartService.planSwitch(Role.SPEEDRUNNER,
                Role.HUNTER, true, false);

        assertEquals(Boolean.FALSE, plan.runnerAlive());
        assertTrue(plan.participantEdge());
        assertFalse(plan.watcherEdge());
    }

    @Test
    void eliminatedRejoinReactivates() {
        MatchStartService.SwitchPlan plan = MatchStartService.planSwitch(Role.SPECTATOR,
                Role.HUNTER, false, false);

        assertTrue(plan.activate());
        assertFalse(plan.deactivate());
        assertNull(plan.runnerAlive());
        assertTrue(plan.participantEdge());
        assertFalse(plan.watcherEdge());
    }

    @Test
    void participantToWatcherDeactivatesAndEdges() {
        MatchStartService.SwitchPlan plan = MatchStartService.planSwitch(Role.HUNTER,
                Role.SPECTATOR, true, false);

        assertFalse(plan.activate());
        assertTrue(plan.deactivate());
        assertNull(plan.runnerAlive());
        assertFalse(plan.participantEdge());
        assertTrue(plan.watcherEdge());
    }

    @Test
    void inactiveWatcherStaysInactive() {
        MatchStartService.SwitchPlan plan = MatchStartService.planSwitch(Role.HUNTER,
                Role.NONE, false, false);

        assertFalse(plan.activate());
        assertFalse(plan.deactivate());
        assertTrue(plan.watcherEdge());
    }

    @Test
    void heldPlayerKeepsHeadstartHold() {
        MatchStartService.SwitchPlan plan = MatchStartService.planSwitch(Role.HUNTER,
                Role.SPEEDRUNNER, true, true);

        assertFalse(plan.activate());
        assertFalse(plan.participantEdge());
        assertEquals(Boolean.TRUE, plan.runnerAlive());
    }

    @Test
    void watcherToWatcherOnlyEdges() {
        MatchStartService.SwitchPlan plan = MatchStartService.planSwitch(Role.SPECTATOR,
                Role.NONE, true, false);

        assertTrue(plan.deactivate());
        assertNull(plan.runnerAlive());
        assertFalse(plan.participantEdge());
        assertTrue(plan.watcherEdge());
    }
}
