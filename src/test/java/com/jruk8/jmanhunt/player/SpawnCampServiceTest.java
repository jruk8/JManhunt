package com.jruk8.jmanhunt.player;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SpawnCampServiceTest {

    private static final long WINDOW = 120_000L;

    @Test
    void countsKillsInsideRollingWindow() {
        AtomicLong clock = new AtomicLong(1_000L);
        SpawnCampService service = new SpawnCampService(null, null, clock::get);
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
        SpawnCampService service = new SpawnCampService(null, null, clock::get);
        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();

        service.recordKill(1L, attacker, victim, WINDOW);
        clock.set(1_000L + WINDOW + 1L);
        assertEquals(1, service.recordKill(1L, attacker, victim, WINDOW));
    }

    @Test
    void tracksPairsAndMatchesSeparately() {
        AtomicLong clock = new AtomicLong(1_000L);
        SpawnCampService service = new SpawnCampService(null, null, clock::get);
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
        SpawnCampService service = new SpawnCampService(null, null, clock::get);
        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();

        service.recordKill(1L, attacker, victim, WINDOW);
        service.recordKill(2L, attacker, victim, WINDOW);
        service.clearMatch(1L);
        assertEquals(1, service.recordKill(1L, attacker, victim, WINDOW));
        assertEquals(2, service.recordKill(2L, attacker, victim, WINDOW));
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
}
