package com.jruk8.jmanhunt.api;

import java.util.UUID;

/**
 * Public API for the JManhunt plugin.
 *
 * <p>External plugins (such as JManhunt-Challenges) obtain the API from the
 * server's {@code ServicesManager} after JManhunt enables:</p>
 *
 * <pre>{@code
 * RegisteredServiceProvider<JManhuntApi> provider = Bukkit.getServicesManager()
 *         .getRegistration(JManhuntApi.class);
 * JManhuntApi api = provider == null ? null : provider.getProvider();
 * }</pre>
 *
 * <p>Match lifecycle changes are also published as Bukkit events in
 * {@code com.jruk8.jmanhunt.api.events}: {@code JMatchStartEvent},
 * {@code JGameBeginEvent} and {@code JMatchEndEvent}.</p>
 */
public interface JManhuntApi {

    /** Returns true while a match is ongoing (including the pre-start window). */
    boolean isMatchActive();

    /** Returns true once the active match has actually begun (after the pre-start window). */
    boolean hasGameBegun();

    /** Returns true while a match is being finished (end delay running). */
    boolean isMatchEnding();

    /** Returns the incrementing id of the running match, or the id of the last finished match. */
    long getMatchId();

    /** Returns the current match role of the given player, never null. */
    PlayerRole getRole(UUID playerId);

    /** Returns true if the given player is currently an active match participant (hunter or speedrunner). */
    boolean isParticipant(UUID playerId);
}