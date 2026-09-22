package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.player.Role;

/**
 * Callbacks from the leaf match services into the start/finish paths.
 * The leaves schedule and decide; the facade breaks the constructor
 * cycle by implementing these with the start and finish services.
 */
interface MatchControl {
    /** Begins play for a waiting match (force-start path). */
    void beginGame(GameInstance instance);

    /** Finishes a begun match with the given winner. */
    void finish(GameInstance instance, Role winner);

    /** Tears down a match and frees its world-engine resources. */
    void teardownNow(GameInstance instance);

    /** Starts a match for one lobby's queued players. */
    boolean start(int lobbyId);
}
