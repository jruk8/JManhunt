package com.jruk8.jmanhunt.core;

import com.jruk8.jmanhunt.config.EngineStateRepository;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/**
 * Reads and writes the crash flag in the engine database. A set flag
 * means the previous run crashed or still owes crash wipes.
 */
public final class CrashFlagService {
    private final EngineStateRepository engineState;
    private final BooleanSupplier wipesOwed;
    private final JManhuntLogger log;
    private final Logger pluginLog;

    /**
     * @param engineState engine database, null when it failed to open
     * @param wipesOwed true while crash wipes are still owed
     * @param log funneled logger for the enable path
     * @param pluginLog Bukkit logger for the disable path, where the funnel may be null
     */
    public CrashFlagService(EngineStateRepository engineState, BooleanSupplier wipesOwed,
            JManhuntLogger log, Logger pluginLog) {
        this.engineState = engineState;
        this.wipesOwed = wipesOwed;
        this.log = log;
        this.pluginLog = pluginLog;
    }

    /**
     * Reads the crash flag left by the previous run: a set flag means the
     * server crashed or still owes wipes, so stale end reservations are
     * cleared after orphan deletion already consumed them. The flag stays
     * untouched here; the crash cleanup load arms it for this run later.
     */
    public void checkCrashFlag() {
        if (engineState == null) {
            return;
        }
        try {
            if (engineState.getCrashFlag()) {
                log.warning("JManhunt found unwiped crash state from an earlier run; "
                        + "clearing stale match reservations from the engine database.");
                engineState.clearEndReservations();
            }
        } catch (Exception exception) {
            log.warning("Could not check the crash flag: " + exception.getMessage());
        }
    }

    /** Clean-shutdown flag write; stays set while crash wipes are still owed. */
    public void writeCrashFlag() {
        if (engineState == null) {
            return;
        }
        try {
            engineState.setCrashFlag(wipesOwed.getAsBoolean());
        } catch (Exception exception) {
            pluginLog.warning("Could not write the crash flag: " + exception.getMessage());
        }
    }
}
