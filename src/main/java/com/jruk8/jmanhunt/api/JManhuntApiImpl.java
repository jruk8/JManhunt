package com.jruk8.jmanhunt.api;

import com.jruk8.jmanhunt.GameInstance;
import com.jruk8.jmanhunt.GameManager;
import com.jruk8.jmanhunt.LobbyService;
import com.jruk8.jmanhunt.PlayerStateStore;
import com.jruk8.jmanhunt.Role;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Default {@link JManhuntApi} implementation backed by the live
 * {@link GameManager}, {@link PlayerStateStore}, and {@link LobbyService}.
 * Registered into the server's {@code ServicesManager} by JManhunt on enable.
 */
public final class JManhuntApiImpl implements JManhuntApi {
    private final GameManager game;
    private final PlayerStateStore playerStates;
    private final LobbyService lobbies;

    public JManhuntApiImpl(GameManager game, PlayerStateStore playerStates, LobbyService lobbies) {
        this.game = Objects.requireNonNull(game, "game");
        this.playerStates = Objects.requireNonNull(playerStates, "playerStates");
        this.lobbies = Objects.requireNonNull(lobbies, "lobbies");
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
    public List<Long> getLiveMatchIds() {
        return game.liveInstances().stream().map(GameInstance::matchId).toList();
    }

    @Override
    public boolean isMatchLive(long matchId) {
        return game.instance(matchId).isPresent();
    }

    @Override
    public boolean hasMatchBegun(long matchId) {
        return game.instance(matchId).map(GameInstance::begun).orElse(false);
    }

    @Override
    public boolean isMatchEnding(long matchId) {
        return game.instance(matchId).map(GameInstance::ending).orElse(false);
    }

    @Override
    public long getMatchId(UUID playerId) {
        if (playerId == null) {
            return -1L;
        }
        return game.instanceOf(playerId).map(GameInstance::matchId).orElse(-1L);
    }

    @Override
    public Set<UUID> getMatchPlayers(long matchId) {
        return game.instance(matchId).map(instance -> Set.copyOf(instance.activeIds())).orElse(Set.of());
    }

    @Override
    public int getOriginLobbyId(long matchId) {
        return game.instance(matchId).map(GameInstance::originLobbyId).orElse(-1);
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

    @Override
    public Set<Integer> getLobbyIds() {
        return lobbies.lobbyIds();
    }

    @Override
    public Set<UUID> getLobbyPlayers(int lobbyId) {
        return lobbies.get(lobbyId).map(lobby -> Set.copyOf(lobby.memberIds())).orElse(Set.of());
    }

    @Override
    public int getPlayerLobbyId(UUID playerId) {
        if (playerId == null) {
            return -1;
        }
        return lobbies.lobbyOf(playerId).map(lobby -> lobby.id()).orElse(-1);
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
