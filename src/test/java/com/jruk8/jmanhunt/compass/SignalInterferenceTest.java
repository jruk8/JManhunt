package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.compass.SignalInterference.CheckOn;
import com.jruk8.jmanhunt.compass.SignalInterference.Config;
import com.jruk8.jmanhunt.compass.SignalInterference.InterfereWhen;
import com.jruk8.jmanhunt.compass.SignalInterference.InterfereWhenVisible;
import com.jruk8.jmanhunt.compass.SignalInterference.Reason;
import com.jruk8.jmanhunt.compass.SignalInterference.Snapshot;
import com.jruk8.jmanhunt.compass.SignalInterference.StatThresholds;
import com.jruk8.jmanhunt.compass.SignalInterference.Weather;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verdict matrix for the pure signal interference core: each option,
 * required-to-fail combining, per-option check-on sides, bypass
 * boundaries, reason ids, and config clamping. No Bukkit server needed.
 */
class SignalInterferenceTest {

    private static Snapshot spot(int sky, int block, boolean normal, int above,
            int fluids, int y, Weather weather, String biome) {
        return spot(sky, block, normal, above, fluids, y, weather, biome, 0.0, false);
    }

    private static Snapshot spot(int sky, int block, boolean normal, int above,
            int fluids, int y, Weather weather, String biome,
            double moved, boolean feetInWater) {
        return spot(sky, block, normal, above, fluids, y, weather, biome, moved, feetInWater,
                false);
    }

    private static Snapshot spot(int sky, int block, boolean normal, int above,
            int fluids, int y, Weather weather, String biome,
            double moved, boolean feetInWater, boolean invisible) {
        return spot(sky, block, normal, above, fluids, y, weather, biome, moved, feetInWater,
                invisible, 20.0, 20, 30);
    }

    private static Snapshot spot(int sky, int block, boolean normal, int above,
            int fluids, int y, Weather weather, String biome,
            double moved, boolean feetInWater, boolean invisible, double health, int hunger,
            int expLevel) {
        return new Snapshot(sky, block, normal, above, fluids, y, weather,
                biome, moved, feetInWater, invisible, health, hunger, expLevel);
    }

