package com.jruk8.jmanhunt.compass;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CompassPickTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();

    private static CompassCandidate candidate(UUID id, String name, double distance, double flat) {
        return new CompassCandidate(id, name, distance, flat);
    }

    private static CompassSighting sighting(UUID owner, String name, double distance) {
        return new CompassSighting(owner, name, distance);
    }

    @Test
    void nearestInRangeOpponentIsTracked() {
        CompassPick pick = CompassPick.resolve(
                List.of(candidate(ALICE, "Alice", 100.0, 100.0),
                        candidate(BOB, "Bob", 200.0, 200.0)),
                List.of(), true, 25.0, 500.0);
        assertEquals(CompassPick.Kind.TRACK_PLAYER, pick.kind());
        assertEquals(ALICE, pick.id());
        assertEquals("Alice", pick.name());
    }

    @Test
    void tooCloseNearestFallsThroughToNextActiveOpponent() {
        CompassPick pick = CompassPick.resolve(
                List.of(candidate(ALICE, "Alice", 10.0, 10.0),
                        candidate(BOB, "Bob", 100.0, 100.0)),
                List.of(), true, 25.0, 500.0);
        assertEquals(CompassPick.Kind.TRACK_PLAYER, pick.kind());
        assertEquals(BOB, pick.id());
    }

    @Test
    void allTooCloseShowsNearby() {
        CompassPick pick = CompassPick.resolve(
                List.of(candidate(ALICE, "Alice", 10.0, 10.0)),
                List.of(sighting(BOB, "Bob", 50.0)), true, 25.0, 500.0);
        assertEquals(CompassPick.Kind.NEARBY, pick.kind());
        assertEquals(ALICE, pick.id());
        assertEquals("Alice", pick.name());
    }

    @Test
    void tooFarFallsBackToAnotherPlayersSighting() {
        CompassPick pick = CompassPick.resolve(
                List.of(candidate(ALICE, "Alice", 1000.0, 1000.0)),
                List.of(sighting(BOB, "Bob", 300.0)), true, 25.0, 500.0);
        assertEquals(CompassPick.Kind.TRACK_SIGHTING, pick.kind());
        assertEquals(BOB, pick.id());
    }

    @Test
    void ownSightingIsExcludedWhenTooFar() {
        CompassPick pick = CompassPick.resolve(
                List.of(candidate(ALICE, "Alice", 1000.0, 1000.0)),
                List.of(sighting(ALICE, "Alice", 900.0)), true, 25.0, 500.0);
        assertEquals(CompassPick.Kind.TOO_FAR, pick.kind());
        assertEquals("Alice", pick.name());
    }

    @Test
    void noOpponentsUsesSighting() {
        CompassPick pick = CompassPick.resolve(
                List.of(), List.of(sighting(BOB, "Bob", 300.0)), true, 25.0, 500.0);
        assertEquals(CompassPick.Kind.TRACK_SIGHTING, pick.kind());
        assertEquals(BOB, pick.id());
    }

    @Test
    void nothingAvailableSpinsWithNone() {
        CompassPick pick = CompassPick.resolve(List.of(), List.of(), true, 25.0, 500.0);
        assertEquals(CompassPick.Kind.NONE, pick.kind());
        assertNull(pick.id());
        assertNull(pick.name());
    }

    @Test
    void nearbyDisabledTracksCloseOpponent() {
        CompassPick pick = CompassPick.resolve(
                List.of(candidate(ALICE, "Alice", 10.0, 10.0)),
                List.of(), false, 25.0, 500.0);
        assertEquals(CompassPick.Kind.TRACK_PLAYER, pick.kind());
        assertEquals(ALICE, pick.id());
    }

    @Test
    void unlimitedRangeTracksFarOpponent() {
        CompassPick pick = CompassPick.resolve(
                List.of(candidate(ALICE, "Alice", 100000.0, 100000.0)),
                List.of(), true, 25.0, -1.0);
        assertEquals(CompassPick.Kind.TRACK_PLAYER, pick.kind());
        assertEquals(ALICE, pick.id());
    }

    @Test
    void inRangeSightingBeatsNearerOutOfRangeSighting() {
        CompassPick pick = CompassPick.resolve(
                List.of(),
                List.of(sighting(BOB, "Bob", 900.0), sighting(CAROL, "Carol", 300.0)),
                true, 25.0, 500.0);
        assertEquals(CompassPick.Kind.TRACK_SIGHTING, pick.kind());
        assertEquals(CAROL, pick.id());
    }

    @Test
    void unsortedOpponentsStillPickNearest() {
        CompassPick pick = CompassPick.resolve(
                List.of(candidate(BOB, "Bob", 200.0, 200.0),
                        candidate(ALICE, "Alice", 100.0, 100.0)),
                List.of(), true, 25.0, 500.0);
        assertEquals(CompassPick.Kind.TRACK_PLAYER, pick.kind());
        assertEquals(ALICE, pick.id());
    }

    @Test
    void lockCycleRunsOpponentsThenSightingsAndWrapsToAutomatic() {
        List<CompassCandidate> opponents = List.of(
                candidate(BOB, "Bob", 200.0, 200.0),
                candidate(ALICE, "Alice", 100.0, 100.0));
        List<CompassSighting> sightings = List.of(sighting(CAROL, "Carol", 50.0));

        assertEquals(ALICE, CompassPick.cycleLock(opponents, sightings, null, 5));
        assertEquals(BOB, CompassPick.cycleLock(opponents, sightings, ALICE, 5));
        assertEquals(CAROL, CompassPick.cycleLock(opponents, sightings, BOB, 5));
        assertNull(CompassPick.cycleLock(opponents, sightings, CAROL, 5));
    }

    @Test
    void lockCycleStaysAutomaticWithoutCandidates() {
        assertNull(CompassPick.cycleLock(List.of(), List.of(), null, 5));
    }

    @Test
    void lockCycleCapsCandidatesAndDropsStaleLocksToAutomatic() {
        List<CompassCandidate> opponents = List.of(
                candidate(ALICE, "Alice", 100.0, 100.0),
                candidate(BOB, "Bob", 200.0, 200.0),
                candidate(CAROL, "Carol", 300.0, 300.0));

        // Capped at two: Bob is last, Carol never cycles in.
        assertEquals(BOB, CompassPick.cycleLock(opponents, List.of(), ALICE, 2));
        assertNull(CompassPick.cycleLock(opponents, List.of(), BOB, 2));
        // A lock that left the candidate set returns to automatic.
        assertNull(CompassPick.cycleLock(opponents, List.of(), CAROL, 2));
        assertNull(CompassPick.cycleLock(opponents, List.of(), UUID.randomUUID(), 5));
    }

    @Test
    void lockCycleDedupesLivePlayersBeforeTheirSightings() {
        List<CompassCandidate> opponents = List.of(candidate(ALICE, "Alice", 100.0, 100.0));
        List<CompassSighting> sightings = List.of(sighting(ALICE, "Alice", 50.0));

        assertEquals(ALICE, CompassPick.cycleLock(opponents, sightings, null, 5));
        assertNull(CompassPick.cycleLock(opponents, sightings, ALICE, 5));
    }

    @Test
    void lockCycleClampsNonPositiveCapToOne() {
        List<CompassCandidate> opponents = List.of(
                candidate(ALICE, "Alice", 100.0, 100.0),
                candidate(BOB, "Bob", 200.0, 200.0));

        assertEquals(ALICE, CompassPick.cycleLock(opponents, List.of(), null, 0));
        assertNull(CompassPick.cycleLock(opponents, List.of(), ALICE, -3));
    }
}
