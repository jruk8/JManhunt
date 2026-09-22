package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the live-match registry: id sequence, instance map, and every lookup
 * over it. GameManager keeps thin delegates so callers never touch the map.
 */
final class MatchStore {
    private final PlayerStateStore playerStates;
    private final Map<Long, GameInstance> instances = new HashMap<>();
    private long matchId;

    MatchStore(PlayerStateStore playerStates) {
        this.playerStates = playerStates;
    }

    /** Hands out the next match id. */
    long nextMatchId() {
        return ++matchId;
    }

    long matchId() {
        return matchId;
    }

    /** Live instances keyed by match id; more than one only with the world engine on. */
    Map<Long, GameInstance> instances() {
        return Map.copyOf(instances);
    }

    boolean isEmpty() {
        return instances.isEmpty();
    }

    /** Looks up a live instance by match id. */
    Optional<GameInstance> instance(long matchId) {
        return Optional.ofNullable(instances.get(matchId));
    }

    void registerInstance(GameInstance instance) {
        instances.put(instance.matchId(), instance);
    }

    /** Removes a match from the registry, returning it when present. */
    Optional<GameInstance> removeInstance(long matchId) {
        return Optional.ofNullable(instances.remove(matchId));
    }

    void clear() {
        instances.clear();
    }

    /** Live instances oldest first. */
    List<GameInstance> liveInstances() {
        return instances.values().stream().sorted(Comparator.comparingLong(GameInstance::matchId)).toList();
    }

    /** The lone live instance, or empty unless exactly one match runs. */
    Optional<GameInstance> singleLiveInstance() {
        List<GameInstance> live = liveInstances();
        return live.size() == 1 ? Optional.of(live.get(0)) : Optional.empty();
    }

    /** The live instance a player actively participates in, if any. */
    Optional<GameInstance> instanceOf(UUID playerId) {
        return instances.values().stream().filter(instance -> instance.isActive(playerId)).findFirst();
    }

    /** Live instance started from a lobby, if that lobby has one running. */
    Optional<GameInstance> instanceForLobby(int lobbyId) {
        return instancesForLobby(lobbyId).stream().findFirst();
    }

    /** Live instances started from one lobby, sublobbies included. */
    List<GameInstance> instancesForLobby(int lobbyId) {
        return instances.values().stream()
                .filter(instance -> instance.originLobbyId() == lobbyId).toList();
    }

    /** True when the player actively participates in any live match. */
    boolean isInLiveInstance(UUID playerId) {
        return instanceOf(playerId).isPresent();
    }

    /** Online active participants of a match. */
    List<Player> onlineParticipants(long matchId) {
        GameInstance instance = instances.get(matchId);
        if (instance == null) {
            return List.of();
        }
        return onlineActivePlayers(instance);
    }

    /** Online active participants of a match. */
    List<Player> onlineActivePlayers(GameInstance instance) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> instance.isActive(player.getUniqueId()))
                .map(player -> (Player) player).toList();
    }

    /** Online players ever assigned to a match, including the eliminated. */
    List<Player> onlineAssignedPlayers(GameInstance instance) {
        Set<UUID> assigned = instance.assignedPlayerIds();
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> assigned.contains(player.getUniqueId()))
                .map(player -> (Player) player).toList();
    }

    boolean isActiveInInstance(long matchId, UUID playerId) {
        GameInstance instance = instances.get(matchId);
        return instance != null && instance.isActive(playerId);
    }

    /** Live speedrunners of a match (active, alive, and holding the role). */
    int activeRunnerCount(GameInstance instance) {
        int count = 0;
        for (UUID playerId : instance.activeIds()) {
            if (playerStates.role(playerId) == Role.SPEEDRUNNER
                    && playerStates.isActiveSpeedrunner(playerId)) {
                count++;
            }
        }
        return count;
    }

    /** Live hunters of a match holding the role. */
    int activeHunterCount(GameInstance instance) {
        int count = 0;
        for (UUID playerId : instance.activeIds()) {
            if (playerStates.role(playerId) == Role.HUNTER) {
                count++;
            }
        }
        return count;
    }
}
