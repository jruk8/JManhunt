package com.jruk8.jmanhunt.settings.world_engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldEngineConfigTest {

    @Test
    void calculatesDiameterUsingLargerOfConfiguredRadiusAndSpreadPlusOne() {
        // startBorderRadius (10) > tpSpreadRadius + 1 (6) → use 10
        assertEquals(20, WorldEngineConfig.calculateStartBorderDiameter(5, 10));
    }

    @Test
    void calculatesDiameterUsingSpreadPlusOneWhenRadiusIsSmaller() {
        // startBorderRadius (3) < tpSpreadRadius + 1 (21) → use 21
        assertEquals(42, WorldEngineConfig.calculateStartBorderDiameter(20, 3));
    }

    @Test
    void calculatesDiameterUsingSpreadPlusOneOnlyWhenRadiusIsMinusOne() {
        // -1 means use tpSpreadRadius + 1 only
        assertEquals(12, WorldEngineConfig.calculateStartBorderDiameter(5, -1));
    }

    @Test
    void calculatesDiameterWithZeroSpread() {
        // tpSpreadRadius 0 → spreadBasedRadius = 1, radius 10 wins
        assertEquals(20, WorldEngineConfig.calculateStartBorderDiameter(0, 10));
    }

    @Test
    void calculatesDiameterWithZeroSpreadAndMinusOneRadius() {
        // tpSpreadRadius 0, radius -1 → use 0 + 1 = 1
        assertEquals(2, WorldEngineConfig.calculateStartBorderDiameter(0, -1));
    }

    @Test
    void calculatesDiameterWhenRadiusEqualsSpreadPlusOne() {
        // Both are equal (10 == 9 + 1) → use 10
        assertEquals(20, WorldEngineConfig.calculateStartBorderDiameter(9, 10));
    }
}