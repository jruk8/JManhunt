package com.jruk8.jmanhunt.compass;

import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;

/** Analysis resolution stamping shared by the lock service timer. */
final class AnalysisResolution {

    private AnalysisResolution() {
    }

    /**
     * Runs SUCCESS costs, stamping the click clock only when they pass:
     * a cost-too-high block never touches the regular cooldown.
     */
    static boolean stampClickOnSuccessfulCost(AnalysisHost host,
            Map<UUID, Long> sharedClicks, Player holder, UUID id) {
        if (!host.trySuccessCost(holder)) {
            host.cancelAnalysisSnapshots(id);
            return false;
        }
        sharedClicks.put(id, System.currentTimeMillis());
        return true;
    }
}
