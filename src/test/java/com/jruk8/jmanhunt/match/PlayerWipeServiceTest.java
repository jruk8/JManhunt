package com.jruk8.jmanhunt.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.player.PlayerResetService;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Crash recovery cycles against a real SQLite store: each block opens
 * a fresh repository and service on the same folder to simulate one
 * server run, closing between runs like a restart would.
 */
class PlayerWipeServiceTest {

    private record Run(EngineStateRepository repository, PlayerWipeService wipes)
            implements AutoCloseable {
        @Override public void close() {
            repository.close();
        }
    }

    private static Run openRun(Path dir) throws SQLException {
        EngineStateRepository repository = EngineStateRepository.open(dir.toFile());
        PlayerWipeService wipes = new PlayerWipeService(repository,
                mock(JManhuntLogger.class), mock(PlayerResetService.class));
        return new Run(repository, wipes);
    }

    private static Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }

    @Test
    void cleanShutdownWithOwedWipesReloadsThemNextEnable(@TempDir Path dir) throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try (Run run = openRun(dir)) {
            run.wipes().trackMatchEntry(List.of(first, second));
            run.repository().setCrashFlag(true);
        }
        try (Run run = openRun(dir)) {
            run.wipes().loadCrashCleanup();

            assertTrue(run.wipes().hasPendingCrashWipes());
            // Mirrors JManhuntPlugin.writeCrashFlag: a clean shutdown
            // keeps the flag set while wipes are still owed.
            run.repository().setCrashFlag(run.wipes().hasPendingCrashWipes());
        }
        List<UUID> wiped = new ArrayList<>();
        try (Run run = openRun(dir)) {
            run.wipes().loadCrashCleanup();

            assertTrue(run.wipes().hasPendingCrashWipes());
            assertTrue(run.wipes().applyPendingCrashWipe(player(first),
                    joined -> wiped.add(joined.getUniqueId())));
            assertTrue(run.wipes().hasPendingCrashWipes());
            assertTrue(run.wipes().applyPendingCrashWipe(player(second),
                    joined -> wiped.add(joined.getUniqueId())));
            assertFalse(run.wipes().hasPendingCrashWipes());
            run.repository().setCrashFlag(run.wipes().hasPendingCrashWipes());
        }
        assertEquals(List.of(first, second), wiped);
        try (Run run = openRun(dir)) {
            run.wipes().loadCrashCleanup();

            assertFalse(run.wipes().hasPendingCrashWipes());
            assertFalse(run.wipes().applyPendingCrashWipe(player(first), joined -> { }));
        }
    }

    @Test
    void secondCrashAfterPartialDeliveryRearmsOnlyLeftovers(@TempDir Path dir) throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID joined = UUID.randomUUID();
        try (Run run = openRun(dir)) {
            run.wipes().trackMatchEntry(List.of(first, second));
            run.repository().setCrashFlag(true);
        }
        List<UUID> wiped = new ArrayList<>();
        try (Run run = openRun(dir)) {
            run.wipes().loadCrashCleanup();

            assertTrue(run.wipes().applyPendingCrashWipe(player(first),
                    rejoined -> wiped.add(rejoined.getUniqueId())));
            // A new match during the recovery run tracks its own entry.
            run.wipes().trackMatchEntry(List.of(joined));
            // Second crash: disable never runs, so the flag stays armed.
        }
        try (Run run = openRun(dir)) {
            run.wipes().loadCrashCleanup();

            assertFalse(run.wipes().applyPendingCrashWipe(player(first),
                    rejoined -> wiped.add(rejoined.getUniqueId())));
            assertTrue(run.wipes().applyPendingCrashWipe(player(second),
                    rejoined -> wiped.add(rejoined.getUniqueId())));
            assertTrue(run.wipes().applyPendingCrashWipe(player(joined),
                    rejoined -> wiped.add(rejoined.getUniqueId())));
            assertFalse(run.wipes().hasPendingCrashWipes());
        }
        assertEquals(List.of(first, second, joined), wiped);
    }

    @Test
    void cleanShutdownWithNothingOwedDropsStaleRows(@TempDir Path dir) throws Exception {
        UUID stale = UUID.randomUUID();
        try (Run run = openRun(dir)) {
            run.wipes().trackMatchEntry(List.of(stale));
            run.repository().setCrashFlag(false);
        }
        try (Run run = openRun(dir)) {
            run.wipes().loadCrashCleanup();

            assertFalse(run.wipes().hasPendingCrashWipes());
            assertTrue(run.repository().crashCleanupIds().isEmpty());
            assertFalse(run.wipes().applyPendingCrashWipe(player(stale), rejoined -> { }));
        }
    }

    @Test
    void cleanExitDuringRecoveryRunDropsPendingAndRow(@TempDir Path dir) throws Exception {
        UUID exited = UUID.randomUUID();
        UUID staying = UUID.randomUUID();
        try (Run run = openRun(dir)) {
            run.wipes().trackMatchEntry(List.of(exited, staying));
            run.repository().setCrashFlag(true);
        }
        try (Run run = openRun(dir)) {
            run.wipes().loadCrashCleanup();
            run.wipes().untrackMatchExit(List.of(exited));

            assertTrue(run.wipes().hasPendingCrashWipes());
            run.repository().setCrashFlag(run.wipes().hasPendingCrashWipes());
        }
        try (Run run = openRun(dir)) {
            run.wipes().loadCrashCleanup();

            assertFalse(run.wipes().applyPendingCrashWipe(player(exited), rejoined -> { }));
            assertTrue(run.wipes().applyPendingCrashWipe(player(staying), rejoined -> { }));
            assertFalse(run.wipes().hasPendingCrashWipes());
        }
    }
}
