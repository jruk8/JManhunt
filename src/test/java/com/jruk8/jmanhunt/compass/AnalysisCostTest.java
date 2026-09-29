package com.jruk8.jmanhunt.compass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisCostTest {

    @Test
    void chargesAtMatchesPointsCaseInsensitively() {
        assertTrue(AnalysisCost.chargesAt(List.of("INITIATE"), "INITIATE"));
        assertTrue(AnalysisCost.chargesAt(List.of("initiate", "success"), "SUCCESS"));
        assertFalse(AnalysisCost.chargesAt(List.of("INITIATE"), "SUCCESS"));
        assertFalse(AnalysisCost.chargesAt(List.of("BOGUS"), "INITIATE"));
        assertFalse(AnalysisCost.chargesAt(null, "INITIATE"));
        assertFalse(AnalysisCost.chargesAt(List.of("INITIATE"), null));
    }

    @Test
    void lackingNamesUncoveredChargesInOrder() {
        AnalysisCost.Payment payment = new AnalysisCost.Payment(true, 3, true, 4, true, true, 1);
        AnalysisCost.Stats poor = new AnalysisCost.Stats(3.0, 0.0, 0, 0);

        assertEquals(List.of("low-health", "hungry", "low-exp-level"),
                AnalysisCost.lacking(poor, payment));
    }

    @Test
    void lackingSkipsDisabledChargesAndBoundaries() {
        AnalysisCost.Payment payment = new AnalysisCost.Payment(false, 40, false, 100, true,
                false, 100);
        AnalysisCost.Stats poor = new AnalysisCost.Stats(0.0, 0.0, 0, 0);

        assertEquals(List.of(), AnalysisCost.lacking(poor, payment));

        AnalysisCost.Payment enabled = new AnalysisCost.Payment(true, 3, true, 4, true, true, 1);
        AnalysisCost.Stats exact = new AnalysisCost.Stats(4.0, 3.0, 0, 1);

        assertEquals(List.of(), AnalysisCost.lacking(exact, enabled));
    }

    @Test
    void lackingTreatsSaturationAndHungerAsOnePool() {
        AnalysisCost.Payment payment = new AnalysisCost.Payment(true, 3, false, 4, true, false, 1);

        assertEquals(List.of(),
                AnalysisCost.lacking(new AnalysisCost.Stats(20.0, 1.0, 19, 5), payment));
        assertEquals(List.of("hungry"),
                AnalysisCost.lacking(new AnalysisCost.Stats(20.0, 0.0, 2, 5), payment));
    }

    @Test
    void chargeDrainsSaturationBeforeHunger() {
        AnalysisCost.Payment payment = new AnalysisCost.Payment(true, 3, false, 4, true, false, 1);

        AnalysisCost.Charge satOnly = AnalysisCost
                .charge(new AnalysisCost.Stats(20.0, 5.0, 20, 5), payment);
        assertEquals(2.0, satOnly.saturation(), 1e-9);
        assertEquals(20, satOnly.foodLevel());

        AnalysisCost.Charge spill = AnalysisCost
                .charge(new AnalysisCost.Stats(20.0, 1.0, 20, 5), payment);
        assertEquals(0.0, spill.saturation(), 1e-9);
        assertEquals(18, spill.foodLevel());

        AnalysisCost.Charge dry = AnalysisCost
                .charge(new AnalysisCost.Stats(20.0, 0.0, 1, 5), payment);
        assertEquals(0.0, dry.saturation(), 1e-9);
        assertEquals(0, dry.foodLevel());
    }

    @Test
    void chargeFloorsHealthWithoutCanKill() {
        AnalysisCost.Payment lethal = new AnalysisCost.Payment(false, 3, true, 4, true, false, 1);
        AnalysisCost.Payment gentle = new AnalysisCost.Payment(false, 3, true, 4, false, false, 1);

        assertEquals(0.0,
                AnalysisCost.charge(new AnalysisCost.Stats(3.0, 0.0, 20, 5), lethal).health(),
                1e-9);
        assertEquals(1.0,
                AnalysisCost.charge(new AnalysisCost.Stats(3.0, 0.0, 20, 5), gentle).health(),
                1e-9);
        assertEquals(16.0,
                AnalysisCost.charge(new AnalysisCost.Stats(20.0, 0.0, 20, 5), gentle).health(),
                1e-9);
    }

    @Test
    void chargeFloorsLevelsAndSkipsDisabled() {
        AnalysisCost.Payment payment = new AnalysisCost.Payment(false, 3, false, 4, true, true, 1);

        AnalysisCost.Charge charged = AnalysisCost
                .charge(new AnalysisCost.Stats(19.0, 4.0, 18, 0), payment);
        assertEquals(19.0, charged.health(), 1e-9);
        assertEquals(4.0, charged.saturation(), 1e-9);
        assertEquals(18, charged.foodLevel());
        assertEquals(0, charged.expLevel());

        AnalysisCost.Charge leveled = AnalysisCost
                .charge(new AnalysisCost.Stats(19.0, 4.0, 18, 5), payment);
        assertEquals(4, leveled.expLevel());
    }
}
