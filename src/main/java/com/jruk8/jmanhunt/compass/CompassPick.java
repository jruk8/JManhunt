package com.jruk8.jmanhunt.compass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pure compass target ranking. Active players beat the nearby fallback,
 * which beats last-seen locations; anything else spins the needle.
 */
public record CompassPick(Kind kind, UUID id, String name) {
    public enum Kind {
        TRACK_PLAYER,
        NEARBY,
        TRACK_SIGHTING,
        TOO_FAR,
        NONE
    }

    /**
     * Ranks opponents (nearest first wins) against the nearby and
     * tracking-distance settings, then last-seen locations of other
     * players. Never returns null. Pure for tests.
     */
    public static CompassPick resolve(List<CompassCandidate> opponents, List<CompassSighting> sightings,
            boolean nearbyEnabled, double nearbyThreshold, double trackingDistance) {
        List<CompassCandidate> ordered = new ArrayList<>(opponents);
        ordered.sort(Comparator.comparingDouble(CompassCandidate::distance));
        for (CompassCandidate candidate : ordered) {
            if (!tooClose(candidate, nearbyEnabled, nearbyThreshold)
                    && !tooFar(candidate.distance(), trackingDistance)) {
                return new CompassPick(Kind.TRACK_PLAYER, candidate.id(), candidate.name());
            }
        }
        for (CompassCandidate candidate : ordered) {
            if (tooClose(candidate, nearbyEnabled, nearbyThreshold)) {
                return new CompassPick(Kind.NEARBY, candidate.id(), candidate.name());
            }
        }
        UUID excluded = ordered.isEmpty() ? null : ordered.get(0).id();
        CompassSighting sighting = pickSighting(sightings, excluded, trackingDistance);
        if (sighting != null) {
            return new CompassPick(Kind.TRACK_SIGHTING, sighting.ownerId(), sighting.name());
        }
        if (!ordered.isEmpty()) {
            return new CompassPick(Kind.TOO_FAR, ordered.get(0).id(), ordered.get(0).name());
        }
        return new CompassPick(Kind.NONE, null, null);
    }

    private static boolean tooClose(CompassCandidate candidate, boolean nearbyEnabled, double nearbyThreshold) {
        return nearbyEnabled && nearbyThreshold > 0 && candidate.flatDistance() <= nearbyThreshold;
    }

    private static boolean tooFar(double distance, double trackingDistance) {
        return trackingDistance >= 0 && distance > trackingDistance;
    }

    /**
     * Nearest last-seen location owned by someone else, preferring one
     * within tracking range. Pure for tests.
     */
    static CompassSighting pickSighting(List<CompassSighting> sightings, UUID excluded, double trackingDistance) {
        return sightings.stream()
                .filter(sighting -> excluded == null || !sighting.ownerId().equals(excluded))
                .min(Comparator.comparingInt(
                                (CompassSighting sighting) -> tooFar(sighting.distance(), trackingDistance) ? 1 : 0)
                        .thenComparingDouble(CompassSighting::distance))
                .orElse(null);
    }
}
