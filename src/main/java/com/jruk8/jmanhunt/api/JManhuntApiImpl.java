package com.jruk8.jmanhunt.api;

import com.jruk8.jmanhunt.GameManager;
import com.jruk8.jmanhunt.PlayerStateStore;
import com.jruk8.jmanhunt.Role;

import java.util.Objects;
import java.util.UUID;

/**
 * Default {@link JManhuntApi} implementation backed by the live
 * {@link GameManager} and {@link PlayerStateStore}. Registered into the
 * server's {@code ServicesManager} by JManhunt on enable.
 */
public final class JManhuntApiImpl implements JManhuntApi {
    private final GameManager game;
    private final PlayerStateStore playerStates;

    public JManhuntApiImpl(GameManager game, PlayerStateStore playerStates) {
        this.game = Objects.requireNonNull(game, "game");
        this.playerStates = Objects.requireNonNull(playerStates, "playerStates");
    }

    @Override
    public boolean isMatchActive() {
        return game.isActive();
    }

    @Override
    public boolean hasGameBegun() {
        return game.isGameBegun();
    }

    @Override
    public boolean isMatchEnding() {
        return game.isEnding();
    }

    @Override
    public long getMatchId() {
        return game.matchId();
    }

    @Override
    public PlayerRole getRole(UUID playerId) {
        if (playerId == null) {
            return PlayerRole.NONE;
        }
        return fromInternal(playerStates.role(playerId));
    }

    @Override
    public boolean isParticipant(UUID playerId) {
        return getRole(playerId).isParticipant();
    }

    private static PlayerRole fromInternal(Role role) {
        if (role == null) {
            return PlayerRole.NONE;
        }
        return switch (role) {
            case HUNTER -> PlayerRole.HUNTER;
            case SPEEDRUNNER -> PlayerRole.SPEEDRUNNER;
            case AFK -> PlayerRole.AFK;
            case NONE -> PlayerRole.NONE;
        };
    }
}