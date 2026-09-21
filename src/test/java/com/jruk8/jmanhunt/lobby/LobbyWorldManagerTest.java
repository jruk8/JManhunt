package com.jruk8.jmanhunt.lobby;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void missingLobbyZeroDetectsUnsetLobbytp() {
        LobbyConfig lobbyConfig = new LobbyConfig();
        lobbyConfig.getLobbies().clear();

        assertTrue(LobbyWorldManager.missingLobbyZero(lobbyConfig));
        assertTrue(LobbyWorldManager.missingLobbyZero(null));

        LobbyConfig.LobbyEntry entry = new LobbyConfig.LobbyEntry();
        lobbyConfig.getLobbies().put("0", entry);
        assertTrue(LobbyWorldManager.missingLobbyZero(lobbyConfig));

        entry.setLobbytp(LobbyConfig.LobbyTp.of(0.0, 65.0, 0.0, 0.0f, 0.0f));
        assertFalse(LobbyWorldManager.missingLobbyZero(lobbyConfig));
    }

    @Test
    void namesClashIgnoresCase() {
        assertTrue(LobbyWorldManager.namesClash("world", "world"));
        assertTrue(LobbyWorldManager.namesClash("World", "world"));
        assertFalse(LobbyWorldManager.namesClash("jmh-lobby", "world"));
        assertFalse(LobbyWorldManager.namesClash(null, "world"));
        assertFalse(LobbyWorldManager.namesClash("jmh-lobby", null));
    }

    @Test
    void rescueStaysInLobbyWorld() {
        World lobbyWorld = mock(World.class);
        when(lobbyWorld.getName()).thenReturn("jmh-lobby");
        World gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("world");

        Location lobbySpot = new Location(lobbyWorld, 0.5, 65.0, 0.5);
        Location gameSpot = new Location(gameWorld, 1.0, 2.0, 3.0);
        assertEquals(Optional.of(lobbySpot),
                LobbyWorldManager.inLobbyWorld(Optional.of(lobbySpot), "jmh-lobby"));
        assertEquals(Optional.empty(),
                LobbyWorldManager.inLobbyWorld(Optional.of(gameSpot), "jmh-lobby"));
        assertEquals(Optional.empty(),
                LobbyWorldManager.inLobbyWorld(Optional.of(new Location(null, 0, 0, 0)), "jmh-lobby"));
        assertEquals(Optional.empty(),
                LobbyWorldManager.inLobbyWorld(Optional.empty(), "jmh-lobby"));
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
