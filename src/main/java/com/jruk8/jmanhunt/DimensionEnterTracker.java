package com.jruk8.jmanhunt;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks per-match Nether/End entry for custom-modifier triggers.
 *
 * <p>Each player fires the per-player trigger ({@code ON_NETHER_ENTER} /
 * {@code ON_END_ENTER}) at most once per match, while the first player to
 * enter additionally fires the global trigger ({@code ON_FIRST_NETHER_ENTER}
 * / {@code ON_FIRST_END_ENTER}). State is keyed by match so concurrent
 * matches never share entries.
 */
public final class DimensionEnterTracker {
    public enum Dimension { NETHER, END }

    public enum Fire { PER_PLAYER, GLOBAL_FIRST }

    private static final class MatchEntries {
        final Set<UUID> enteredNether = new HashSet<>();
        final Set<UUID> enteredEnd = new HashSet<>();
        boolean netherFirstFired;
        boolean endFirstFired;
    }

    private final Map<Long, MatchEntries> matches = new HashMap<>();

    /**
     * Records a dimension entry.
     *
     * @return which trigger families should fire; empty when this player
     *         already entered this dimension during this match
     */
    public EnumSet<Fire> onEnter(Dimension dimension, UUID player, long matchId) {
        MatchEntries entries = matches.computeIfAbsent(matchId, ignored -> new MatchEntries());
        Set<UUID> entered = dimension == Dimension.NETHER ? entries.enteredNether : entries.enteredEnd;
        if (!entered.add(player)) {
            return EnumSet.noneOf(Fire.class);
        }
        EnumSet<Fire> result = EnumSet.of(Fire.PER_PLAYER);
        if (dimension == Dimension.NETHER) {
            if (!entries.netherFirstFired) {
                entries.netherFirstFired = true;
                result.add(Fire.GLOBAL_FIRST);
            }
        } else if (!entries.endFirstFired) {
            entries.endFirstFired = true;
            result.add(Fire.GLOBAL_FIRST);
        }
        return result;
    }

    /** Drops one finished match so entries never leak into later matches. */
    public void dropMatch(long matchId) {
        matches.remove(matchId);
    }
}
