package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.lobby.MidMatchPolicy;
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
import com.jruk8.jmanhunt.match.GameInstance;

/**
 * Owns the live-match registry: id sequence, instance map, and every lookup
 * over it. GameManager keeps thin delegates so callers never touch the map.
 */
public final class MatchStore {
    private final PlayerStateStore playerStates;
    private final Map<Long, GameInstance> instances = new HashMap<>();
    private long matchId;

    public MatchStore(PlayerStateStore playerStates) {
        this.playerStates = playerStates;
    }

    /** Hands out the next match id. */
    public long nextMatchId() {
        return ++matchId;
    }

    public long matchId() {
        return matchId;
    }

    /** Live instances keyed by match id; more than one only with the world engine on. */
    public Map<Long, GameInstance> instances() {
        return Map.copyOf(instances);
    }

    public boolean isEmpty() {
        return instances.isEmpty();
    }

    /** Looks up a live instance by match id. */
    public Optional<GameInstance> instance(long matchId) {
        return Optional.ofNullable(instances.get(matchId));
    }

    /** Live instance by world-engine cell index. */
    public Optional<GameInstance> instanceByCell(long cellIndex) {
        return instances.values().stream()
                .filter(instance -> instance.cellIndex().isPresent()
                        && instance.cellIndex().getAsLong() == cellIndex)
                .findFirst();
    }

