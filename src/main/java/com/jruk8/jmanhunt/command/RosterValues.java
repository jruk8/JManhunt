package com.jruk8.jmanhunt.command;

import org.bukkit.Location;
import org.bukkit.util.Vector;
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

    /**
     * True when the name is eliminated or was never in the game: only
     * names active in a live instance read false. Defaults to true.
     */
    default boolean eliminated(String playerName) {
        return true;
    }

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

    /**
     * Unit look direction for an online player (eye location, y-up),
     * or empty when the player is offline or unknown. Defaults to
     * empty.
     */
    default Optional<Vector> lookDirection(String playerName) {
        return Optional.empty();
    }

    /**
     * One body state for an online player, or empty when the player
     * is offline or unknown. The state arrives canonical upper-case
     * ({@code SNEAK}, {@code SPRINT}, {@code GLIDE}, {@code SWIM},
     * {@code GROUND}); tags validate names before calling. Defaults
     * to empty.
     */
    default Optional<Boolean> playerState(String playerName, String state) {
        return Optional.empty();
    }

    /**
     * Upper-case material of the block below an online player's
     * feet, or empty when the player is offline or unknown. Over
     * the void the block is air and backends report {@code AIR}.
     * Defaults to empty.
     */
    default Optional<String> standingOn(String playerName) {
        return Optional.empty();
    }

    /** One slot address: a named equipment slot or a raw index. */
    sealed interface InventorySlot permits InventorySlot.Named, InventorySlot.Index {
        /** Named equipment slot, canonical upper-case. */
        record Named(String name) implements InventorySlot {
        }

        /** Raw player-inventory index, bounds-checked by callers. */
        record Index(int index) implements InventorySlot {
        }
    }

    /** One slot's contents: upper-case material plus quantity. */
    record SlotContent(String material, int qty) {
    }

    /**
     * One slot's contents for an online player, or empty when the
     * player is offline or unknown, or the slot is empty. Defaults
     * to empty.
     */
    default Optional<SlotContent> slotItem(String playerName, InventorySlot slot) {
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
