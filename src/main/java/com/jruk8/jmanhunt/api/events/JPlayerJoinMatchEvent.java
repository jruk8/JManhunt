package com.jruk8.jmanhunt.api.events;

import com.jruk8.jmanhunt.api.PlayerRole;
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/**
 * Fired synchronously for each player added to a running match, either
 * through {@code /manhunt game join} or a mid-match role promotion. Fires
 * after the player is activated, so {@code JManhuntApi} already reports
 * their new state.
 */
public class JPlayerJoinMatchEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    /** Returns the id of the match that was joined. */
    @Getter
    private final long matchId;
    /** Returns the id of the player that joined. */
    @Getter
    private final UUID playerId;
    /** Returns the role the player joined with. */
    @Getter
    private final PlayerRole role;

    public JPlayerJoinMatchEvent(long matchId, UUID playerId, PlayerRole role) {
        this.matchId = matchId;
        this.playerId = playerId;
        this.role = role;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
