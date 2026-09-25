package com.jruk8.jmanhunt.api.events;

import com.jruk8.jmanhunt.api.PlayerRole;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired synchronously when a match ends and a winner is announced. The end
 * delay may still be running ({@code JManhuntApi#isMatchEnding()} returns
 * true); the match is torn down after the configurable delay.
 */
public class JMatchEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final long matchId;
    private final PlayerRole winner;

    public JMatchEndEvent(long matchId, PlayerRole winner) {
        this.matchId = matchId;
        this.winner = winner;
    }

    /** Returns the id of the match that ended. */
    public long getMatchId() {
        return matchId;
    }

    /** Returns the winning role ({@link PlayerRole#HUNTER} or {@link PlayerRole#SPEEDRUNNER}). */
    public PlayerRole getWinner() {
        return winner;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}