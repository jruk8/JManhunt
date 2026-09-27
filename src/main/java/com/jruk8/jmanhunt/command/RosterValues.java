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