    private static Snapshot clearSpot() {
        return spot(15, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains");
    }

    private static Snapshot invisibleSpot() {
        return spot(15, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains",
                0.0, false, true);
    }

    private static Config config(boolean light, boolean underground,
            boolean underwater, boolean altitude, boolean weather, boolean biome,
            boolean movement, boolean los) {
        return configFull(light, underground, underwater, altitude, weather, biome, movement,
                los, CheckOn.SELF, CheckOn.BOTH, CheckOn.BOTH, CheckOn.BOTH, CheckOn.BOTH,
                CheckOn.SELF, 1, 0.0, InterfereWhen.ONE_UNMET, InterfereWhenVisible.VISIBLE);
    }

    private static Config configFull(boolean light, boolean underground,
            boolean underwater, boolean altitude, boolean weather, boolean biome,
            boolean movement, boolean los, CheckOn lightOn, CheckOn undergroundOn,
            CheckOn underwaterOn, CheckOn altitudeOn, CheckOn biomeOn, CheckOn movementOn,
            int required, double bypass, InterfereWhen when, InterfereWhenVisible losWhen) {
        return new Config(light, 10, 5, when, lightOn,
                underground, 3, undergroundOn, underwater, 2, underwaterOn,
                altitude, -20, 120, altitudeOn, weather,
                Set.of(Weather.STORM, Weather.RAIN),
                biome, Set.of("minecraft:desert", "minecraft:the_end"), biomeOn,
                movement, 0.2, movementOn, los, losWhen, 300, false,
                CheckOn.BOTH, false, 8, CheckOn.SELF,
                false, 10, CheckOn.SELF, false, 5, CheckOn.SELF, required, bypass);
    }

    private static Config invisibleConfig(CheckOn checkOn) {
        return new Config(false, 10, 5, InterfereWhen.ONE_UNMET, CheckOn.SELF,
                false, 3, CheckOn.BOTH, false, 2, CheckOn.BOTH, false, -20, 120,
                CheckOn.BOTH, false, Set.of(), false, Set.of(), CheckOn.BOTH, false, 0.2,
                CheckOn.SELF, false, InterfereWhenVisible.VISIBLE, 300,
                true, checkOn, false, 8, CheckOn.SELF, false, 10, CheckOn.SELF, false, 5,
                CheckOn.SELF, 1, 0.0);
    }

    private static Config statsConfig(boolean health, boolean hunger,
            boolean experience, CheckOn healthOn, CheckOn hungerOn, CheckOn expOn) {
        return new Config(false, 10, 5, InterfereWhen.ONE_UNMET, CheckOn.SELF,
                false, 3, CheckOn.BOTH, false, 2, CheckOn.BOTH, false, -20, 120,
                CheckOn.BOTH, false, Set.of(), false, Set.of(), CheckOn.BOTH, false, 0.2,
                CheckOn.SELF, false, InterfereWhenVisible.VISIBLE, 300,
                false, CheckOn.BOTH, health, 8, healthOn, hunger, 10, hungerOn,
                experience, 5, expOn, 1, 0.0);
    }

    private static Config withCounts(Config base, int required, double bypass) {
        return new Config(base.lightEnabled(), base.minSkyLight(),
                base.minBlockLight(), base.interfereWhen(), base.lightCheckOn(),
                base.undergroundEnabled(), base.maxBlocksAbove(), base.undergroundCheckOn(),
                base.underwaterEnabled(), base.maxFluidAbove(), base.underwaterCheckOn(),
                base.altitudeEnabled(), base.minY(), base.maxY(), base.altitudeCheckOn(),
                base.weatherEnabled(), base.interfereDuring(),
                base.biomeEnabled(), base.interfereIn(), base.biomeCheckOn(),
                base.movementEnabled(), base.thresholdBlocks(), base.movementCheckOn(),
                base.losEnabled(), base.losWhen(), base.losMaxDistance(),
                base.invisibleEnabled(), base.invisibleCheckOn(),
                base.healthEnabled(), base.minHealth(), base.healthCheckOn(),
                base.hungerEnabled(), base.minHunger(), base.hungerCheckOn(),
                base.expEnabled(), base.minExpLevel(), base.expCheckOn(), required, bypass);
    }

    private static Config withModes(Config base, InterfereWhen when,
            InterfereWhenVisible losWhen) {
        return new Config(base.lightEnabled(), base.minSkyLight(),
                base.minBlockLight(), when, base.lightCheckOn(),
                base.undergroundEnabled(), base.maxBlocksAbove(), base.undergroundCheckOn(),
                base.underwaterEnabled(), base.maxFluidAbove(), base.underwaterCheckOn(),
                base.altitudeEnabled(), base.minY(), base.maxY(), base.altitudeCheckOn(),
                base.weatherEnabled(), base.interfereDuring(),
                base.biomeEnabled(), base.interfereIn(), base.biomeCheckOn(),
                base.movementEnabled(), base.thresholdBlocks(), base.movementCheckOn(),
                base.losEnabled(), losWhen, base.losMaxDistance(), base.invisibleEnabled(),
                base.invisibleCheckOn(), base.healthEnabled(),
                base.minHealth(), base.healthCheckOn(), base.hungerEnabled(), base.minHunger(),
                base.hungerCheckOn(), base.expEnabled(), base.minExpLevel(), base.expCheckOn(),
                base.requiredToFail(), base.chanceToBypass());
    }

    @Test
    void allDisabledNeverFails() {
        Config off = configFull(false, false, false, false, false, false,
                false, false, CheckOn.BOTH, CheckOn.BOTH, CheckOn.BOTH, CheckOn.BOTH,
                CheckOn.BOTH, CheckOn.BOTH, 1, 0.0, InterfereWhen.ONE_UNMET,
                InterfereWhenVisible.VISIBLE);
        Snapshot worst =
                spot(0, 0, true, 380, 380, -64, Weather.STORM, "minecraft:desert",
                        999.0, true);

        assertFalse(SignalInterference.badSignal(worst, worst, off, 0.99));
    }

    @Test
    void lightOneUnmetFailsOnEitherReading() {
        Config light =
                config(true, false, false, false, false, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(10, 5, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains"),
                null, light, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(9, 5, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains"),
                null, light, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(10, 4, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains"),
                null, light, 0.0));
    }

    @Test
    void lightBothUnmetNeedsBothReadings() {
        Config both = withModes(
                config(true, false, false, false, false, false, false, false),
                InterfereWhen.BOTH_UNMET, InterfereWhenVisible.VISIBLE);

        assertFalse(SignalInterference.badSignal(
                spot(9, 5, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains"),
                null, both, 0.0));
        assertFalse(SignalInterference.badSignal(
                spot(10, 4, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains"),
                null, both, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(9, 4, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains"),
                null, both, 0.0));
    }

    @Test
    void lightSkippedOutsideOverworld() {
        Config light =
                config(true, false, false, false, false, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(0, 0, false, 0, 0, 64, Weather.CLEAR, "minecraft:plains"),
                null, light, 0.0));
    }

    @Test
    void undergroundFailsPastMaximum() {
        Config underground =
                config(false, true, false, false, false, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(15, 15, true, 3, 0, 64, Weather.CLEAR, "minecraft:plains"),
                null, underground, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 4, 0, 64, Weather.CLEAR, "minecraft:plains"),
                null, underground, 0.0));
    }

    @Test
    void underwaterFailsPastMaximum() {
        Config underwater =
                config(false, false, true, false, false, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(15, 15, true, 0, 2, 64, Weather.CLEAR, "minecraft:plains",
                        0.0, true),
                null, underwater, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 3, 64, Weather.CLEAR, "minecraft:plains",
                        0.0, true),
                null, underwater, 0.0));
    }

    @Test
    void underwaterNeedsWaterFeet() {
        Config underwater =
                config(false, false, true, false, false, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(15, 15, true, 0, 380, 64, Weather.CLEAR, "minecraft:plains",
                        0.0, false),
                null, underwater, 0.0));
    }

