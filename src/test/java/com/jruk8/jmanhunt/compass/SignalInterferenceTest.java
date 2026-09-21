package com.jruk8.jmanhunt.compass;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verdict matrix for the pure signal-interference core: each option,
 * required-to-fail combining, two-way sides, bypass boundaries, and
 * config clamping. No Bukkit server needed.
 */
class SignalInterferenceTest {

    private static SignalInterference.Snapshot spot(int sky, int block, boolean normal, int above,
            int y, SignalInterference.Weather weather, String biome) {
        return new SignalInterference.Snapshot(sky, block, normal, above, y, weather, biome);
    }

    private static SignalInterference.Snapshot clearSpot() {
        return spot(15, 15, true, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");
    }

    private static SignalInterference.Config config(boolean light, boolean underground, boolean altitude,
            boolean weather, boolean biome) {
        return new SignalInterference.Config(light, 10, 5,
                SignalInterference.InterfereWhen.ONE_UNMET,
                underground, 3, altitude, -20, 120, weather,
                Set.of(SignalInterference.Weather.STORM, SignalInterference.Weather.RAIN),
                biome, Set.of("minecraft:desert", "minecraft:the_end"), 1, false, 0.0);
    }

    private static SignalInterference.Config withCounts(
            SignalInterference.Config base, int required, boolean twoWay, double bypass) {
        return new SignalInterference.Config(base.lightEnabled(), base.minSkyLight(),
                base.minBlockLight(), base.interfereWhen(), base.undergroundEnabled(),
                base.maxBlocksAbove(), base.altitudeEnabled(), base.minY(), base.maxY(),
                base.weatherEnabled(), base.interfereDuring(), base.biomeEnabled(),
                base.interfereIn(), required, twoWay, bypass);
    }

    @Test
    void allDisabledNeverFails() {
        SignalInterference.Config off = config(false, false, false, false, false);
        SignalInterference.Snapshot worst =
                spot(0, 0, true, 380, -64, SignalInterference.Weather.STORM, "minecraft:desert");

        assertFalse(SignalInterference.badSignal(worst, worst, withCounts(off, 1, true, 0.0), 0.99));
    }

    @Test
    void lightOneUnmetFailsOnEitherReading() {
        SignalInterference.Config light = config(true, false, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(10, 5, true, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, light, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(9, 5, true, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, light, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(10, 4, true, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, light, 0.0));
    }

    @Test
    void lightBothUnmetNeedsBothReadings() {
        SignalInterference.Config both = new SignalInterference.Config(true, 10, 5,
                SignalInterference.InterfereWhen.BOTH_UNMET, false, 3, false, -20, 120, false,
                Set.of(), false, Set.of(), 1, false, 0.0);

        assertFalse(SignalInterference.badSignal(
                spot(9, 5, true, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, both, 0.0));
        assertFalse(SignalInterference.badSignal(
                spot(10, 4, true, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, both, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(9, 4, true, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, both, 0.0));
    }

    @Test
    void lightSkippedOutsideOverworld() {
        SignalInterference.Config light = config(true, false, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(0, 0, false, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, light, 0.0));
    }

    @Test
    void undergroundFailsPastMaximum() {
        SignalInterference.Config underground = config(false, true, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(15, 15, true, 3, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, underground, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 4, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, underground, 0.0));
    }

    @Test
    void altitudeFailsOutsideInclusiveRange() {
        SignalInterference.Config altitude = config(false, false, true, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(15, 15, true, 0, -20, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, altitude, 0.0));
        assertFalse(SignalInterference.badSignal(
                spot(15, 15, true, 0, 120, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, altitude, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, -21, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, altitude, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 121, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, altitude, 0.0));
    }

    @Test
    void weatherFailsOnlyDuringListedBuckets() {
        SignalInterference.Config weather = config(false, false, false, true, false);

        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 64, SignalInterference.Weather.STORM, "minecraft:plains"),
                null, weather, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 64, SignalInterference.Weather.RAIN, "minecraft:plains"),
                null, weather, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, weather, 0.0));
    }

    @Test
    void biomeMatchesKeysCaseInsensitively() {
        SignalInterference.Config biome = config(false, false, false, false, true);

        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:desert"),
                null, biome, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 64, SignalInterference.Weather.CLEAR, "MINECRAFT:THE_END"),
                null, biome, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, biome, 0.0));
    }

    @Test
    void requiredToFailNeedsEnoughOptions() {
        SignalInterference.Config two = withCounts(config(true, true, false, false, false), 2, false, 0.0);
        SignalInterference.Snapshot dark =
                spot(0, 15, true, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");
        SignalInterference.Snapshot darkAndCovered =
                spot(0, 15, true, 9, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");

        assertFalse(SignalInterference.badSignal(dark, null, two, 0.0));
        assertTrue(SignalInterference.badSignal(darkAndCovered, null, two, 0.0));
    }

    @Test
    void requiredToFailClampsToEnabledCount() {
        SignalInterference.Config clamped =
                withCounts(config(true, false, false, false, false), 5, false, 0.0);
        SignalInterference.Snapshot dark =
                spot(0, 15, true, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");

        assertTrue(SignalInterference.badSignal(dark, null, clamped, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, clamped, 0.0));
    }

    @Test
    void twoWayFailsFromEitherSide() {
        SignalInterference.Config oneWay = config(true, false, false, false, false);
        SignalInterference.Config twoWay = withCounts(oneWay, 1, true, 0.0);
        SignalInterference.Snapshot dark =
                spot(0, 15, true, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");

        assertFalse(SignalInterference.badSignal(clearSpot(), dark, oneWay, 0.0));
        assertTrue(SignalInterference.badSignal(clearSpot(), dark, twoWay, 0.0));
        assertTrue(SignalInterference.badSignal(dark, clearSpot(), twoWay, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, twoWay, 0.0));
    }

    @Test
    void bypassRollSavesBadSignalsUnderChance() {
        SignalInterference.Config none = config(true, false, false, false, false);
        SignalInterference.Config half = withCounts(none, 1, false, 0.5);
        SignalInterference.Config always = withCounts(none, 1, false, 1.0);
        SignalInterference.Snapshot dark =
                spot(0, 15, true, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");

        assertTrue(SignalInterference.badSignal(dark, null, none, 0.0));
        assertFalse(SignalInterference.badSignal(dark, null, half, 0.49));
        assertTrue(SignalInterference.badSignal(dark, null, half, 0.5));
        assertFalse(SignalInterference.badSignal(dark, null, always, 0.999));
    }

    @Test
    void configClampsEveryRange() {
        Set<String> biomes = new HashSet<>();
        biomes.add("MINECRAFT:DESERT");
        biomes.add(null);
        SignalInterference.Config clamped = new SignalInterference.Config(true, 99, -9, null,
                true, 999, true, 200, -100, true, null, true,
                biomes, -3, false, 2.5);

        assertEquals(15, clamped.minSkyLight());
        assertEquals(0, clamped.minBlockLight());
        assertEquals(SignalInterference.InterfereWhen.ONE_UNMET, clamped.interfereWhen());
        assertEquals(380, clamped.maxBlocksAbove());
        assertEquals(-64, clamped.minY());
        assertEquals(200, clamped.maxY());
        assertTrue(clamped.interfereDuring().isEmpty());
        assertEquals(Set.of("minecraft:desert"), clamped.interfereIn());
        assertEquals(1.0, clamped.chanceToBypass());
    }
}
