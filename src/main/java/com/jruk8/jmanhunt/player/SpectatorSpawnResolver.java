package com.jruk8.jmanhunt.player;

import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.stats.ProgressionRank;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Where a watching spectator lands: the online speedrunner with the
 * highest progression, else the top hunter, else the last-seen
 * speedrunner spot, else the last-seen hunter spot, else the cell
 * center. Match start, mid-match joins, and the toolbar teleporter all
 * share this order.
 */
public final class SpectatorSpawnResolver {

    /** One spawn hopeful: online spot, last-seen spot, or neither. */
    public record SpawnCandidate(String name, Role role, boolean onlineValid,
            int progression, Location onlineLocation, Location lastSeen) {
    }

    private final PlayerStateStore playerStates;
    private final FakeSpectatorService fakes;

    public SpectatorSpawnResolver(PlayerStateStore playerStates, FakeSpectatorService fakes) {
        this.playerStates = playerStates;
        this.fakes = fakes;
    }

    /**
     * Picks the spawn in priority order. Pure for tests. The cell center
     * may be null, in which case an empty pick means "do not teleport".
     */
    public static Optional<Location> resolve(List<SpawnCandidate> candidates, Location cellCenter) {
        Optional<Location> online = pickOnline(candidates);
        if (online.isPresent()) {
            return online;
        }
        Optional<Location> seen = pickLastSeen(candidates);
        if (seen.isPresent()) {
            return seen;
        }
        return Optional.ofNullable(cellCenter);
    }

    private static Optional<Location> pickOnline(List<SpawnCandidate> candidates) {
        Comparator<SpawnCandidate> order = Comparator
                .comparingInt(SpawnCandidate::progression).reversed()
                .thenComparing(SpawnCandidate::name, String.CASE_INSENSITIVE_ORDER);
        Optional<Location> runner = candidates.stream()
                .filter(candidate -> candidate.onlineValid() && candidate.role() == Role.SPEEDRUNNER)
                .sorted(order)
                .map(SpawnCandidate::onlineLocation)
                .filter(location -> location != null)
                .findFirst();
        if (runner.isPresent()) {
            return runner;
        }
        return candidates.stream()
                .filter(candidate -> candidate.onlineValid() && candidate.role() == Role.HUNTER)
                .sorted(order)
                .map(SpawnCandidate::onlineLocation)
                .filter(location -> location != null)
                .findFirst();
    }

    private static Optional<Location> pickLastSeen(List<SpawnCandidate> candidates) {
        Comparator<SpawnCandidate> order =
                Comparator.comparing(SpawnCandidate::name, String.CASE_INSENSITIVE_ORDER);
        Optional<Location> runner = candidates.stream()
                .filter(candidate -> !candidate.onlineValid() && candidate.role() == Role.SPEEDRUNNER
                        && candidate.lastSeen() != null)
                .sorted(order)
                .map(SpawnCandidate::lastSeen)
                .findFirst();
        if (runner.isPresent()) {
            return runner;
        }
        return candidates.stream()
                .filter(candidate -> !candidate.onlineValid() && candidate.role() == Role.HUNTER
                        && candidate.lastSeen() != null)
                .sorted(order)
                .map(SpawnCandidate::lastSeen)
                .findFirst();
    }

    /** Candidates from live instance assignees, online and offline. */
    public List<SpawnCandidate> candidatesOf(GameInstance instance) {
        List<SpawnCandidate> candidates = new ArrayList<>();
        for (UUID playerId : instance.assignedPlayerIds()) {
            Role role = playerStates.role(playerId);
            if (!role.isParticipant()) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            boolean online = player != null;
            boolean active = instance.isActive(playerId);
            boolean fake = online && fakes.isFakeSpectator(player);
            boolean runnerAlive = role != Role.SPEEDRUNNER
                    || playerStates.isActiveSpeedrunner(playerId);
            boolean valid = online && active && !fake && runnerAlive;
            int progression = online ? ProgressionRank.of(player).value() : 0;
            Location at = online ? player.getLocation() : null;
            Location seen = playerStates.lastSeen(playerId).orElse(null);
            candidates.add(new SpawnCandidate(playerStates.playerName(playerId), role,
                    valid, progression, at, seen));
        }
        return candidates;
    }

    /** Candidates from an online pool, before the instance exists. */
    public List<SpawnCandidate> candidatesOfPool(List<Player> pool) {
        List<SpawnCandidate> candidates = new ArrayList<>();
        for (Player player : pool) {
            UUID playerId = player.getUniqueId();
            Role role = playerStates.role(player);
            if (!role.isParticipant()) {
                continue;
            }
            boolean runnerAlive = role != Role.SPEEDRUNNER
                    || playerStates.isActiveSpeedrunner(playerId);
            boolean valid = !fakes.isFakeSpectator(player) && runnerAlive;
            candidates.add(new SpawnCandidate(player.getName(), role, valid,
                    ProgressionRank.of(player).value(), player.getLocation(),
                    playerStates.lastSeen(playerId).orElse(null)));
        }
        return candidates;
    }
}
