package com.jruk8.jmanhunt.compass;

import org.bukkit.entity.Player;
import java.util.UUID;

/**
 * Analysis sampling hooks hosted by the facade: movement sampling,
 * doom and location checks, main-hand checks, snapshot cleanup, and
 * success costs. The lock service owns the timer; the facade owns the
 * snapshots.
 */
interface AnalysisHost {

    /** Folds one movement sample into the holder's running maximum. */
    void sampleAnalysisMovement(Player holder);

    /**
     * True when the holder's in-flight analysis is already doomed:
     * failing interference, or every available target inside the min
     * gate or past the max gate.
     */
    boolean analysisDoomed(Player holder);

    /** True when the holder's doom-check pick has no location at all. */
    boolean noLocationAvailable(Player holder);

    /** True when the holder carries a compass in their main hand. */
    boolean isMainhandCompass(Player holder);

    /** Drops the holder's press-time snapshots and movement maximum. */
    void cancelAnalysisSnapshots(UUID holderId);

    /**
     * Charges the SUCCESS cost when configured. False when the holder
     * cannot pay and cancel-when-poor aborts the resolution.
     */
    boolean trySuccessCost(Player holder);
}
