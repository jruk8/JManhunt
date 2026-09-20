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
}
