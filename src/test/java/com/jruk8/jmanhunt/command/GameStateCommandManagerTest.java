package com.jruk8.jmanhunt.command;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameStateCommandManagerTest {

    @Test
    void removedLegacyKeysPassThroughUnmatched() {
        assertEquals("ON_FIRST_ENTER_NETHER",
                GameStateCommandManager.normalizeTrigger("ON_FIRST_ENTER_NETHER"));
        assertEquals("ON_FIRST_ENTER_END",
                GameStateCommandManager.normalizeTrigger("ON_FIRST_ENTER_END"));
    }

    @Test
    void canonicalAndUnknownKeysPassThrough() {
        assertEquals("ON_NETHER_ENTER",
                GameStateCommandManager.normalizeTrigger("ON_NETHER_ENTER"));
        assertEquals("ON_FIRST_NETHER_ENTER",
                GameStateCommandManager.normalizeTrigger("ON_FIRST_NETHER_ENTER"));
        assertEquals("ON_FIRST_END_ENTER",
                GameStateCommandManager.normalizeTrigger("ON_FIRST_END_ENTER"));
        assertEquals("ON_EVERY_KILL",
                GameStateCommandManager.normalizeTrigger("ON_EVERY_KILL"));
    }

    @Test
    void matchingIgnoresCaseAndSurroundingWhitespace() {
        assertEquals("on_first_enter_nether",
                GameStateCommandManager.normalizeTrigger("  on_first_enter_nether "));
        assertEquals("on_start",
                GameStateCommandManager.normalizeTrigger("  on_start "));
    }

    @Test
    void nullStaysNull() {
        assertNull(GameStateCommandManager.normalizeTrigger(null));
    }

    @Test
    void chanceClampsToUnitRange() {
        assertEquals(1.0, GameStateCommandManager.clampChance(1.0));
        assertEquals(0.0, GameStateCommandManager.clampChance(0.0));
        assertEquals(1.0, GameStateCommandManager.clampChance(1.5));
        assertEquals(0.0, GameStateCommandManager.clampChance(-0.25));
        assertEquals(1.0, GameStateCommandManager.clampChance(Double.NaN));
    }

    @Test
    void chanceRollUsesStrictLessThan() {
        assertTrue(GameStateCommandManager.rollChance(1.0, 0.999));
        assertFalse(GameStateCommandManager.rollChance(0.0, 0.0));
        assertTrue(GameStateCommandManager.rollChance(0.5, 0.499));
        assertFalse(GameStateCommandManager.rollChance(0.5, 0.5));
    }

    @Test
    void scopeDefaultsToPerInvoke() {
        assertEquals(GameStateCommandManager.TriggerScope.PER_INVOKE,
                GameStateCommandManager.parseScope(null));
        assertEquals(GameStateCommandManager.TriggerScope.PER_INVOKE,
                GameStateCommandManager.parseScope("PER_INVOKE"));
        assertEquals(GameStateCommandManager.TriggerScope.PER_EXECUTOR,
                GameStateCommandManager.parseScope("  per_executor "));
        assertEquals(GameStateCommandManager.TriggerScope.PER_INVOKE,
                GameStateCommandManager.parseScope("banana"));
    }

    @Test
    void selectionDefaultsToInOrder() {
        assertEquals(GameStateCommandManager.Selection.IN_ORDER,
                GameStateCommandManager.parseSelection(null));
        assertEquals(GameStateCommandManager.Selection.PICK_RANDOM,
                GameStateCommandManager.parseSelection("pick_random"));
        assertEquals(GameStateCommandManager.Selection.IN_ORDER,
                GameStateCommandManager.parseSelection("IN_ORDER"));
    }

    @Test
    void picksDrawDistinctLinesUpToCount() {
        List<String> commands = List.of("a", "b", "c", "d");
        List<String> picked = GameStateCommandManager.pickCommands(commands, 2, new Random(7));
        assertEquals(2, picked.size());
        assertEquals(2, picked.stream().distinct().count());
        assertTrue(commands.containsAll(picked));
        assertEquals(4, GameStateCommandManager.pickCommands(commands, 99, new Random(7)).size());
        assertTrue(GameStateCommandManager.pickCommands(commands, 0, new Random(7)).isEmpty());
        assertTrue(GameStateCommandManager.pickCommands(List.of(), 1, new Random(7)).isEmpty());
    }

    @Test
    void deviationClampsToIntervalBounds() {
        assertEquals(0.0, GameStateCommandManager.clampDeviation(0.0, 60.0));
        assertEquals(0.0, GameStateCommandManager.clampDeviation(-5.0, 60.0));
        assertEquals(15.0, GameStateCommandManager.clampDeviation(15.0, 60.0));
        assertEquals(60.0, GameStateCommandManager.clampDeviation(90.0, 60.0));
    }

    @Test
    void jitteredIntervalStaysWithinBoundsAndTicks() {
        assertEquals(1200L, GameStateCommandManager.jitteredIntervalTicks(60.0, 0.0, 0.5));
        assertEquals(900L, GameStateCommandManager.jitteredIntervalTicks(60.0, 15.0, 0.0));
        assertEquals(1500L, GameStateCommandManager.jitteredIntervalTicks(60.0, 15.0, 0.999999));
        // deviation equal to the interval can roll down to zero seconds: still one tick, never zero.
        assertEquals(1L, GameStateCommandManager.jitteredIntervalTicks(60.0, 60.0, 0.0));
        assertEquals(1L, GameStateCommandManager.secondsToTicks(0.0));
        assertEquals(1L, GameStateCommandManager.secondsToTicks(-3.0));
    }

    @Test
    void preStartOrderDefaultsToBefore() {
        assertFalse(GameStateCommandManager.runsAfterPrestart(null));
        assertFalse(GameStateCommandManager.runsAfterPrestart("BEFORE"));
        assertFalse(GameStateCommandManager.runsAfterPrestart("banana"));
        assertTrue(GameStateCommandManager.runsAfterPrestart("AFTER"));
        assertTrue(GameStateCommandManager.runsAfterPrestart("  after  "));
    }

    @Test
    void staleDispatchVoidsRestartedOrTornDownEngines() {
        assertFalse(GameStateCommandManager.isStaleDispatch(3L, 3L, true));
        assertTrue(GameStateCommandManager.isStaleDispatch(3L, 4L, true));
        assertTrue(GameStateCommandManager.isStaleDispatch(3L, 3L, false));
        assertTrue(GameStateCommandManager.isStaleDispatch(3L, 4L, false));
    }
}
