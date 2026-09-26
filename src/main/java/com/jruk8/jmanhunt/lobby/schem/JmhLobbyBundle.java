package com.jruk8.jmanhunt.lobby.schem;

import java.util.List;

/**
 * One .jmhlobby bundle: raw structure bytes plus the lobby boxes and
 * teleports collected with them, all stored relative to the save-time
 * minimum corner. Bound offsets are integers; teleports keep one
 * decimal place, and look direction keeps its floats.
 */
public record JmhLobbyBundle(byte[] nbt, Offset origin,
        List<BoundEntry> bounds, List<TeleportEntry> teleports) {

    /** Integer block offset triple. */
    public record Offset(int x, int y, int z) {
    }

    /** One lobby's bounds box as relative min/max corners. */
    public record BoundEntry(int lobby, Offset min, Offset max) {
    }

    /** One lobby's teleport as relative coords plus look direction. */
    public record TeleportEntry(int lobby, double x, double y, double z, float yaw, float pitch) {
    }
}
