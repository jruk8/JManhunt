package com.jruk8.jmanhunt.compass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnalysisTimingTest {

    @Test
    void shortenedTicksKeepsFractionCeiled() {
        assertEquals(30L, AnalysisTiming.shortenedTicks(100L, 0.3));
        assertEquals(0L, AnalysisTiming.shortenedTicks(100L, 0.0));
        assertEquals(100L, AnalysisTiming.shortenedTicks(100L, 1.0));
    }

    @Test
    void shortenedTicksClampsMultiplierAndRemaining() {
        assertEquals(100L, AnalysisTiming.shortenedTicks(100L, 2.0));
        assertEquals(0L, AnalysisTiming.shortenedTicks(100L, -1.0));
        assertEquals(0L, AnalysisTiming.shortenedTicks(-5L, 0.3));
    }

    @Test
    void locationExitEnteringBanksCut() {
        AnalysisTiming.LocationExit exit =
                AnalysisTiming.locationExitTransition(100L, 0.3, false, true, 0L);

        assertEquals(30L, exit.remaining());
        assertEquals(70L, exit.locCut());
        assertTrue(exit.noLoc());
    }

    @Test
    void locationExitStayingOutKeepsEverything() {
        AnalysisTiming.LocationExit exit =
                AnalysisTiming.locationExitTransition(100L, 0.3, false, false, 0L);

        assertEquals(100L, exit.remaining());
        assertEquals(0L, exit.locCut());
        assertFalse(exit.noLoc());
    }

    @Test
    void locationExitStayingInNeverDoubleCuts() {
        AnalysisTiming.LocationExit exit =
                AnalysisTiming.locationExitTransition(30L, 0.3, true, true, 70L);

        assertEquals(30L, exit.remaining());
        assertEquals(70L, exit.locCut());
        assertTrue(exit.noLoc());
    }

    @Test
    void locationExitRecoveringRestoresExactCut() {
        AnalysisTiming.LocationExit exit =
                AnalysisTiming.locationExitTransition(30L, 0.3, true, false, 70L);

        assertEquals(100L, exit.remaining());
        assertEquals(0L, exit.locCut());
        assertFalse(exit.noLoc());
    }

    @Test
    void locationExitRoundTripIsExact() {
        AnalysisTiming.LocationExit entered =
                AnalysisTiming.locationExitTransition(87L, 0.3, false, true, 0L);
        AnalysisTiming.LocationExit recovered = AnalysisTiming.locationExitTransition(
                entered.remaining(), 0.3, true, false, entered.locCut());

        assertEquals(87L, recovered.remaining());
        assertEquals(0L, recovered.locCut());
        assertFalse(recovered.noLoc());
    }

    @Test
    void locationExitMultiplierOneIsNoOp() {
        AnalysisTiming.LocationExit exit =
                AnalysisTiming.locationExitTransition(100L, 1.0, false, true, 0L);

        assertEquals(100L, exit.remaining());
        assertEquals(0L, exit.locCut());
        assertTrue(exit.noLoc());
    }

    @Test
    void locationExitMultiplierZeroResolvesAtOnce() {
        AnalysisTiming.LocationExit exit =
                AnalysisTiming.locationExitTransition(100L, 0.0, false, true, 0L);

        assertEquals(0L, exit.remaining());
        assertEquals(100L, exit.locCut());
        assertTrue(exit.noLoc());
    }
}
