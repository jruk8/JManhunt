package com.jruk8.jmanhunt.player;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Spectator spawn priority: top runner, hunter, last-seen, center. */
class SpectatorSpawnResolverTest {

    private static final Location CENTER = new Location(null, 0.0, 64.0, 0.0);

    private static SpectatorSpawnResolver.SpawnCandidate online(String name, Role role,
            int progression, double x) {
        return new SpectatorSpawnResolver.SpawnCandidate(name, role, true, progression,
                new Location(null, x, 64.0, 0.0), null);
    }

    private static SpectatorSpawnResolver.SpawnCandidate offline(String name, Role role,
            Double seenX) {
        Location seen = seenX == null ? null : new Location(null, seenX, 64.0, 0.0);
        return new SpectatorSpawnResolver.SpawnCandidate(name, role, false, 0, null, seen);
    }

    @Test
    void prefersHighestProgressionRunner() {
        Location iron = new Location(null, 2.0, 64.0, 0.0);
        List<SpectatorSpawnResolver.SpawnCandidate> candidates = List.of(
                online("Wood", Role.SPEEDRUNNER, 1, 1.0),
                new SpectatorSpawnResolver.SpawnCandidate("Iron", Role.SPEEDRUNNER,
                        true, 2, iron, null),
                online("Hunter", Role.HUNTER, 7, 3.0));

        assertEquals(Optional.of(iron),
                SpectatorSpawnResolver.resolve(candidates, CENTER));
    }

    @Test
    void hunterBeatsLastSeenRunner() {
        Location hunter = new Location(null, 3.0, 64.0, 0.0);
        List<SpectatorSpawnResolver.SpawnCandidate> candidates = List.of(
                offline("Runner", Role.SPEEDRUNNER, 9.0),
                new SpectatorSpawnResolver.SpawnCandidate("Hunter", Role.HUNTER,
                        true, 0, hunter, null));

        assertEquals(Optional.of(hunter),
                SpectatorSpawnResolver.resolve(candidates, CENTER));
    }

    @Test
    void lastSeenRunnerBeatsLastSeenHunter() {
        List<SpectatorSpawnResolver.SpawnCandidate> candidates = List.of(
                offline("Zed", Role.HUNTER, 8.0),
                offline("Runner", Role.SPEEDRUNNER, 9.0));

        assertEquals(Optional.of(new Location(null, 9.0, 64.0, 0.0)),
                SpectatorSpawnResolver.resolve(candidates, CENTER));
    }

    @Test
    void fallsBackToCellCenter() {
        List<SpectatorSpawnResolver.SpawnCandidate> candidates = List.of(
                offline("Runner", Role.SPEEDRUNNER, null),
                offline("Hunter", Role.HUNTER, null));

        assertEquals(Optional.of(CENTER),
                SpectatorSpawnResolver.resolve(candidates, CENTER));
    }

    @Test
    void emptyPickWithoutCenter() {
        assertEquals(Optional.empty(), SpectatorSpawnResolver.resolve(List.of(), null));
    }

    @Test
    void tiesBreakByName() {
        List<SpectatorSpawnResolver.SpawnCandidate> candidates = List.of(
                online("zed", Role.HUNTER, 3, 3.0),
                online("Amy", Role.HUNTER, 3, 4.0));

        assertEquals(Optional.of(new Location(null, 4.0, 64.0, 0.0)),
                SpectatorSpawnResolver.resolve(candidates, CENTER));
    }
}
