package com.jruk8.jmanhunt;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks per-match Nether/End entry for custom-modifier triggers.
 *
 * <p>Each player fires the per-player trigger ({@code ON_NETHER_ENTER} /
 * {@code ON_END_ENTER}) at most once per match, while the first player to
 * enter additionally fires the global trigger ({@code ON_FIRST_NETHER_ENTER}
 * / {@code ON_FIRST_END_ENTER}). State resets for every match.
 */
public final class DimensionEnterTracker {
    public enum Dimension { NETHER, END }

    public enum Fire { PER_PLAYER, GLOBAL_FIRST }

    private final Set<UUID> enteredNether = new HashSet<>();
    private final Set<UUID> enteredEnd = new HashSet<>();
    private boolean netherFirstFired;
    private boolean endFirstFired;

    /**
     * Records a dimension entry.
     *
     * @return which trigger families should fire; empty when this player
     *         already entered this dimension during the current match
     */
    public EnumSet<Fire> onEnter(Dimension dimension, UUID player) {
        Set<UUID> entered = dimension == Dimension.NETHER ? enteredNether : enteredEnd;
        if (!entered.add(player)) {
            return EnumSet.noneOf(Fire.class);
        }
        EnumSet<Fire> result = EnumSet.of(Fire.PER_PLAYER);
        if (dimension == Dimension.NETHER) {
            if (!netherFirstFired) {
                netherFirstFired = true;
                result.add(Fire.GLOBAL_FIRST);
            }
        } else if (!endFirstFired) {
            endFirstFired = true;
            result.add(Fire.GLOBAL_FIRST);
        }
        return result;
    }

    /** Clears all per-match state for the next match. */
    public void reset() {
        enteredNether.clear();
        enteredEnd.clear();
        netherFirstFired = false;
        endFirstFired = false;
    }
}
