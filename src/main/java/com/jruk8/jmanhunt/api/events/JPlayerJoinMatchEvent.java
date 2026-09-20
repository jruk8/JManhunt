package com.jruk8.jmanhunt.api.events;

import com.jruk8.jmanhunt.api.PlayerRole;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired synchronously for each player added to a running match, either
 * through {@code /manhunt joingame} or a mid-match role promotion. Fires
 * after the player is activated, so {@code JManhuntApi} already reports
 * their new state.
 */
public class JPlayerJoinMatchEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final long matchId;
    private final UUID playerId;
    private final PlayerRole role;

    public JPlayerJoinMatchEvent(long matchId, UUID playerId, PlayerRole role) {
        this.matchId = matchId;
        this.playerId = playerId;
        this.role = role;
    }

    /** Returns the id of the match that was joined. */
    public long getMatchId() {
        return matchId;
    }

    /** Returns the id of the player that joined. */
    public UUID getPlayerId() {
        return playerId;
    }

    /** Returns the role the player joined with. */
    public PlayerRole getRole() {
        return role;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
