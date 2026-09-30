package com.jruk8.jmanhunt.command;

import org.bukkit.Location;
import java.util.List;
import java.util.Optional;

/**
 * Match roster reads for tags: assignee roles, eligible-player
 * lists, and online locations. Implemented by the match layer so
 * tags stay pure; managers own every Bukkit call.
 */
public interface RosterValues {

    /**
     * Raw role name for a match assignee (any role, active or not),
     * or empty when no assignee matches the name.
     */
    Optional<String> roleOf(String playerName);

    /** Alphabetical eligible names for one participant role. */
    List<String> activePlayers(String role);

    /** Online player location by case-insensitive name, or empty. */
    Optional<Location> locationOf(String playerName);

    /**
     * One online participant for proximity scans: display name, raw
     * upper-case role, coords, and world environment plus name (both
     * raw; tags normalize through
     * {@link TagLocations#worldAlias}).
     */
    record NearbyParticipant(String name, String role, double x, double y, double z,
            String environment, String world) {
    }

    /**
     * Interval-eligible online participants of both teams for
     * proximity tags (the same population {@link #activePlayers}
     * draws from). Defaults to empty.
     */
    default List<NearbyParticipant> nearbyParticipants() {
        return List.of();
    }

    /**
     * Storage-contents count of one material for an online player, or
     * empty when the player is offline or the key resolves to
     * nothing. The key arrives raw (any case, optional
     * {@code minecraft:} prefix); live backends normalize through
     * {@link TagItems#normalizeMaterialKey}. Defaults to empty.
     */
    default Optional<Integer> countItem(String playerName, String materialKey) {
        return Optional.empty();
    }

    /**
     * Upper-case main-hand material for an online player, or empty
     * when the player is offline, unknown, or empty-handed.
     * Defaults to empty.
     */
    default Optional<String> heldItem(String playerName) {
        return Optional.empty();
    }

    /** Roster that resolves nothing. */
    static RosterValues inert() {
        return new RosterValues() {
            @Override
            public Optional<String> roleOf(String playerName) {
                return Optional.empty();
            }

            @Override
            public List<String> activePlayers(String role) {
                return List.of();
            }

            @Override
            public Optional<Location> locationOf(String playerName) {
                return Optional.empty();
            }
        };
    }
}
