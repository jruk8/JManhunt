package com.jruk8.jmanhunt.api.events;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired synchronously when a match is cancelled with no winner. Career
 * statistics are not saved. The end delay may still be running
 * ({@code JManhuntApi#isMatchEnding(long)} returns true); the match is torn
 * down after the configurable delay.
 */
public class JMatchCancelEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    /** Returns the id of the match that was cancelled. */
    @Getter
    private final long matchId;

    public JMatchCancelEvent(long matchId) {
        this.matchId = matchId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
