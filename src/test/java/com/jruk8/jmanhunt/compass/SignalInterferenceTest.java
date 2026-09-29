package com.jruk8.jmanhunt.compass;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verdict matrix for the pure signal-interference core: each option,
 * required-to-fail combining, per-option two-way sides, bypass
 * boundaries, reason ids, and config clamping. No Bukkit server needed.
 */
class SignalInterferenceTest {

    private static SignalInterference.Snapshot spot(int sky, int block, boolean normal, int above,
            int fluids, int y, SignalInterference.Weather weather, String biome) {
        return spot(sky, block, normal, above, fluids, y, weather, biome, 0.0, false);
    }

    private static SignalInterference.Snapshot spot(int sky, int block, boolean normal, int above,
            int fluids, int y, SignalInterference.Weather weather, String biome,
            double moved, boolean feetInWater) {
        return spot(sky, block, normal, above, fluids, y, weather, biome, moved, feetInWater,
                false);
    }

    private static SignalInterference.Snapshot spot(int sky, int block, boolean normal, int above,
            int fluids, int y, SignalInterference.Weather weather, String biome,
            double moved, boolean feetInWater, boolean invisible) {
        return spot(sky, block, normal, above, fluids, y, weather, biome, moved, feetInWater,
                invisible, 20.0, 20, 30);
    }

    private static SignalInterference.Snapshot spot(int sky, int block, boolean normal, int above,
            int fluids, int y, SignalInterference.Weather weather, String biome,
            double moved, boolean feetInWater, boolean invisible, double health, int hunger,
            int expLevel) {
        return new SignalInterference.Snapshot(sky, block, normal, above, fluids, y, weather,
                biome, moved, feetInWater, invisible, health, hunger, expLevel);
    }