    /**
     * Resolves an instance id typed in a command: a match id first, then a
     * world-engine cell index as an alias. Empty when unparsable or unknown.
     */
    public Optional<GameInstance> resolveInstance(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        long id;
        try {
            id = Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        Optional<GameInstance> byMatch = instance(id);
        return byMatch.isPresent() ? byMatch : instanceByCell(id);
    }

    public void registerInstance(GameInstance instance) {
        instances.put(instance.matchId(), instance);
    }

    /** Removes a match from the registry, returning it when present. */
    public Optional<GameInstance> removeInstance(long matchId) {
        return Optional.ofNullable(instances.remove(matchId));
    }

    /** Live instances oldest first. */
    public List<GameInstance> liveInstances() {
        return instances.values().stream().sorted(Comparator.comparingLong(GameInstance::matchId)).toList();
    }

    /** The lone live instance, or empty unless exactly one match runs. */
    public Optional<GameInstance> singleLiveInstance() {
        List<GameInstance> live = liveInstances();
        return live.size() == 1 ? Optional.of(live.get(0)) : Optional.empty();
    }

    /** The live instance a player actively participates in, if any. */
    public Optional<GameInstance> instanceOf(UUID playerId) {
        return instances.values().stream().filter(instance -> instance.isActive(playerId)).findFirst();
    }

    /** A live instance a player is assigned to but inactive in (an eliminated watcher), if any. */
    public Optional<GameInstance> assignedInstanceOf(UUID playerId) {
        return instances.values().stream().filter(instance -> !instance.isActive(playerId)
                && instance.assignedPlayerIds().contains(playerId)).findFirst();
    }

    /** Origin lobby of one match for override resolution, or null when gone. */
    public Integer lobbyOf(long matchId) {
        return instance(matchId).map(instance -> instance.originLobbyId()).orElse(null);
    }

    /** Origin lobby of a player's match for override resolution, or null outside matches. */
    public Integer lobbyOfPlayer(UUID playerId) {
        return instanceOf(playerId).map(instance -> instance.originLobbyId()).orElse(null);
    }

    /** Live instance started from a lobby, if that lobby has one running. */
    public Optional<GameInstance> instanceForLobby(int lobbyId) {
        return instancesForLobby(lobbyId).stream().findFirst();
    }

    /** Live instances started from one lobby, sublobbies included. */
    public List<GameInstance> instancesForLobby(int lobbyId) {
        return instances.values().stream()
                .filter(instance -> instance.originLobbyId() == lobbyId).toList();
    }

    /**
     * Lowest-numbered live sublobby (the oldest running one); empty when
     * none runs. Parent-hosted matches have no number and never win. Pure
     * for tests.
     */
    public static Optional<GameInstance> oldestSubLobby(java.util.Collection<GameInstance> instances) {
        return instances.stream()
                .filter(instance -> instance.active() && !instance.ending()
                        && instance.subLobby() != null)
                .min(Comparator.comparingInt(instance -> instance.subLobby().subId()));
    }

    /** True when the player actively participates in any live match. */
    public boolean isInLiveInstance(UUID playerId) {
        return instanceOf(playerId).isPresent();
    }

    /**
     * Mid-match join target for one role change: spectators under
     * SUBLOBBY_WITH_SPECTATORS join the oldest running sublobby of
     * their lobby, falling back to the lobby match when no sublobby
     * runs; every other case keeps the lobby match.
     */
    public GameInstance midMatchJoinTarget(MidMatchPolicy policy, int lobbyId, GameInstance live,
            Role role) {
        if (policy == MidMatchPolicy.SUBLOBBY_WITH_SPECTATORS && role == Role.SPECTATOR) {
            return oldestSubLobby(instancesForLobby(lobbyId)).orElse(live);
        }
        return live;
    }

    /** Online active participants of a match. */
    public List<Player> onlineParticipants(long matchId) {
        GameInstance instance = instances.get(matchId);
        if (instance == null) {
            return List.of();
        }
        return onlineActivePlayers(instance);
    }

    /** Online active participants of a match. */
    public List<Player> onlineActivePlayers(GameInstance instance) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> instance.isActive(player.getUniqueId()))
                .map(player -> (Player) player).toList();
    }

    /** Online players ever assigned to a match, including the eliminated. */
    public List<Player> onlineAssignedPlayers(GameInstance instance) {
        Set<UUID> assigned = instance.assignedPlayerIds();
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> assigned.contains(player.getUniqueId()))
                .map(player -> (Player) player).toList();
    }

    /**
     * Online audience for match traffic: assigned members who read in
     * the match (everyone active, plus eliminated and box watchers
     * holding participant or spectator roles). Lobby-returned leavers
     * (deactivated, role NONE) hear nothing more. Cleanup and teleport
     * code keeps using onlineAssignedPlayers.
     */
    public List<Player> onlineMatchAudience(GameInstance instance) {
        Set<UUID> assigned = instance.assignedPlayerIds();
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> assigned.contains(player.getUniqueId())
                        && showsInMatchStatus(instance, player.getUniqueId(),
                                playerStates.role(player)))
                .map(player -> (Player) player).toList();
    }

    /**
     * Online match members for status output: the active plus assigned
     * holders of participant and spectator roles. Lobby-returned
     * leavers (deactivated NONE/AFK) read only in lobby status.
     */
    public List<Player> onlineMatchRoster(GameInstance instance) {
        Set<UUID> assigned = instance.assignedPlayerIds();
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> assigned.contains(player.getUniqueId())
                        && showsInMatchStatus(instance, player.getUniqueId(),
                                playerStates.role(player)))
                .map(player -> (Player) player).toList();
    }

    /**
     * True when an assignee reads in match status: everyone active,
     * plus the eliminated and box watchers holding participant or
     * spectator roles. Pure for tests.
     */
    public static boolean showsInMatchStatus(GameInstance instance, UUID playerId, Role role) {
        return instance.isActive(playerId) || role.isParticipant() || role == Role.SPECTATOR;
    }

    public boolean isActiveInInstance(long matchId, UUID playerId) {
        GameInstance instance = instances.get(matchId);
        return instance != null && instance.isActive(playerId);
    }

    /** Live speedrunners of a match (active, alive, and holding the role). */
    public int activeRunnerCount(GameInstance instance) {
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
    public int activeHunterCount(GameInstance instance) {
        int count = 0;
        for (UUID playerId : instance.activeIds()) {
            if (playerStates.role(playerId) == Role.HUNTER) {
                count++;
            }
        }
        return count;
    }
}
