package com.jruk8.jmanhunt.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.OptionalLong;

/**
 * Fired synchronously whenever a Manhunt match is created and announced
 * (the pre-start window begins). At this point roles and lives are assigned,
 * but the game has not begun yet. Match participants are available through
 * {@code JManhuntApi#getMatchPlayers(long)}.
 */
public class JMatchStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final long matchId;
    private final int originLobbyId;
    private final OptionalLong cellIndex;

    public JMatchStartEvent(long matchId) {
        this(matchId, -1, OptionalLong.empty());
    }

    /**
     * @param matchId the id of the match that just started
     * @param originLobbyId the lobby the match was started from
     * @param cellIndex the world-engine cell backing the match, or empty
     *                  when the engine is off
     */
    public JMatchStartEvent(long matchId, int originLobbyId, OptionalLong cellIndex) {
        this.matchId = matchId;
        this.originLobbyId = originLobbyId;
        this.cellIndex = cellIndex == null ? OptionalLong.empty() : cellIndex;
    }

    /** Returns the id of the match that just started. */
    public long getMatchId() {
        return matchId;
    }

    /** Returns the lobby the match was started from, or -1 when unknown. */
    public int getOriginLobbyId() {
        return originLobbyId;
    }

    /** Returns the world-engine cell backing the match, or empty when the engine is off. */
    public OptionalLong getCellIndex() {
        return cellIndex;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
