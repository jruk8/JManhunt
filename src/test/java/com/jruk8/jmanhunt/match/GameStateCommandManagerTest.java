package com.jruk8.jmanhunt.match;

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
                ModifierTriggers.normalizeTrigger("ON_FIRST_ENTER_NETHER"));
        assertEquals("ON_FIRST_ENTER_END",
                ModifierTriggers.normalizeTrigger("ON_FIRST_ENTER_END"));
    }

    @Test
    void canonicalAndUnknownKeysPassThrough() {
        assertEquals("ON_NETHER_ENTER",
                ModifierTriggers.normalizeTrigger("ON_NETHER_ENTER"));
        assertEquals("ON_FIRST_NETHER_ENTER",
                ModifierTriggers.normalizeTrigger("ON_FIRST_NETHER_ENTER"));
        assertEquals("ON_FIRST_END_ENTER",
                ModifierTriggers.normalizeTrigger("ON_FIRST_END_ENTER"));
        assertEquals("ON_EVERY_KILL",
                ModifierTriggers.normalizeTrigger("ON_EVERY_KILL"));
    }

    @Test
    void matchingIgnoresCaseAndSurroundingWhitespace() {
        assertEquals("on_first_enter_nether",
                ModifierTriggers.normalizeTrigger("  on_first_enter_nether "));
        assertEquals("on_start",
                ModifierTriggers.normalizeTrigger("  on_start "));
    }

    @Test
    void nullStaysNull() {
        assertNull(ModifierTriggers.normalizeTrigger(null));
    }

    @Test
    void chanceClampsToUnitRange() {
        assertEquals(1.0, ModifierTriggers.clampChance(1.0));
        assertEquals(0.0, ModifierTriggers.clampChance(0.0));
        assertEquals(1.0, ModifierTriggers.clampChance(1.5));
        assertEquals(0.0, ModifierTriggers.clampChance(-0.25));
        assertEquals(1.0, ModifierTriggers.clampChance(Double.NaN));
    }

    @Test
    void gameruleRestoresOnLastMatchEnd() {
        assertFalse(GameStateCommandManager.gameruleRestored("start", true, true));
        assertFalse(GameStateCommandManager.gameruleRestored("start", false, true));
        assertFalse(GameStateCommandManager.gameruleRestored("end", false, true));
        assertTrue(GameStateCommandManager.gameruleRestored("end", true, true));
        assertTrue(GameStateCommandManager.gameruleRestored("start", true, false));
        assertTrue(GameStateCommandManager.gameruleRestored("end", false, false));
    }

    @Test
    void chanceRollUsesStrictLessThan() {
        assertTrue(ModifierTriggers.rollChance(1.0, 0.999));
        assertFalse(ModifierTriggers.rollChance(0.0, 0.0));
        assertTrue(ModifierTriggers.rollChance(0.5, 0.499));
        assertFalse(ModifierTriggers.rollChance(0.5, 0.5));
    }

    @Test
    void scopeDefaultsToPerInvoke() {
        assertEquals(ModifierTriggers.TriggerScope.PER_INVOKE,
                ModifierTriggers.parseScope(null));
        assertEquals(ModifierTriggers.TriggerScope.PER_INVOKE,
                ModifierTriggers.parseScope("PER_INVOKE"));
        assertEquals(ModifierTriggers.TriggerScope.PER_EXECUTOR,
                ModifierTriggers.parseScope("  per_executor "));
        assertEquals(ModifierTriggers.TriggerScope.PER_INVOKE,
                ModifierTriggers.parseScope("banana"));
    }

    @Test
    void selectionDefaultsToInOrder() {
        assertEquals(ModifierTriggers.Selection.IN_ORDER,
                ModifierTriggers.parseSelection(null));
        assertEquals(ModifierTriggers.Selection.PICK_RANDOM,
                ModifierTriggers.parseSelection("pick_random"));
        assertEquals(ModifierTriggers.Selection.IN_ORDER,
                ModifierTriggers.parseSelection("IN_ORDER"));
    }

    @Test
    void picksDrawDistinctLinesUpToCount() {
        List<String> commands = List.of("a", "b", "c", "d");
        List<String> picked = ModifierTriggers.pickCommands(commands, 2, new Random(7));
        assertEquals(2, picked.size());
        assertEquals(2, picked.stream().distinct().count());
        assertTrue(commands.containsAll(picked));
        assertEquals(4, ModifierTriggers.pickCommands(commands, 99, new Random(7)).size());
        assertTrue(ModifierTriggers.pickCommands(commands, 0, new Random(7)).isEmpty());
        assertTrue(ModifierTriggers.pickCommands(List.of(), 1, new Random(7)).isEmpty());
    }

    @Test
    void deviationClampsToIntervalBounds() {
        assertEquals(0.0, ModifierTriggers.clampDeviation(0.0, 60.0));
        assertEquals(0.0, ModifierTriggers.clampDeviation(-5.0, 60.0));
        assertEquals(15.0, ModifierTriggers.clampDeviation(15.0, 60.0));
        assertEquals(60.0, ModifierTriggers.clampDeviation(90.0, 60.0));
    }

    @Test
    void jitteredIntervalStaysWithinBoundsAndTicks() {
        assertEquals(1200L, ModifierTriggers.jitteredIntervalTicks(60.0, 0.0, 0.5));
        assertEquals(900L, ModifierTriggers.jitteredIntervalTicks(60.0, 15.0, 0.0));
        assertEquals(1500L, ModifierTriggers.jitteredIntervalTicks(60.0, 15.0, 0.999999));
        // deviation equal to the interval can roll down to zero seconds: still one tick, never zero.
        assertEquals(1L, ModifierTriggers.jitteredIntervalTicks(60.0, 60.0, 0.0));
        assertEquals(1L, ModifierTriggers.secondsToTicks(0.0));
        assertEquals(1L, ModifierTriggers.secondsToTicks(-3.0));
    }

    @Test
    void preStartOrderDefaultsToBefore() {
        assertFalse(ModifierTriggers.runsAfterPrestart(null));
        assertFalse(ModifierTriggers.runsAfterPrestart("BEFORE"));
        assertFalse(ModifierTriggers.runsAfterPrestart("banana"));
        assertTrue(ModifierTriggers.runsAfterPrestart("AFTER"));
        assertTrue(ModifierTriggers.runsAfterPrestart("  after  "));
    }

    @Test
    void staleDispatchVoidsRestartedOrTornDownEngines() {
        assertFalse(ModifierTriggers.isStaleDispatch(3L, 3L, true));
        assertTrue(ModifierTriggers.isStaleDispatch(3L, 4L, true));
        assertTrue(ModifierTriggers.isStaleDispatch(3L, 3L, false));
        assertTrue(ModifierTriggers.isStaleDispatch(3L, 4L, false));
    }
}
