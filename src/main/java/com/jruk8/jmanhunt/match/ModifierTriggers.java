package com.jruk8.jmanhunt.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Pure trigger evaluation for modifiers: trigger names, scopes, chances,
 * delays, and line picks. No Bukkit types, so unit tests cover it directly.
 */
final class ModifierTriggers {
    /** Per-activation versus per-executor random behavior. */
    enum TriggerScope {
        PER_INVOKE, PER_EXECUTOR
    }

    /** Command selection mode for {@code options.execution.selection}. */
    enum Selection {
        IN_ORDER, PICK_RANDOM
    }

    private ModifierTriggers() {
    }

    /**
     * True when a delayed dispatch must not fire: its engine restarted
     * (new generation) or was torn down. Pure for tests.
     */
    static boolean isStaleDispatch(long capturedGeneration, long currentGeneration, boolean engineAlive) {
        return capturedGeneration != currentGeneration || !engineAlive;
    }

    /**
     * Canonicalizes a {@code runs-on} trigger name. Currently this only trims
     * surrounding whitespace; unknown keys (including the removed legacy
     * {@code ON_FIRST_ENTER_NETHER} / {@code ON_FIRST_ENTER_END} aliases)
     * pass through unchanged and therefore never match an event.
     */
    static String normalizeTrigger(String trigger) {
        if (trigger == null) {
            return null;
        }
        return trigger.trim();
    }

    /** Parses a {@code PER_INVOKE} / {@code PER_EXECUTOR} behavior key. */
    static TriggerScope parseScope(String raw) {
        if (raw != null && raw.trim().equalsIgnoreCase("PER_EXECUTOR")) {
            return TriggerScope.PER_EXECUTOR;
        }
        return TriggerScope.PER_INVOKE;
    }

    /** Parses a {@code options.execution.selection} key. */
    static Selection parseSelection(String raw) {
        if (raw != null && raw.trim().equalsIgnoreCase("PICK_RANDOM")) {
            return Selection.PICK_RANDOM;
        }
        return Selection.IN_ORDER;
    }

    /**
     * Parses an {@code on-start.pre-start-order} key. Only AFTER defers
     * the ON_START sequence past the pre-start hit; anything else runs at
     * match start.
     */
    static boolean runsAfterPrestart(String raw) {
        return raw != null && raw.trim().equalsIgnoreCase("AFTER");
    }

    /**
     * Clamps a {@code options.success-chance.chance} value to the 0.0-1.0 decimal
     * fraction range. Unset or unreadable values fall back to 1.0 upstream.
     */
    static double clampChance(double chance) {
        if (Double.isNaN(chance)) {
            return 1.0;
        }
        return Math.min(1.0, Math.max(0.0, chance));
    }

    /** Rolls a clamped chance against a {@code [0.0, 1.0)} draw. */
    static boolean rollChance(double clampedChance, double roll) {
        return roll < clampedChance;
    }

    /** Clamps {@code options.interval-settings.deviation} to {@code [0, interval]}. */
    static double clampDeviation(double deviation, double intervalSeconds) {
        if (Double.isNaN(deviation) || deviation <= 0.0) {
            return 0.0;
        }
        if (Double.isNaN(intervalSeconds) || intervalSeconds <= 0.0) {
            return 0.0;
        }
        return Math.min(deviation, intervalSeconds);
    }

    /**
     * Converts seconds to ticks, rounding to the nearest tick. Anything at or
     * below zero ticks becomes a single tick so scheduling never stalls.
     */
    static long secondsToTicks(double seconds) {
        return Math.max(1L, (long) Math.round(seconds * 20.0));
    }

    /**
     * Rolls the next interval delay within {@code [interval - deviation,
     * interval + deviation]} seconds for a {@code [0.0, 1.0)} draw.
     */
    static long jitteredIntervalTicks(double intervalSeconds, double deviationSeconds, double roll) {
        double deviation = clampDeviation(deviationSeconds, intervalSeconds);
        if (deviation <= 0.0) {
            return secondsToTicks(intervalSeconds);
        }
        return secondsToTicks(intervalSeconds - deviation + roll * deviation * 2.0);
    }

    /** Draws up to {@code count} distinct lines from a command list. */
    static List<String> pickCommands(List<String> commands, int count, Random random) {
        if (commands == null || commands.isEmpty() || count <= 0) {
            return List.of();
        }
        List<String> pool = new ArrayList<>(commands);
        Collections.shuffle(pool, random);
        return List.copyOf(pool.subList(0, Math.min(count, pool.size())));
    }
}
