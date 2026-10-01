package com.jruk8.jmanhunt.api.events;

import com.jruk8.jmanhunt.api.PlayerRole;
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired synchronously when a match ends and a winner is announced. The end
 * delay may still be running ({@code JManhuntApi#isMatchEnding()} returns
 * true); the match is torn down after the configurable delay.
 */
public class JMatchEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    /** Returns the id of the match that ended. */
    @Getter
    private final long matchId;
    /** Returns the winning role ({@link PlayerRole#HUNTER} or {@link PlayerRole#SPEEDRUNNER}). */
    @Getter
    private final PlayerRole winner;

    public JMatchEndEvent(long matchId, PlayerRole winner) {
        this.matchId = matchId;
        this.winner = winner;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
