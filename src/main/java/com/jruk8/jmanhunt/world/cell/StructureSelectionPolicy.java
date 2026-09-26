package com.jruk8.jmanhunt.world.cell;

/**
 * Pure close-to-structure selection policy over one cell fetch. Feed
 * each raw-passing origin in order with its lookup outcome; it answers
 * with the origin to use, or null to keep fetching. The structure
 * lookup itself stays with the caller so tests can fake it.
 */
public final class StructureSelectionPolicy {
    private final int attempts;
    private int used;
    private CellOrigin lastRawPass;

    public StructureSelectionPolicy(int attempts) {
        this.attempts = Math.max(1, attempts);
    }

    /**
     * Records one lookup: a hit uses this origin at once, a miss banks
     * it as the latest fallback and spends one attempt. When the budget
     * runs out, the last raw pass is returned so later fetches never
     * revisit the skipped cells. Returns the origin to use, or null to
     * fetch the next cell.
     */
    public CellOrigin record(CellOrigin origin, boolean hit) {
        if (hit) {
            return origin;
        }
        lastRawPass = origin;
        used++;
        return used >= attempts ? lastRawPass : null;
    }

    /** Latest raw-passing origin, for raw-limit exhaustion. Null when none yet. */
    public CellOrigin lastRawPass() {
        return lastRawPass;
    }
}
