package com.jruk8.jmanhunt.player;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpawnCampServiceTest {

    private static final long WINDOW = 120_000L;

    @Test
    void countsKillsInsideRollingWindow() {
        AtomicLong clock = new AtomicLong(1_000L);
        SpawnCampService service = new SpawnCampService(null, null, null, null, clock::get);
        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();

        assertEquals(1, service.recordKill(1L, attacker, victim, WINDOW));
        clock.set(2_000L);
        assertEquals(2, service.recordKill(1L, attacker, victim, WINDOW));
        clock.set(3_000L);
        assertEquals(3, service.recordKill(1L, attacker, victim, WINDOW));
    }

    @Test
    void oldKillsFallOutOfWindow() {
        AtomicLong clock = new AtomicLong(1_000L);
        SpawnCampService service = new SpawnCampService(null, null, null, null, clock::get);
        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();

        service.recordKill(1L, attacker, victim, WINDOW);
        clock.set(1_000L + WINDOW + 1L);
        assertEquals(1, service.recordKill(1L, attacker, victim, WINDOW));
    }

    @Test
    void tracksPairsAndMatchesSeparately() {
        AtomicLong clock = new AtomicLong(1_000L);
        SpawnCampService service = new SpawnCampService(null, null, null, null, clock::get);
        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        service.recordKill(1L, attacker, victim, WINDOW);
        assertEquals(1, service.recordKill(1L, attacker, other, WINDOW));
        assertEquals(1, service.recordKill(2L, attacker, victim, WINDOW));
        assertEquals(2, service.recordKill(1L, attacker, victim, WINDOW));
    }

    @Test
    void clearMatchDropsOnlyThatMatch() {
        AtomicLong clock = new AtomicLong(1_000L);
        SpawnCampService service = new SpawnCampService(null, null, null, null, clock::get);
        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();

        service.recordKill(1L, attacker, victim, WINDOW);
        service.recordKill(2L, attacker, victim, WINDOW);
        service.clearMatch(1L);
        assertEquals(1, service.recordKill(1L, attacker, victim, WINDOW));
        assertEquals(2, service.recordKill(2L, attacker, victim, WINDOW));
    }

    @Test
    void offensesCountPerAttackerAcrossVictimsAndMatches() {
        AtomicLong clock = new AtomicLong(1_000L);
        SpawnCampService service = new SpawnCampService(null, null, null, null, clock::get);
        UUID attacker = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        assertEquals(1, service.recordOffense(1L, attacker));
        assertEquals(2, service.recordOffense(1L, attacker));
        assertEquals(1, service.recordOffense(1L, other));
        assertEquals(1, service.recordOffense(2L, attacker));
    }

    @Test
    void clearMatchDropsOffenses() {
        AtomicLong clock = new AtomicLong(1_000L);
        SpawnCampService service = new SpawnCampService(null, null, null, null, clock::get);
        UUID attacker = UUID.randomUUID();

        service.recordOffense(1L, attacker);
        service.recordOffense(2L, attacker);
        service.clearMatch(1L);
        assertEquals(1, service.recordOffense(1L, attacker));
        assertEquals(2, service.recordOffense(2L, attacker));
    }

    @Test
    void suppressDeathTriggerTagsOnlyDuringAction() {
        SpawnCampService service = new SpawnCampService(null, null, null, null, () -> 1_000L);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        AtomicBoolean suppressedInside = new AtomicBoolean(false);

        service.suppressDeathTrigger(player,
                () -> suppressedInside.set(service.isDeathTriggerSuppressed(id)));

        assertTrue(suppressedInside.get());
        assertFalse(service.isDeathTriggerSuppressed(id));
    }

    @Test
    void suppressDeathTriggerClearsTagOnException() {
        SpawnCampService service = new SpawnCampService(null, null, null, null, () -> 1_000L);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);

        assertThrows(IllegalStateException.class, () -> service.suppressDeathTrigger(player,
                () -> {
                    throw new IllegalStateException("boom");
                }));
        assertFalse(service.isDeathTriggerSuppressed(id));
    }

    @Test
    void punishmentParsesWithKillFallback() {
        assertEquals(SpawnCampService.Punishment.GEAR_WIPE,
                SpawnCampService.Punishment.parse("GEAR-WIPE"));
        assertEquals(SpawnCampService.Punishment.GEAR_WIPE,
                SpawnCampService.Punishment.parse(" gear-wipe "));
        assertEquals(SpawnCampService.Punishment.KILL,
                SpawnCampService.Punishment.parse("kill"));
        assertEquals(SpawnCampService.Punishment.KILL,
                SpawnCampService.Punishment.parse("ban"));
        assertEquals(SpawnCampService.Punishment.KILL,
                SpawnCampService.Punishment.parse(null));
    }

    @Test
    void shouldWarnFiresOnlyAtOneBelowLimit() {
        assertTrue(SpawnCampService.shouldWarn(2, 3));
        assertFalse(SpawnCampService.shouldWarn(1, 3));
        assertFalse(SpawnCampService.shouldWarn(3, 3));
        assertFalse(SpawnCampService.shouldWarn(0, 1));
    }

    @Test
    void isMonitoredMatchesRolesCaseInsensitively() {
        assertTrue(SpawnCampService.isMonitored(Role.SPEEDRUNNER, List.of("SPEEDRUNNER")));
        assertTrue(SpawnCampService.isMonitored(Role.HUNTER, List.of(" hunter ")));
        assertTrue(SpawnCampService.isMonitored(Role.HUNTER,
                List.of("SPEEDRUNNER", "HUNTER")));
        assertFalse(SpawnCampService.isMonitored(Role.HUNTER, List.of("SPEEDRUNNER")));
        assertFalse(SpawnCampService.isMonitored(Role.SPECTATOR,
                List.of("SPEEDRUNNER", "HUNTER")));
        assertFalse(SpawnCampService.isMonitored(Role.HUNTER, List.of()));
        assertFalse(SpawnCampService.isMonitored(Role.HUNTER, null));
        assertFalse(SpawnCampService.isMonitored(Role.HUNTER, List.of("ADMIN")));
        assertFalse(SpawnCampService.isMonitored(Role.HUNTER,
                Arrays.asList(null, "  ")));
    }
}
