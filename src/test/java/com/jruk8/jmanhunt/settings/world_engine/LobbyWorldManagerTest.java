package com.jruk8.jmanhunt.settings.world_engine;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyWorldManagerTest {

    @Test
    void generationConfirmsOnSecondRunWithinTimeout() {
        AtomicLong clock = new AtomicLong(1_000L);
        LobbyWorldManager manager = new LobbyWorldManager(clock::get);

        assertFalse(manager.confirmGeneration("sender", "jmh-lobby"));
        clock.set(1_000L + LobbyWorldManager.CONFIRM_TIMEOUT_MILLIS - 1L);
        assertTrue(manager.confirmGeneration("sender", "jmh-lobby"));
    }

    @Test
    void confirmationExpiresAfterTimeout() {
        AtomicLong clock = new AtomicLong(1_000L);
        LobbyWorldManager manager = new LobbyWorldManager(clock::get);

        assertFalse(manager.confirmGeneration("sender", "jmh-lobby"));
        clock.set(1_000L + LobbyWorldManager.CONFIRM_TIMEOUT_MILLIS + 1L);
        assertFalse(manager.confirmGeneration("sender", "jmh-lobby"));
    }

    @Test
    void renameBetweenRunsVoidsPendingConfirmation() {
        AtomicLong clock = new AtomicLong(1_000L);
        LobbyWorldManager manager = new LobbyWorldManager(clock::get);

        assertFalse(manager.confirmGeneration("sender", "jmh-lobby"));
        assertFalse(manager.confirmGeneration("sender", "other"));
        assertTrue(manager.confirmGeneration("sender", "other"));
    }

    @Test
    void confirmationsAreTrackedPerSender() {
        AtomicLong clock = new AtomicLong(1_000L);
        LobbyWorldManager manager = new LobbyWorldManager(clock::get);

        assertFalse(manager.confirmGeneration("one", "jmh-lobby"));
        assertFalse(manager.confirmGeneration("two", "jmh-lobby"));
        assertTrue(manager.confirmGeneration("two", "jmh-lobby"));
    }

    @Test
    void missingLobbyZeroDetectsUnsetLocation() {
        YamlConfiguration config = new YamlConfiguration();

        assertTrue(LobbyWorldManager.missingLobbyZero(config));

        config.set("world-engine.lobby-locations.0.world", "jmh-lobby");
        assertFalse(LobbyWorldManager.missingLobbyZero(config));
    }

    @Test
    void rescuePrefersMemberLobbyThenZero() {
        Location own = new Location(null, 1.0, 2.0, 3.0);
        Location zero = new Location(null, 4.0, 5.0, 6.0);
        Map<Integer, Location> locations = Map.of(0, zero, 2, own);

        assertEquals(Optional.of(own),
                LobbyWorldManager.selectRescueLocation(locations, OptionalInt.of(2)));
        assertEquals(Optional.of(zero),
                LobbyWorldManager.selectRescueLocation(locations, OptionalInt.of(9)));
        assertEquals(Optional.of(zero),
                LobbyWorldManager.selectRescueLocation(locations, OptionalInt.empty()));
        assertEquals(Optional.empty(),
                LobbyWorldManager.selectRescueLocation(Map.of(), OptionalInt.of(2)));
    }
}
