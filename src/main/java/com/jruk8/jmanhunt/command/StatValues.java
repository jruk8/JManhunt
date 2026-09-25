package com.jruk8.jmanhunt.command;

import java.util.Optional;

/**
 * Stat values behind {@code <pstat>} and {@code <gstat>}. Pure seam:
 * the Bukkit-backed implementation lives in the match package while
 * tests inject fakes. Empty means unavailable (offline player, no
 * live match, no loaded world); callers warn with the context.
 */
public interface StatValues {

    /** One player's stat, or empty when the player is offline. */
    Optional<String> player(String playerName, String key);

    /** One global stat, or empty when no match or world backs it. */
    Optional<String> global(String key);

    /** Backend with no values: every lookup misses. */
    static StatValues inert() {
        return new StatValues() {
            @Override
            public Optional<String> player(String playerName, String key) {
                return Optional.empty();
            }

            @Override
            public Optional<String> global(String key) {
                return Optional.empty();
            }
        };
    }
}
