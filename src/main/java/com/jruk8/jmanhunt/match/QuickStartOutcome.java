package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.player.Role;
import java.util.Set;

/** Outcome of a quick start: whether the match started plus cap-blocked roles. */
public record QuickStartOutcome(boolean started, Set<Role> cappedRoles) {
}