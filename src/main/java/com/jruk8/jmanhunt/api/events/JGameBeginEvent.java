package com.jruk8.jmanhunt.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired synchronously when the match actually begins: the pre-start window
 * (start-on-speedrunner-damage waiting, start delay) is over and participants
 * are playing. {@code JManhuntApi#hasGameBegun()} returns true from here on.
 */
public class JGameBeginEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final long matchId;

    public JGameBeginEvent(long matchId) {
        this.matchId = matchId;
    }

    /** Returns the id of the match that began. */
    public long getMatchId() {
        return matchId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}