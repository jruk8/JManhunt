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
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.world.LobbyWorldManager;

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
        assertFalse(LobbyWorldManager.namesClash("jmh_lobby", "world"));
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
    void lowestLobbyTpPicksLowestIdWithTeleport() {
        LobbyConfig lobbyConfig = new LobbyConfig();
        lobbyConfig.getLobbies().clear();
        LobbyConfig.LobbyEntry two = new LobbyConfig.LobbyEntry();
        two.setLobbytp(LobbyConfig.LobbyTp.of(2.0, 65.0, 2.0, 0.0f, 0.0f));
        LobbyConfig.LobbyEntry five = new LobbyConfig.LobbyEntry();
        five.setLobbytp(LobbyConfig.LobbyTp.of(5.0, 65.0, 5.0, 0.0f, 0.0f));
        LobbyConfig.LobbyEntry unset = new LobbyConfig.LobbyEntry();
        lobbyConfig.getLobbies().put("5", five);
        lobbyConfig.getLobbies().put("bogus", five);
        lobbyConfig.getLobbies().put("-1", five);
        lobbyConfig.getLobbies().put("1", unset);
        lobbyConfig.getLobbies().put("2", two);

        Optional<Map.Entry<Integer, LobbyConfig.LobbyTp>> lowest =
                LobbyWorldManager.lowestLobbyTp(lobbyConfig.getLobbies());

        assertTrue(lowest.isPresent());
        assertEquals(2, lowest.get().getKey());
        assertEquals(2.0, lowest.get().getValue().getX());
    }

    @Test
    void lowestLobbyTpEmptyWhenNoneAvailable() {
        assertEquals(Optional.empty(), LobbyWorldManager.lowestLobbyTp(null));
        assertEquals(Optional.empty(), LobbyWorldManager.lowestLobbyTp(Map.of()));
        LobbyConfig lobbyConfig = new LobbyConfig();
        lobbyConfig.getLobbies().clear();
        lobbyConfig.getLobbies().put("0", new LobbyConfig.LobbyEntry());
        assertEquals(Optional.empty(),
                LobbyWorldManager.lowestLobbyTp(lobbyConfig.getLobbies()));
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