    @Test
    void movementFailsPastThreshold() {
        Config movement =
                config(false, false, false, false, false, false, true, false);
        Snapshot still =
                spot(15, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains",
                        0.2, false);
        Snapshot stepped =
                spot(15, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains",
                        0.21, false);

        assertFalse(SignalInterference.badSignal(still, null, movement, 0.0));
        assertTrue(SignalInterference.badSignal(stepped, null, movement, 0.0));
        assertEquals(Optional.of(new Reason("moved", false)),
                SignalInterference.lastReason(stepped, null, movement, 0.0, null));
    }

    @Test
    void movementCheckOnGatesSides() {
        Config self =
                config(false, false, false, false, false, false, true, false);
        Config both = configFull(false, false, false, false, false, false,
                true, false, CheckOn.SELF, CheckOn.BOTH, CheckOn.BOTH, CheckOn.BOTH,
                CheckOn.BOTH, CheckOn.BOTH, 1, 0.0, InterfereWhen.ONE_UNMET,
                InterfereWhenVisible.VISIBLE);
        Config target = configFull(false, false, false, false, false, false,
                true, false, CheckOn.SELF, CheckOn.BOTH, CheckOn.BOTH, CheckOn.BOTH,
                CheckOn.BOTH, CheckOn.TARGET, 1, 0.0, InterfereWhen.ONE_UNMET,
                InterfereWhenVisible.VISIBLE);
        Snapshot moved =
                spot(15, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains",
                        5.0, false);

        assertFalse(SignalInterference.badSignal(clearSpot(), moved, self, 0.0));
        assertTrue(SignalInterference.badSignal(clearSpot(), moved, both, 0.0));
        assertTrue(SignalInterference.badSignal(moved, clearSpot(), both, 0.0));
        assertFalse(SignalInterference.badSignal(moved, clearSpot(), target, 0.0));
        assertTrue(SignalInterference.badSignal(clearSpot(), moved, target, 0.0));
    }

