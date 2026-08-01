package com.jruk8.jmanhunt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks disconnect strike counts for speedrunners during a match. */
public final class SpeedrunnerDisconnectTracker {
    public record Decision(int strikes, boolean forfeit) {}
    private record StrikeState(long matchId, int strikes) {}

    private final Map<UUID, StrikeState> strikes = new HashMap<>();

    public Decision registerDisconnect(UUID playerId, long matchId, int maxStrikes) {
        int normalizedMaxStrikes = Math.max(1, maxStrikes);
        StrikeState existing = strikes.get(playerId);
        int nextStrike = existing != null && existing.matchId() == matchId ? existing.strikes() + 1 : 1;
        strikes.put(playerId, new StrikeState(matchId, nextStrike));
        int strikeCount = nextStrike;
        return new Decision(strikeCount, strikeCount >= normalizedMaxStrikes);
    }

    public void clear(UUID playerId) {
        strikes.remove(playerId);
    }
}