    private static SignalInterference.Snapshot clearSpot() {
        return spot(15, 15, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");
    }

    private static SignalInterference.Snapshot invisibleSpot() {
        return spot(15, 15, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains",
                0.0, false, true);
    }

    private static SignalInterference.Config config(boolean light, boolean underground,
            boolean underwater, boolean altitude, boolean weather, boolean biome,
            boolean movement, boolean los) {
        return configFull(light, underground, underwater, altitude, weather, biome, movement,
                los, false, false, false, false, false, false, false, 1, 0.0,
                SignalInterference.InterfereWhen.ONE_UNMET,
                SignalInterference.InterfereWhenVisible.VISIBLE);
    }

    private static SignalInterference.Config configFull(boolean light, boolean underground,
            boolean underwater, boolean altitude, boolean weather, boolean biome,
            boolean movement, boolean los, boolean lightTwo, boolean undergroundTwo,
            boolean underwaterTwo, boolean altitudeTwo, boolean weatherTwo, boolean biomeTwo,
            boolean movementTwo, int required, double bypass,
            SignalInterference.InterfereWhen when,
            SignalInterference.InterfereWhenVisible losWhen) {
        return new SignalInterference.Config(light, 10, 5, when, lightTwo,
                underground, 3, undergroundTwo, underwater, 2, underwaterTwo,
                altitude, -20, 120, altitudeTwo, weather,
                Set.of(SignalInterference.Weather.STORM, SignalInterference.Weather.RAIN),
                weatherTwo, biome, Set.of("minecraft:desert", "minecraft:the_end"), biomeTwo,
                movement, 0.2, movementTwo, los, losWhen, 300, false,
                SignalInterference.InvisibleMode.TARGET, false, false, 8, false,
                false, 10, false, false, 5, false, required, bypass);
    }

    private static SignalInterference.Config invisibleConfig(
            SignalInterference.InvisibleMode mode, boolean twoWay) {
        return new SignalInterference.Config(false, 10, 5,
                SignalInterference.InterfereWhen.ONE_UNMET, false,
                false, 3, false, false, 2, false, false, -20, 120, false, false,
                Set.of(), false, false, Set.of(), false, false, 0.2, false,
                false, SignalInterference.InterfereWhenVisible.VISIBLE, 300,
                true, mode, twoWay, false, 8, false, false, 10, false, false, 5,
                false, 1, 0.0);
    }

    private static SignalInterference.Config statsConfig(boolean health, boolean hunger,
            boolean experience, boolean healthTwo, boolean hungerTwo, boolean expTwo) {
        return new SignalInterference.Config(false, 10, 5,
                SignalInterference.InterfereWhen.ONE_UNMET, false,
                false, 3, false, false, 2, false, false, -20, 120, false, false,
                Set.of(), false, false, Set.of(), false, false, 0.2, false,
                false, SignalInterference.InterfereWhenVisible.VISIBLE, 300,
                false, SignalInterference.InvisibleMode.TARGET, false, health, 8,
                healthTwo, hunger, 10, hungerTwo, experience, 5, expTwo, 1, 0.0);
    }

    private static SignalInterference.Config withCounts(
            SignalInterference.Config base, int required, double bypass) {
        return new SignalInterference.Config(base.lightEnabled(), base.minSkyLight(),
                base.minBlockLight(), base.interfereWhen(), base.lightTwoWay(),
                base.undergroundEnabled(), base.maxBlocksAbove(), base.undergroundTwoWay(),
                base.underwaterEnabled(), base.maxFluidAbove(), base.underwaterTwoWay(),
                base.altitudeEnabled(), base.minY(), base.maxY(), base.altitudeTwoWay(),
                base.weatherEnabled(), base.interfereDuring(), base.weatherTwoWay(),
                base.biomeEnabled(), base.interfereIn(), base.biomeTwoWay(),
                base.movementEnabled(), base.thresholdBlocks(), base.movementTwoWay(),
                base.losEnabled(), base.losWhen(), base.losMaxDistance(),
                base.invisibleEnabled(), base.invisibleMode(), base.invisibleTwoWay(),
                base.healthEnabled(), base.minHealth(), base.healthTwoWay(),
                base.hungerEnabled(), base.minHunger(), base.hungerTwoWay(),
                base.expEnabled(), base.minExpLevel(), base.expTwoWay(), required, bypass);
    }

    private static SignalInterference.Config withModes(SignalInterference.Config base,
            SignalInterference.InterfereWhen when,
            SignalInterference.InterfereWhenVisible losWhen) {
        return new SignalInterference.Config(base.lightEnabled(), base.minSkyLight(),
                base.minBlockLight(), when, base.lightTwoWay(),
                base.undergroundEnabled(), base.maxBlocksAbove(), base.undergroundTwoWay(),
                base.underwaterEnabled(), base.maxFluidAbove(), base.underwaterTwoWay(),
                base.altitudeEnabled(), base.minY(), base.maxY(), base.altitudeTwoWay(),
                base.weatherEnabled(), base.interfereDuring(), base.weatherTwoWay(),
                base.biomeEnabled(), base.interfereIn(), base.biomeTwoWay(),
                base.movementEnabled(), base.thresholdBlocks(), base.movementTwoWay(),
                base.losEnabled(), losWhen, base.losMaxDistance(), base.invisibleEnabled(),
                base.invisibleMode(), base.invisibleTwoWay(), base.healthEnabled(),
                base.minHealth(), base.healthTwoWay(), base.hungerEnabled(), base.minHunger(),
                base.hungerTwoWay(), base.expEnabled(), base.minExpLevel(), base.expTwoWay(),
                base.requiredToFail(), base.chanceToBypass());
    }

    @Test
    void allDisabledNeverFails() {
        SignalInterference.Config off = configFull(false, false, false, false, false, false,
                false, false, true, true, true, true, true, true, true, 1, 0.0,
                SignalInterference.InterfereWhen.ONE_UNMET,
                SignalInterference.InterfereWhenVisible.VISIBLE);
        SignalInterference.Snapshot worst =
                spot(0, 0, true, 380, 380, -64, SignalInterference.Weather.STORM, "minecraft:desert",
                        999.0, true);

        assertFalse(SignalInterference.badSignal(worst, worst, off, 0.99));
    }

    @Test
    void lightOneUnmetFailsOnEitherReading() {
        SignalInterference.Config light =
                config(true, false, false, false, false, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(10, 5, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, light, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(9, 5, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, light, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(10, 4, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, light, 0.0));
    }

    @Test
    void lightBothUnmetNeedsBothReadings() {
        SignalInterference.Config both = withModes(
                config(true, false, false, false, false, false, false, false),
                SignalInterference.InterfereWhen.BOTH_UNMET,
                SignalInterference.InterfereWhenVisible.VISIBLE);

        assertFalse(SignalInterference.badSignal(
                spot(9, 5, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, both, 0.0));
        assertFalse(SignalInterference.badSignal(
                spot(10, 4, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, both, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(9, 4, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, both, 0.0));
    }

    @Test
    void lightSkippedOutsideOverworld() {
        SignalInterference.Config light =
                config(true, false, false, false, false, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(0, 0, false, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, light, 0.0));
    }

    @Test
    void undergroundFailsPastMaximum() {
        SignalInterference.Config underground =
                config(false, true, false, false, false, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(15, 15, true, 3, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, underground, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 4, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, underground, 0.0));
    }

    @Test
    void underwaterFailsPastMaximum() {
        SignalInterference.Config underwater =
                config(false, false, true, false, false, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(15, 15, true, 0, 2, 64, SignalInterference.Weather.CLEAR, "minecraft:plains",
                        0.0, true),
                null, underwater, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 3, 64, SignalInterference.Weather.CLEAR, "minecraft:plains",
                        0.0, true),
                null, underwater, 0.0));
    }

    @Test
    void underwaterNeedsWaterFeet() {
        SignalInterference.Config underwater =
                config(false, false, true, false, false, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(15, 15, true, 0, 380, 64, SignalInterference.Weather.CLEAR, "minecraft:plains",
                        0.0, false),
                null, underwater, 0.0));
    }

    @Test
    void movementFailsPastThreshold() {
        SignalInterference.Config movement =
                config(false, false, false, false, false, false, true, false);
        SignalInterference.Snapshot still =
                spot(15, 15, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains",
                        0.2, false);
        SignalInterference.Snapshot stepped =
                spot(15, 15, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains",
                        0.21, false);

        assertFalse(SignalInterference.badSignal(still, null, movement, 0.0));
        assertTrue(SignalInterference.badSignal(stepped, null, movement, 0.0));
        assertEquals(Optional.of(new SignalInterference.Reason("moved", false)),
                SignalInterference.lastReason(stepped, null, movement, 0.0, null));
    }

    @Test
    void movementTwoWayChecksTargetSide() {
        SignalInterference.Config oneWay =
                config(false, false, false, false, false, false, true, false);
        SignalInterference.Config twoWay = configFull(false, false, false, false, false, false,
                true, false, false, false, false, false, false, false, true, 1, 0.0,
                SignalInterference.InterfereWhen.ONE_UNMET,
                SignalInterference.InterfereWhenVisible.VISIBLE);
        SignalInterference.Snapshot moved =
                spot(15, 15, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains",
                        5.0, false);

        assertFalse(SignalInterference.badSignal(clearSpot(), moved, oneWay, 0.0));
        assertTrue(SignalInterference.badSignal(clearSpot(), moved, twoWay, 0.0));
    }

    @Test
    void losVisibleModeFailsOnlyWhenSeen() {
        SignalInterference.Config los =
                config(false, false, false, false, false, false, false, true);

        assertTrue(SignalInterference.badSignal(clearSpot(), null, los, 0.0, true));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, los, 0.0, false));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, los, 0.0, null));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, los, 0.0));
    }

    @Test
    void losNotVisibleModeFailsOnlyWhenHidden() {
        SignalInterference.Config hidden = withModes(
                config(false, false, false, false, false, false, false, true),
                SignalInterference.InterfereWhen.ONE_UNMET,
                SignalInterference.InterfereWhenVisible.NOT_VISIBLE);

        assertFalse(SignalInterference.badSignal(clearSpot(), null, hidden, 0.0, true));
        assertTrue(SignalInterference.badSignal(clearSpot(), null, hidden, 0.0, false));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, hidden, 0.0, null));
    }

    @Test
    void lineOfSightNeverCountsTargetSide() {
        SignalInterference.Config los =
                config(false, false, false, false, false, false, false, true);

        assertEquals(List.of("line-of-sight"),
                SignalInterference.interferingReasons(clearSpot(), los, true, false));
        assertEquals(List.of(),
                SignalInterference.interferingReasons(clearSpot(), los, true, true));
    }

    @Test
    void altitudeFailsOutsideInclusiveRange() {
        SignalInterference.Config altitude =
                config(false, false, false, true, false, false, false, false);

        assertFalse(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, -20, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, altitude, 0.0));
        assertFalse(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, 120, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, altitude, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, -21, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, altitude, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, 121, SignalInterference.Weather.CLEAR, "minecraft:plains"),
                null, altitude, 0.0));
    }

    @Test
    void weatherFailsOnlyDuringListedBuckets() {
        SignalInterference.Config weather =
                config(false, false, false, false, true, false, false, false);

        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, 64, SignalInterference.Weather.STORM, "minecraft:plains"),
                null, weather, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, 64, SignalInterference.Weather.RAIN, "minecraft:plains"),
                null, weather, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, weather, 0.0));
    }

    @Test
    void biomeMatchesKeysCaseInsensitively() {
        SignalInterference.Config biome =
                config(false, false, false, false, false, true, false, false);

        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:desert"),
                null, biome, 0.0));
        assertTrue(SignalInterference.badSignal(
                spot(15, 15, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "MINECRAFT:THE_END"),
                null, biome, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, biome, 0.0));
    }

    @Test
    void requiredToFailNeedsEnoughOptions() {
        SignalInterference.Config two = withCounts(
                config(true, true, false, false, false, false, false, false), 2, 0.0);
        SignalInterference.Snapshot dark =
                spot(0, 15, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");
        SignalInterference.Snapshot darkAndCovered =
                spot(0, 15, true, 9, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");

        assertFalse(SignalInterference.badSignal(dark, null, two, 0.0));
        assertTrue(SignalInterference.badSignal(darkAndCovered, null, two, 0.0));
    }

    @Test
    void requiredToFailClampsToEnabledCount() {
        SignalInterference.Config clamped = withCounts(
                config(true, false, false, false, false, false, false, false), 5, 0.0);
        SignalInterference.Snapshot dark =
                spot(0, 15, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");

        assertTrue(SignalInterference.badSignal(dark, null, clamped, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, clamped, 0.0));
    }

    @Test
    void lightTwoWayFailsFromTargetSide() {
        SignalInterference.Config oneWay =
                config(true, false, false, false, false, false, false, false);
        SignalInterference.Config twoWay = configFull(true, false, false, false, false, false,
                false, false, true, false, false, false, false, false, false, 1, 0.0,
                SignalInterference.InterfereWhen.ONE_UNMET,
                SignalInterference.InterfereWhenVisible.VISIBLE);
        SignalInterference.Snapshot dark =
                spot(0, 15, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");

        assertFalse(SignalInterference.badSignal(clearSpot(), dark, oneWay, 0.0));
        assertTrue(SignalInterference.badSignal(clearSpot(), dark, twoWay, 0.0));
        assertTrue(SignalInterference.badSignal(dark, clearSpot(), twoWay, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), null, twoWay, 0.0));
    }

    @Test
    void twoWayIsPerOption() {
        SignalInterference.Config mixed = configFull(true, true, false, false, false, false,
                false, false, true, false, false, false, false, false, false, 1, 0.0,
                SignalInterference.InterfereWhen.ONE_UNMET,
                SignalInterference.InterfereWhenVisible.VISIBLE);
        SignalInterference.Snapshot dark =
                spot(0, 15, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");
        SignalInterference.Snapshot buried =
                spot(15, 15, true, 10, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");

        assertTrue(SignalInterference.badSignal(clearSpot(), dark, mixed, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), buried, mixed, 0.0));
        assertTrue(SignalInterference.badSignal(buried, clearSpot(), mixed, 0.0));
    }

    @Test
    void bypassRollSavesBadSignalsUnderChance() {
        SignalInterference.Config none =
                config(true, false, false, false, false, false, false, false);
        SignalInterference.Config half = withCounts(none, 1, 0.5);
        SignalInterference.Config always = withCounts(none, 1, 1.0);
        SignalInterference.Snapshot dark =
                spot(0, 15, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");

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
        SignalInterference.Config clamped = new SignalInterference.Config(true, 99, -9, null, true,
                true, 999, true, true, 999, true, true, 200, -100, true, true, null, true,
                true, biomes, true, true, -5.0, true, true, null, 9999, true, null, true,
                true, 999, true, true, -5, true, true, 500, true, -3, 2.5);

        assertEquals(15, clamped.minSkyLight());
        assertEquals(0, clamped.minBlockLight());
        assertEquals(SignalInterference.InterfereWhen.BOTH_UNMET, clamped.interfereWhen());
        assertEquals(380, clamped.maxBlocksAbove());
        assertEquals(380, clamped.maxFluidAbove());
        assertEquals(-64, clamped.minY());
        assertEquals(200, clamped.maxY());
        assertTrue(clamped.interfereDuring().isEmpty());
        assertEquals(Set.of("minecraft:desert"), clamped.interfereIn());
        assertEquals(0.0, clamped.thresholdBlocks());
        assertEquals(SignalInterference.InterfereWhenVisible.NOT_VISIBLE, clamped.losWhen());
        assertEquals(1000, clamped.losMaxDistance());
        assertEquals(SignalInterference.InvisibleMode.TARGET, clamped.invisibleMode());
        assertEquals(100, clamped.minHealth());
        assertEquals(1, clamped.minHunger());
        assertEquals(100, clamped.minExpLevel());
        assertEquals(1.0, clamped.chanceToBypass());
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
        SignalInterference.Config stats = statsConfig(true, true, true, false, false, false);
        SignalInterference.Snapshot weak = spot(15, 15, true, 0, 0, 64,
                SignalInterference.Weather.CLEAR, "minecraft:plains", 0.0, false, false,
                7.0, 9, 4);

        assertTrue(SignalInterference.badSignal(weak, clearSpot(), stats, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), clearSpot(), stats, 0.0));
        Optional<SignalInterference.Reason> reason =
                SignalInterference.lastReason(weak, clearSpot(), stats, 0.0, null);
        assertTrue(reason.isPresent());
        assertEquals("low-exp-level", reason.get().id());
        assertFalse(reason.get().targetSide());
    }

    @Test
    void playerStatsTwoWayChecksTargetSide() {
        SignalInterference.Config oneWay = statsConfig(true, false, false, false, false, false);
        SignalInterference.Config twoWay = statsConfig(true, false, false, true, false, false);
        SignalInterference.Snapshot weak = spot(15, 15, true, 0, 0, 64,
                SignalInterference.Weather.CLEAR, "minecraft:plains", 0.0, false, false,
                7.0, 20, 30);

        assertFalse(SignalInterference.badSignal(clearSpot(), weak, oneWay, 0.0));
        assertTrue(SignalInterference.badSignal(clearSpot(), weak, twoWay, 0.0));
        Optional<SignalInterference.Reason> reason =
                SignalInterference.lastReason(clearSpot(), weak, twoWay, 0.0, null);
        assertTrue(reason.isPresent());
        assertEquals("low-health", reason.get().id());
        assertTrue(reason.get().targetSide());
    }

    @Test
    void playerStatsCountTowardRequiredToFail() {
        SignalInterference.Config stats =
                withCounts(statsConfig(true, true, false, false, false, false), 2, 0.0);
        SignalInterference.Snapshot weak = spot(15, 15, true, 0, 0, 64,
                SignalInterference.Weather.CLEAR, "minecraft:plains", 0.0, false, false,
                7.0, 9, 30);
        SignalInterference.Snapshot hungry = spot(15, 15, true, 0, 0, 64,
                SignalInterference.Weather.CLEAR, "minecraft:plains", 0.0, false, false,
                20.0, 9, 30);

        assertTrue(SignalInterference.badSignal(weak, clearSpot(), stats, 0.0));
        assertFalse(SignalInterference.badSignal(hungry, clearSpot(), stats, 0.0));
    }

    @Test
    void invisibleTargetModeFailsOnlyOnTargetSide() {
        SignalInterference.Config target =
                invisibleConfig(SignalInterference.InvisibleMode.TARGET, false);

        assertTrue(SignalInterference.badSignal(clearSpot(), invisibleSpot(), target, 0.0));
        assertFalse(SignalInterference.badSignal(invisibleSpot(), clearSpot(), target, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), clearSpot(), target, 0.0));
    }

    @Test
    void invisibleSelfModeFailsOnlyOnHolderSide() {
        SignalInterference.Config self =
                invisibleConfig(SignalInterference.InvisibleMode.SELF, false);

        assertTrue(SignalInterference.badSignal(invisibleSpot(), clearSpot(), self, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), invisibleSpot(), self, 0.0));
    }

    @Test
    void invisibleTwoWayChecksBothSides() {
        SignalInterference.Config both =
                invisibleConfig(SignalInterference.InvisibleMode.TARGET, true);

        assertTrue(SignalInterference.badSignal(invisibleSpot(), clearSpot(), both, 0.0));
        assertTrue(SignalInterference.badSignal(clearSpot(), invisibleSpot(), both, 0.0));
        assertFalse(SignalInterference.badSignal(clearSpot(), clearSpot(), both, 0.0));
    }

    @Test
    void invisibleSideAppliesPicksExactlyOneSide() {
        assertTrue(SignalInterference.invisibleSideApplies(
                SignalInterference.InvisibleMode.TARGET, false, true));
        assertFalse(SignalInterference.invisibleSideApplies(
                SignalInterference.InvisibleMode.TARGET, false, false));
        assertTrue(SignalInterference.invisibleSideApplies(
                SignalInterference.InvisibleMode.SELF, false, false));
        assertFalse(SignalInterference.invisibleSideApplies(
                SignalInterference.InvisibleMode.SELF, false, true));
        assertTrue(SignalInterference.invisibleSideApplies(
                SignalInterference.InvisibleMode.TARGET, true, false));
        assertTrue(SignalInterference.invisibleSideApplies(
                SignalInterference.InvisibleMode.SELF, true, true));
    }

    @Test
    void invisibleReportsReasonIdAndSide() {
        SignalInterference.Config target =
                invisibleConfig(SignalInterference.InvisibleMode.TARGET, false);

        Optional<SignalInterference.Reason> reason = SignalInterference.lastReason(
                clearSpot(), invisibleSpot(), target, 0.0, null);

        assertTrue(reason.isPresent());
        assertEquals("invisible", reason.get().id());
        assertTrue(reason.get().targetSide());
    }

    @Test
    void lastReasonNamesTheSingleFailure() {
        SignalInterference.Config underground =
                config(false, true, false, false, false, false, false, false);

        assertEquals(Optional.of(new SignalInterference.Reason("underground", false)),
                SignalInterference.lastReason(
                        spot(15, 15, true, 10, 0, 64, SignalInterference.Weather.CLEAR,
                                "minecraft:plains"),
                        null, underground, 0.99, null));
    }

    @Test
    void lastReasonPicksTheMostRecentOfMany() {
        SignalInterference.Config both =
                config(false, true, false, false, false, true, false, false);

        assertEquals(Optional.of(new SignalInterference.Reason("biome", false)),
                SignalInterference.lastReason(
                        spot(15, 15, true, 10, 0, 64, SignalInterference.Weather.CLEAR,
                                "minecraft:desert"),
                        null, both, 0.99, null));
    }

    @Test
    void lastReasonEmptyWhenGoodOrBypassed() {
        SignalInterference.Config underground =
                config(false, true, false, false, false, false, false, false);
        SignalInterference.Snapshot buried =
                spot(15, 15, true, 10, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");

        assertEquals(Optional.empty(),
                SignalInterference.lastReason(clearSpot(), null, underground, 0.99, null));
        assertEquals(Optional.empty(), SignalInterference.lastReason(
                buried, null, withCounts(underground, 1, 1.0), 0.0, null));
    }

    @Test
    void lastReasonPrefersTheHolderSide() {
        SignalInterference.Config both = configFull(false, true, false, false, false, true,
                false, false, false, false, false, false, false, true, false, 1, 0.0,
                SignalInterference.InterfereWhen.ONE_UNMET,
                SignalInterference.InterfereWhenVisible.VISIBLE);
        SignalInterference.Snapshot buried =
                spot(15, 15, true, 10, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:plains");
        SignalInterference.Snapshot desert =
                spot(15, 15, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:desert");

        assertEquals(Optional.of(new SignalInterference.Reason("underground", false)),
                SignalInterference.lastReason(buried, desert, both, 0.99, null));
    }

    @Test
    void lastReasonMarksTargetSideFailures() {
        SignalInterference.Config biome = configFull(false, false, false, false, false, true,
                false, false, false, false, false, false, false, true, false, 1, 0.0,
                SignalInterference.InterfereWhen.ONE_UNMET,
                SignalInterference.InterfereWhenVisible.VISIBLE);
        SignalInterference.Snapshot desert =
                spot(15, 15, true, 0, 0, 64, SignalInterference.Weather.CLEAR, "minecraft:desert");

        assertEquals(Optional.of(new SignalInterference.Reason("biome", true)),
                SignalInterference.lastReason(clearSpot(), desert, biome, 0.99, null));
    }

    @Test
    void lastReasonIdsLineOfSightByMode() {
        SignalInterference.Config visible =
                config(false, false, false, false, false, false, false, true);
        SignalInterference.Config hidden = withModes(visible,
                SignalInterference.InterfereWhen.ONE_UNMET,
                SignalInterference.InterfereWhenVisible.NOT_VISIBLE);

        assertEquals(Optional.of(new SignalInterference.Reason("line-of-sight", false)),
                SignalInterference.lastReason(clearSpot(), null, visible, 0.0, true));
        assertEquals(Optional.of(new SignalInterference.Reason("line-of-sight-hidden", false)),
                SignalInterference.lastReason(clearSpot(), null, hidden, 0.0, false));
    }
}