    @Test
    void losVisibleModeFailsOnlyWhenSeen() {
        Config los =
                config(false, false, false, false, false, false, false, true);

        assertTrue(SignalInterference.badSignal(clearSpot(), null, los, 0.0, true));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, los, 0.0, false));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, los, 0.0, null));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, los, 0.0));
    }

    @Test
    void losNotVisibleModeFailsOnlyWhenHidden() {
        Config hidden = withModes(
                config(false, false, false, false, false, false, false, true),
                InterfereWhen.ONE_UNMET, InterfereWhenVisible.NOT_VISIBLE);

        assertFalse(SignalInterference.badSignal(clearSpot(), null, hidden, 0.0, true));
        assertTrue(SignalInterference.badSignal(clearSpot(), null, hidden, 0.0, false));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, hidden, 0.0, null));
    }

    @Test
    void lineOfSightNeverCountsTargetSide() {
        Config los =
                config(false, false, false, false, false, false, false, true);

        assertEquals(List.of("line-of-sight"),
                SignalInterference.interferingReasons(clearSpot(), los, true, false));
        assertEquals(List.of(),
                SignalInterference.interferingReasons(clearSpot(), los, true, true));
    }

    @Test
    void altitudeFailsOutsideInclusiveRange() {
        Config altitude =
                config(false, false, false, true, false, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, -20, Weather.CLEAR, "minecraft:plains"),
                null, altitude, 0.0));
        assertFalse(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, 120, Weather.CLEAR, "minecraft:plains"),
                null, altitude, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, -21, Weather.CLEAR, "minecraft:plains"),
                null, altitude, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, 121, Weather.CLEAR, "minecraft:plains"),
                null, altitude, 0.0));
    }

    @Test
    void weatherFailsOnlyDuringListedBuckets() {
        Config weather =
                config(false, false, false, false, true, false, false, false);

        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, 64, Weather.STORM, "minecraft:plains"),
                null, weather, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, 64, Weather.RAIN, "minecraft:plains"),
                null, weather, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, weather, 0.0));
    }

    @Test
    void weatherNeverEvaluatesTargetSide() {
        Config weather =
                config(false, false, false, false, true, false, false, false);
        Snapshot stormy =
                spot(15, 15, true, 0, 0, 64, Weather.STORM, "minecraft:plains");

        assertTrue(SignalInterference.badSignal(stormy, clearSpot(), weather, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), stormy, weather, 0.0));
    }

    @Test
    void biomeMatchesKeysCaseInsensitively() {
        Config biome =
                config(false, false, false, false, false, true, false, false);

        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:desert"),
                null, biome, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, 64, Weather.CLEAR, "MINECRAFT:THE_END"),
                null, biome, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, biome, 0.0));
    }

    @Test
    void requiredToFailNeedsEnoughOptions() {
        Config two = withCounts(
                config(true, true, false, false, false, false, false, false), 2, 0.0);
        Snapshot dark =
                spot(0, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains");
        Snapshot darkAndCovered =
                spot(0, 15, true, 9, 0, 64, Weather.CLEAR, "minecraft:plains");

        assertFalse(SignalInterference.badSignal(dark, null, two, 0.0));
        assertTrue(SignalInterference.badSignal(darkAndCovered, null, two, 0.0));
    }

    @Test
    void requiredToFailClampsToEnabledCount() {
        Config clamped = withCounts(
                config(true, false, false, false, false, false, false, false), 5, 0.0);
        Snapshot dark =
                spot(0, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains");

        assertTrue(SignalInterference.badSignal(dark, null, clamped, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, clamped, 0.0));
    }

    @Test
    void lightCheckOnGatesSides() {
        Config self =
                config(true, false, false, false, false, false, false, false);
        Config both = configFull(true, false, false, false, false, false,
                false, false, CheckOn.BOTH, CheckOn.BOTH, CheckOn.BOTH, CheckOn.BOTH,
                CheckOn.BOTH, CheckOn.SELF, 1, 0.0, InterfereWhen.ONE_UNMET,
                InterfereWhenVisible.VISIBLE);
        Config target = configFull(true, false, false, false, false, false,
                false, false, CheckOn.TARGET, CheckOn.BOTH, CheckOn.BOTH, CheckOn.BOTH,
                CheckOn.BOTH, CheckOn.SELF, 1, 0.0, InterfereWhen.ONE_UNMET,
                InterfereWhenVisible.VISIBLE);
        Snapshot dark =
                spot(0, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains");

        assertFalse(SignalInterference.badSignal(clearSpot(), dark, self, 0.0));
        assertTrue(SignalInterference.badSignal(clearSpot(), dark, both, 0.0));
        assertTrue(SignalInterference.badSignal(dark, clearSpot(), both, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, both, 0.0));
        assertFalse(SignalInterference.badSignal(dark, clearSpot(), target, 0.0));
        assertTrue(SignalInterference.badSignal(clearSpot(), dark, target, 0.0));
    }

    @Test
    void checkOnIsPerOption() {
        Config mixed = configFull(true, true, false, false, false, false,
                false, false, CheckOn.BOTH, CheckOn.SELF, CheckOn.BOTH, CheckOn.BOTH,
                CheckOn.BOTH, CheckOn.SELF, 1, 0.0, InterfereWhen.ONE_UNMET,
                InterfereWhenVisible.VISIBLE);
        Snapshot dark =
                spot(0, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains");
        Snapshot buried =
                spot(15, 15, true, 10, 0, 64, Weather.CLEAR, "minecraft:plains");

        assertTrue(SignalInterference.badSignal(clearSpot(), dark, mixed, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), buried, mixed, 0.0));
        assertTrue(SignalInterference.badSignal(buried, clearSpot(), mixed, 0.0));
    }

    @Test
    void bypassRollSavesBadSignalsUnderChance() {
        Config none =
                config(true, false, false, false, false, false, false, false);
        Config half = withCounts(none, 1, 0.5);
        Config always = withCounts(none, 1, 1.0);
        Snapshot dark =
                spot(0, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains");

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
        Config clamped = new Config(true, 99, -9, null, null,
                true, 999, null, true, 999, null, true, 200, -100, null, true, null,
                true, biomes, null, true, -5.0, null, true, null, 9999, true, null,
                true, 999, null, true, -5, null, true, 500, null, -3, 2.5);

        assertEquals(15, clamped.minSkyLight());
        assertEquals(0, clamped.minBlockLight());
        assertEquals(InterfereWhen.BOTH_UNMET, clamped.interfereWhen());
        assertEquals(CheckOn.SELF, clamped.lightCheckOn());
        assertEquals(CheckOn.BOTH, clamped.undergroundCheckOn());
        assertEquals(CheckOn.BOTH, clamped.underwaterCheckOn());
        assertEquals(CheckOn.BOTH, clamped.altitudeCheckOn());
        assertEquals(CheckOn.BOTH, clamped.biomeCheckOn());
        assertEquals(CheckOn.SELF, clamped.movementCheckOn());
        assertEquals(CheckOn.BOTH, clamped.invisibleCheckOn());
        assertEquals(CheckOn.SELF, clamped.healthCheckOn());
        assertEquals(CheckOn.SELF, clamped.hungerCheckOn());
        assertEquals(CheckOn.SELF, clamped.expCheckOn());
        assertEquals(380, clamped.maxBlocksAbove());
        assertEquals(380, clamped.maxFluidAbove());
        assertEquals(-64, clamped.minY());
        assertEquals(200, clamped.maxY());
        assertTrue(clamped.interfereDuring().isEmpty());
        assertEquals(Set.of("minecraft:desert"), clamped.interfereIn());
        assertEquals(0.0, clamped.thresholdBlocks());
        assertEquals(InterfereWhenVisible.VISIBLE, clamped.losWhen());
        assertEquals(1000, clamped.losMaxDistance());
        assertEquals(100, clamped.minHealth());
        assertEquals(1, clamped.minHunger());
        assertEquals(100, clamped.minExpLevel());
        assertEquals(1.0, clamped.chanceToBypass());
    }

    @Test
    void statThresholdsDefaultToSelf() {
        StatThresholds stats = new StatThresholds(true, 8, null, true, 10, null,
                true, 5, null);

        assertEquals(CheckOn.SELF, stats.healthCheckOn());
        assertEquals(CheckOn.SELF, stats.hungerCheckOn());
        assertEquals(CheckOn.SELF, stats.expCheckOn());
    }

    @Test
    void playerStatsPredicatesCompareAgainstMinimums() {
        assertTrue(SignalInterference.healthInterferes(7.5, 8));
        assertFalse(SignalInterference.healthInterferes(8.0, 8));
        assertTrue(SignalInterference.hungerInterferes(9, 10));
        assertFalse(SignalInterference.hungerInterferes(10, 10));
        assertTrue(SignalInterference.expInterferes(4, 5));
        assertFalse(SignalInterference.expInterferes(5, 5));
    }

    @Test
    void playerStatsFailHolderSideWithReasonIds() {
        Config stats = statsConfig(true, true, true,
                CheckOn.SELF, CheckOn.SELF, CheckOn.SELF);
        Snapshot weak = spot(15, 15, true, 0, 0, 64,
                Weather.CLEAR, "minecraft:plains", 0.0, false, false,
                7.0, 9, 4);

        assertTrue(SignalInterference.badSignal(weak, clearSpot(), stats, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), clearSpot(), stats, 0.0));
        Optional<Reason> reason =
                SignalInterference.lastReason(weak, clearSpot(), stats, 0.0, null);
        assertTrue(reason.isPresent());
        assertEquals("low-exp-level", reason.get().id());
        assertFalse(reason.get().targetSide());
    }

    @Test
    void playerStatsCheckOnGatesSides() {
        Config self = statsConfig(true, false, false,
                CheckOn.SELF, CheckOn.SELF, CheckOn.SELF);
        Config both = statsConfig(true, false, false,
                CheckOn.BOTH, CheckOn.SELF, CheckOn.SELF);
        Config target = statsConfig(true, false, false,
                CheckOn.TARGET, CheckOn.SELF, CheckOn.SELF);
        Snapshot weak = spot(15, 15, true, 0, 0, 64,
                Weather.CLEAR, "minecraft:plains", 0.0, false, false,
                7.0, 20, 30);

        assertFalse(SignalInterference.badSignal(clearSpot(), weak, self, 0.0));
        assertTrue(SignalInterference.badSignal(clearSpot(), weak, both, 0.0));
        assertFalse(SignalInterference.badSignal(weak, clearSpot(), target, 0.0));
        assertTrue(SignalInterference.badSignal(clearSpot(), weak, target, 0.0));
        Optional<Reason> reason =
                SignalInterference.lastReason(clearSpot(), weak, both, 0.0, null);
        assertTrue(reason.isPresent());
        assertEquals("low-health", reason.get().id());
        assertTrue(reason.get().targetSide());
    }

    @Test
    void playerStatsCountTowardRequiredToFail() {
        Config stats = withCounts(statsConfig(true, true, false,
                CheckOn.SELF, CheckOn.SELF, CheckOn.SELF), 2, 0.0);
        Snapshot weak = spot(15, 15, true, 0, 0, 64,
                Weather.CLEAR, "minecraft:plains", 0.0, false, false,
                7.0, 9, 30);
        Snapshot hungry = spot(15, 15, true, 0, 0, 64,
                Weather.CLEAR, "minecraft:plains", 0.0, false, false,
                20.0, 9, 30);

        assertTrue(SignalInterference.badSignal(weak, clearSpot(), stats, 0.0));
        assertFalse(SignalInterference.badSignal(hungry, clearSpot(), stats, 0.0));
    }

    @Test
    void invisibleTargetFailsOnlyOnTargetSide() {
        Config target = invisibleConfig(CheckOn.TARGET);

        assertTrue(SignalInterference.badSignal(clearSpot(), invisibleSpot(), target, 0.0));
        assertFalse(SignalInterference.badSignal(invisibleSpot(), clearSpot(), target, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), clearSpot(), target, 0.0));
    }

    @Test
    void invisibleSelfFailsOnlyOnHolderSide() {
        Config self = invisibleConfig(CheckOn.SELF);

        assertTrue(SignalInterference.badSignal(invisibleSpot(), clearSpot(), self, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), invisibleSpot(), self, 0.0));
    }

    @Test
    void invisibleBothChecksEachSide() {
        Config both = invisibleConfig(CheckOn.BOTH);

        assertTrue(SignalInterference.badSignal(invisibleSpot(), clearSpot(), both, 0.0));
        assertTrue(SignalInterference.badSignal(clearSpot(), invisibleSpot(), both, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), clearSpot(), both, 0.0));
    }

    @Test
    void sideAppliesGatesEachSide() {
        assertTrue(SignalInterference.sideApplies(CheckOn.BOTH, false));
        assertTrue(SignalInterference.sideApplies(CheckOn.BOTH, true));
        assertTrue(SignalInterference.sideApplies(CheckOn.SELF, false));
        assertFalse(SignalInterference.sideApplies(CheckOn.SELF, true));
        assertFalse(SignalInterference.sideApplies(CheckOn.TARGET, false));
        assertTrue(SignalInterference.sideApplies(CheckOn.TARGET, true));
    }

    @Test
    void targetCheckOnSkipsHolderSideLightGroundWater() {
        Snapshot dark = spot(0, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains");
        Snapshot buried =
                spot(15, 15, true, 10, 0, 64, Weather.CLEAR, "minecraft:plains");
        Snapshot soaked = spot(15, 15, true, 0, 3, 64, Weather.CLEAR, "minecraft:plains",
                0.0, true);

        Config light = configFull(true, false, false, false, false, false,
                false, false, CheckOn.TARGET, CheckOn.BOTH, CheckOn.BOTH, CheckOn.BOTH,
                CheckOn.BOTH, CheckOn.SELF, 1, 0.0, InterfereWhen.ONE_UNMET,
                InterfereWhenVisible.VISIBLE);
        Config underground = configFull(false, true, false, false, false, false,
                false, false, CheckOn.SELF, CheckOn.TARGET, CheckOn.BOTH, CheckOn.BOTH,
                CheckOn.BOTH, CheckOn.SELF, 1, 0.0, InterfereWhen.ONE_UNMET,
                InterfereWhenVisible.VISIBLE);
        Config underwater = configFull(false, false, true, false, false, false,
                false, false, CheckOn.SELF, CheckOn.BOTH, CheckOn.TARGET, CheckOn.BOTH,
                CheckOn.BOTH, CheckOn.SELF, 1, 0.0, InterfereWhen.ONE_UNMET,
                InterfereWhenVisible.VISIBLE);
        assertTargetOnly(light, dark);
        assertTargetOnly(underground, buried);
        assertTargetOnly(underwater, soaked);
    }

    @Test
    void targetCheckOnSkipsHolderSideAltitudeBiomeRest() {
        Snapshot weak = spot(15, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains",
                0.0, false, false, 7.0, 9, 4);
        Snapshot high = spot(15, 15, true, 0, 0, 121, Weather.CLEAR, "minecraft:plains");
        Snapshot desert =
                spot(15, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:desert");
        Snapshot moved = spot(15, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:plains",
                5.0, false);
        Config altitude = configFull(false, false, false, true, false, false,
                false, false, CheckOn.SELF, CheckOn.BOTH, CheckOn.BOTH, CheckOn.TARGET,
                CheckOn.BOTH, CheckOn.SELF, 1, 0.0, InterfereWhen.ONE_UNMET,
                InterfereWhenVisible.VISIBLE);
        Config biome = configFull(false, false, false, false, false, true,
                false, false, CheckOn.SELF, CheckOn.BOTH, CheckOn.BOTH, CheckOn.BOTH,
                CheckOn.TARGET, CheckOn.SELF, 1, 0.0, InterfereWhen.ONE_UNMET,
                InterfereWhenVisible.VISIBLE);
        Config movement = configFull(false, false, false, false, false, false,
                true, false, CheckOn.SELF, CheckOn.BOTH, CheckOn.BOTH, CheckOn.BOTH,
                CheckOn.BOTH, CheckOn.TARGET, 1, 0.0, InterfereWhen.ONE_UNMET,
                InterfereWhenVisible.VISIBLE);
        assertTargetOnly(altitude, high);
        assertTargetOnly(biome, desert);
        assertTargetOnly(movement, moved);
        assertTargetOnly(invisibleConfig(CheckOn.TARGET), invisibleSpot());
        assertTargetOnly(statsConfig(true, false, false,
                CheckOn.TARGET, CheckOn.SELF, CheckOn.SELF), weak);
        assertTargetOnly(statsConfig(false, true, false,
                CheckOn.SELF, CheckOn.TARGET, CheckOn.SELF), weak);
        assertTargetOnly(statsConfig(false, false, true,
                CheckOn.SELF, CheckOn.SELF, CheckOn.TARGET), weak);
    }



    private static void assertTargetOnly(Config config, Snapshot failing) {
        assertFalse(SignalInterference.badSignal(failing, clearSpot(), config, 0.0));
        assertTrue(SignalInterference.badSignal(clearSpot(), failing, config, 0.0));
    }

    @Test
    void invisibleReportsReasonIdAndSide() {
        Config target = invisibleConfig(CheckOn.TARGET);

        Optional<Reason> reason = SignalInterference.lastReason(
                clearSpot(), invisibleSpot(), target, 0.0, null);

        assertTrue(reason.isPresent());
        assertEquals("invisible", reason.get().id());
        assertTrue(reason.get().targetSide());
    }

    @Test
    void lastReasonNamesTheSingleFailure() {
        Config underground =
                config(false, true, false, false, false, false, false, false);

        assertEquals(Optional.of(new Reason("underground", false)),
                SignalInterference.lastReason(
                        spot(15, 15, true, 10, 0, 64, Weather.CLEAR,
                                "minecraft:plains"),
                        null, underground, 0.99, null));
    }

    @Test
    void lastReasonPicksTheMostRecentOfMany() {
        Config both =
                config(false, true, false, false, false, true, false, false);

        assertEquals(Optional.of(new Reason("biome", false)),
                SignalInterference.lastReason(
                        spot(15, 15, true, 10, 0, 64, Weather.CLEAR,
                                "minecraft:desert"),
                        null, both, 0.99, null));
    }

    @Test
    void lastReasonEmptyWhenGoodOrBypassed() {
        Config underground =
                config(false, true, false, false, false, false, false, false);
        Snapshot buried =
                spot(15, 15, true, 10, 0, 64, Weather.CLEAR, "minecraft:plains");

        assertEquals(Optional.empty(),
                SignalInterference.lastReason(clearSpot(), null, underground, 0.99, null));
        assertEquals(Optional.empty(), SignalInterference.lastReason(
                buried, null, withCounts(underground, 1, 1.0), 0.0, null));
    }

    @Test
    void lastReasonPrefersTheHolderSide() {
        Config both = configFull(false, true, false, false, false, true,
                false, false, CheckOn.SELF, CheckOn.BOTH, CheckOn.BOTH, CheckOn.BOTH,
                CheckOn.BOTH, CheckOn.SELF, 1, 0.0, InterfereWhen.ONE_UNMET,
                InterfereWhenVisible.VISIBLE);
        Snapshot buried =
                spot(15, 15, true, 10, 0, 64, Weather.CLEAR, "minecraft:plains");
        Snapshot desert =
                spot(15, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:desert");

        assertEquals(Optional.of(new Reason("underground", false)),
                SignalInterference.lastReason(buried, desert, both, 0.99, null));
    }

    @Test
    void lastReasonMarksTargetSideFailures() {
        Config biome = configFull(false, false, false, false, false, true,
                false, false, CheckOn.SELF, CheckOn.BOTH, CheckOn.BOTH, CheckOn.BOTH,
                CheckOn.BOTH, CheckOn.SELF, 1, 0.0, InterfereWhen.ONE_UNMET,
                InterfereWhenVisible.VISIBLE);
        Snapshot desert =
                spot(15, 15, true, 0, 0, 64, Weather.CLEAR, "minecraft:desert");

        assertEquals(Optional.of(new Reason("biome", true)),
                SignalInterference.lastReason(clearSpot(), desert, biome, 0.99, null));
    }

    @Test
    void lastReasonIdsLineOfSightByMode() {
        Config visible =
                config(false, false, false, false, false, false, false, true);
        Config hidden = withModes(visible,
                InterfereWhen.ONE_UNMET, InterfereWhenVisible.NOT_VISIBLE);

        assertEquals(Optional.of(new Reason("line-of-sight", false)),
                SignalInterference.lastReason(clearSpot(), null, visible, 0.0, true));
        assertEquals(Optional.of(new Reason("line-of-sight-hidden", false)),
                SignalInterference.lastReason(clearSpot(), null, hidden, 0.0, false));
    }
}
