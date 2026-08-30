package com.jruk8.jmanhunt.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired synchronously whenever a Manhunt match is created and announced
 * (the pre-start window begins). At this point roles and lives are assigned,
 * but the game has not begun yet. Match participants are available through
 * {@code JManhuntApi#getRole(UUID)}.
 */
public class JMatchStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final long matchId;

    public JMatchStartEvent(long matchId) {
        this.matchId = matchId;
    }

    /** Returns the id of the match that just started. */
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