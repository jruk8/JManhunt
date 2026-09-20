package com.jruk8.jmanhunt.api;

import java.util.List;
import java.util.Set;
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
 * <p>Several matches can run concurrently, one per lobby. The parameterless
 * state checks aggregate across all of them; the match id overloads scope to
 * exactly one match. Unknown match ids read as empty: no match, no players,
 * {@code false} states, and {@code -1} ids.</p>
 *
 * <p>Match lifecycle changes are also published as Bukkit events in
 * {@code com.jruk8.jmanhunt.api.events}: {@code JMatchStartEvent},
 * {@code JGameBeginEvent}, {@code JMatchEndEvent},
 * {@code JMatchCancelEvent}, and {@code JPlayerJoinMatchEvent}.</p>
 */
public interface JManhuntApi {

    /** Returns true while a match is ongoing (including the pre-start window). */
    boolean isMatchActive();

    /** Returns true once any live match has actually begun (after the pre-start window). */
    boolean hasGameBegun();

    /** Returns true while any match is being finished (end delay running). */
    boolean isMatchEnding();

    /** Returns the incrementing id of the most recently started match. */
    long getMatchId();

    /** Returns the ids of all live matches, oldest first. Empty when none run. */
    List<Long> getLiveMatchIds();

    /** Returns true while the given match id belongs to a live match. */
    boolean isMatchLive(long matchId);

    /** Returns true once the given match has begun. False for unknown matches. */
    boolean hasMatchBegun(long matchId);

    /** Returns true while the given match is being finished. False for unknown matches. */
    boolean isMatchEnding(long matchId);

    /** Returns the live match id of the given player, or -1 when in no live match. */
    long getMatchId(UUID playerId);

    /** Returns the active participants of a live match. Empty for unknown matches. */
    Set<UUID> getMatchPlayers(long matchId);

    /** Returns the lobby id a match was started from, or -1 for unknown matches. */
    int getOriginLobbyId(long matchId);

    /** Returns the current match role of the given player, never null. */
    PlayerRole getRole(UUID playerId);

    /** Returns true if the given player is currently an active match participant (hunter or speedrunner). */
    boolean isParticipant(UUID playerId);

    /** Returns the ids of all known lobbies. */
    Set<Integer> getLobbyIds();

    /** Returns the members of a lobby. Empty for unknown lobbies. */
    Set<UUID> getLobbyPlayers(int lobbyId);

    /** Returns the lobby id of the given player, or -1 when lobby-less. */
    int getPlayerLobbyId(UUID playerId);
}
