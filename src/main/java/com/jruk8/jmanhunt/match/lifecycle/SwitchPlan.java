package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.player.Role;

/** Pure role-switch roster plan behind pswitch. Pure for tests. */
record SwitchPlan(boolean activate, boolean deactivate, Boolean runnerAlive,
        boolean participantEdge, boolean watcherEdge) {

    /**
     * Plans one role switch: roster flips, the runner-alive flag
     * (null when untouched), and which deferred edge applies. Held
     * players keep their headstart hold, so no participant edge
     * runs for them. Pure for tests.
     */
    static SwitchPlan planSwitch(Role source, Role target, boolean active, boolean held) {
        Boolean runnerAlive = target == Role.SPEEDRUNNER ? Boolean.TRUE
                : source == Role.SPEEDRUNNER ? Boolean.FALSE : null;
        return new SwitchPlan(target.isParticipant() && !active,
                !target.isParticipant() && active, runnerAlive,
                target.isParticipant() && !held, !target.isParticipant());
    }
}
