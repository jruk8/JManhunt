package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.lobby.SubLobby;
import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameInstanceTest {

    @Test
    void assignedRosterOnlyGrows() {
        GameInstance instance = new GameInstance(3L, 0, OptionalLong.of(12L), 1_000L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        instance.assignAll(List.of(first));
        instance.assignAll(List.of(first, second));

        assertEquals(2, instance.assignedCount());
        assertEquals(2, instance.assignedPlayerIds().size());
        assertTrue(instance.assignedPlayerIds().contains(first));
        assertTrue(instance.assignedPlayerIds().contains(second));
    }

    @Test
    void identityAndCellAreExposed() {
        GameInstance instance = new GameInstance(3L, 2, OptionalLong.of(12L), 1_000L);

        assertEquals(3L, instance.matchId());
        assertEquals(2, instance.originLobbyId());
        assertEquals(OptionalLong.of(12L), instance.cellIndex());
    }

    @Test
    void emptyCellIndexMeansNoWorldEngine() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);

        assertTrue(instance.cellIndex().isEmpty());
    }

    @Test
    void activeRosterIsIndependentFromAssignments() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);
        UUID player = UUID.randomUUID();

        instance.activate(player);
        assertEquals(1, instance.assignedCount());
        assertEquals(1, instance.activeIds().size());
        assertTrue(instance.isActive(player));

        instance.deactivate(player);
        assertEquals(1, instance.assignedCount());
        assertTrue(instance.activeIds().isEmpty());
        assertFalse(instance.isActive(player));
    }

    @Test
    void lifecycleFlagsStartFresh() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);

        assertTrue(instance.active());
        assertFalse(instance.begun());
        assertFalse(instance.ending());
        assertFalse(instance.endPhaseDone());
        assertFalse(instance.endStatsShown());
    }

    @Test
    void elapsedSecondsCountsFromStart() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);

        assertEquals(0L, instance.elapsedSeconds(1_000L));
        assertEquals(61L, instance.elapsedSeconds(62_500L));
        assertEquals(0L, instance.elapsedSeconds(500L));
    }

    @Test
    void elapsedMillisCountsFromStartWhileLive() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);

        assertEquals(0L, instance.elapsedMillis(1_000L));
        assertEquals(61_500L, instance.elapsedMillis(62_500L));
        assertEquals(0L, instance.elapsedMillis(500L));
    }

    @Test
    void elapsedFreezesOnceEnding() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);
        instance.refreshElapsedCache(62_500L);
        instance.setEnding(true);

        assertEquals(61_500L, instance.elapsedMillis(200_000L));
        assertEquals(61L, instance.elapsedSeconds(200_000L));
    }

    @Test
    void refreshAfterEndingIsIgnored() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);
        instance.refreshElapsedCache(5_000L);
        instance.setEnding(true);
        instance.refreshElapsedCache(200_000L);

        assertEquals(4_000L, instance.elapsedMillis(300_000L));
        assertEquals(4L, instance.elapsedSeconds(300_000L));
    }

    @Test
    void lobbyTagShowsSublobbyWhenMinted() {
        GameInstance direct = new GameInstance(1L, 2, OptionalLong.empty(), 1_000L);
        GameInstance sublobbed = new GameInstance(2L, 2, OptionalLong.empty(), 1_000L);
        sublobbed.setSubLobby(new SubLobby(2, 0));

        assertNull(direct.subLobby());
        assertEquals("L2", direct.lobbyTag());
        assertEquals("L2-0", sublobbed.lobbyTag());
    }

    @Test
    void recordDeathKeepsOrderAndIgnoresRepeats() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        instance.recordDeath(first, "amy", Role.HUNTER);
        instance.recordDeath(second, "bob", Role.SPEEDRUNNER);
        instance.recordDeath(first, "amy", Role.HUNTER);

        assertEquals(2, instance.deadPlayers().size());
        assertEquals("amy", instance.deadPlayers().get(0).name());
        assertEquals("bob", instance.deadPlayers().get(1).name());
        assertEquals(Role.SPEEDRUNNER, instance.deadPlayers().get(1).formerRole());
    }

    @Test
    void modifierToggleMarksFireOncePerName() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);

        assertTrue(instance.markModifierStarted("m"));
        assertFalse(instance.markModifierStarted("m"));
        assertTrue(instance.markModifierStarted("other"));
        assertTrue(instance.markModifierCleaned("m"));
        assertFalse(instance.markModifierCleaned("m"));
        assertTrue(instance.markModifierCleaned("other"));
    }

    @Test
    void startMarksFireOncePerPlayer() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(instance.markStartFired(first));
        assertFalse(instance.markStartFired(first));
        assertTrue(instance.markStartFired(second));
    }

    @Test
    void lifeIndexCountsDeathsFromZero() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);
        UUID player = UUID.randomUUID();

        assertEquals(0, instance.lifeOf(player));
        instance.noteDeath(player);
        assertEquals(1, instance.lifeOf(player));
        instance.noteDeath(player);
        assertEquals(2, instance.lifeOf(player));
    }

    @Test
    void respawnMarksFireOncePerLife() {
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);
        UUID player = UUID.randomUUID();

        assertTrue(instance.markRespawnFired(player, 0));
        assertFalse(instance.markRespawnFired(player, 0));
        assertTrue(instance.markRespawnFired(player, 1));
        assertFalse(instance.markRespawnFired(player, 1));
    }
}
