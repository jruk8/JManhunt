package com.jruk8.jmanhunt.player;

import org.bukkit.Location;
import java.util.List;
import java.util.Optional;

/**
 * Spectator travel limit: watchers roaming farther than the cap from
 * every anchor are teleported back. Anchors are online participant
 * spots plus last-seen spots, with the cell center as the fallback.
 * Silent by design: no message, no sound.
 */
public final class SpectatorTravelService {

    private SpectatorTravelService() {
    }

    /**
     * Teleport target when the spectator exceeds the cap, else empty.
     * Same-world anchors compare by distance; with none in reach the
     * cell center wins (cross-world counts as exceeded). Pure for tests.
     */
    public static Optional<Location> teleportTarget(Location spectator, List<Location> anchors,
            Location cellCenter, double maxDistance) {
        List<Location> effective = anchors.isEmpty() && cellCenter != null
                ? List.of(cellCenter) : anchors;
        List<Location> sameWorld = effective.stream()
                .filter(anchor -> anchor != null && sameWorld(spectator, anchor)).toList();
        if (sameWorld.isEmpty()) {
            return Optional.ofNullable(cellCenter);
        }
        Location nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Location anchor : sameWorld) {
            double distance = spectator.distance(anchor);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = anchor;
            }
        }
        if (nearest != null && nearestDistance > maxDistance) {
            return Optional.of(nearest);
        }
        return Optional.empty();
    }

    private static boolean sameWorld(Location left, Location right) {
        return left.getWorld() != null && right.getWorld() != null
                && left.getWorld().getUID().equals(right.getWorld().getUID());
    }
}
