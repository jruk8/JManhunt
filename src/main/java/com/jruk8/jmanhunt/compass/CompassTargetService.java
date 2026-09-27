package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/** Collects live opponents and last-seen sightings for compass tracking. */
final class CompassTargetService {
    private final PlayerStateStore playerStates;
    private final FakeSpectatorService fakes;

    CompassTargetService(PlayerStateStore playerStates, FakeSpectatorService fakes) {
        this.playerStates = playerStates;
        this.fakes = fakes;
    }

    /**
     * Live opponents of the given role in the same world and match,
     * nearest first.
     */
    List<CompassCandidate> collectOpponents(Player holder, Role targetRole, GameInstance instance) {
        Location origin = holder.getLocation();
        return liveTrackable(holder, targetRole, instance)
                .map(player -> new CompassCandidate(player.getUniqueId(), player.getName(),
                        origin.distance(player.getLocation()),
                        flatDistance(origin, player.getLocation())))
                .sorted(Comparator.comparingDouble(CompassCandidate::distance))
                .toList();
    }

    /**
     * Live location snapshots of the nearest trackable players of the
     * given role, capped at the limit. Refresh events are the only
     * callers; the snapshots feed the click cache.
     */
    List<CompassSnapshot> collectSnapshots(Player holder, Role targetRole, GameInstance instance,
            int limit) {
        Location origin = holder.getLocation();
        return liveTrackable(holder, targetRole, instance)
                .sorted(Comparator.comparingDouble(player -> origin.distance(player.getLocation())))
                .limit(Math.max(0, limit))
                .map(player -> new CompassSnapshot(player.getUniqueId(),
                        player.getLocation().clone()))
                .toList();
    }

    /**
     * Trackable player identities of the given role, name-sorted, with
     * no locations attached and no same-world filter. Click paths use
     * this for cycle membership so they never fetch a live position.
     */
    List<CompassIdentity> collectIdentities(Player holder, Role targetRole, GameInstance instance) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> playerStates.role(p) == targetRole
                        && isTrackableTarget(p.getUniqueId(), targetRole, instance)
                        && !fakes.isFakeSpectator(p)
                        && !p.getUniqueId().equals(holder.getUniqueId()))
                .map(player -> new CompassIdentity(player.getUniqueId(), player.getName()))
                .sorted(Comparator.comparing(CompassIdentity::name))
                .toList();
    }

    /** Live, same-world, same-match trackable players of the given role. */
    private Stream<? extends Player> liveTrackable(Player holder, Role targetRole,
            GameInstance instance) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> playerStates.role(p) == targetRole
                        && isTrackableTarget(p.getUniqueId(), targetRole, instance)
                        && !fakes.isFakeSpectator(p)
                        && !p.getUniqueId().equals(holder.getUniqueId())
                        && p.getWorld().equals(holder.getWorld()));
    }

    /**
     * Last-seen locations of trackable opponents in the holder's world,
     * nearest first.
     */
    List<CompassSighting> collectSightings(Player holder, Role targetRole, GameInstance instance) {
        Location origin = holder.getLocation();
        return playerStates.sightings().entrySet().stream()
                .filter(entry -> isTrackableTarget(entry.getKey(), targetRole, instance))
                .filter(entry -> !entry.getKey().equals(holder.getUniqueId()))
                .map(entry -> {
                    Location loc = entry.getValue().get(holder.getWorld().getUID());
                    if (loc == null || loc.getWorld() == null) {
                        return null;
                    }
                    Player player = Bukkit.getPlayer(entry.getKey());
                    // Shared pure helper lives on the facade.
                    if (CompassManager.skipLastSeen(player != null,
                            player != null && fakes.isFakeSpectator(player))) {
                        return null;
                    }
                    String name = player != null
                            ? player.getName() : playerStates.playerName(entry.getKey());
                    return new CompassSighting(entry.getKey(), name, origin.distance(loc));
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(CompassSighting::distance))
                .toList();
    }

    /** Flat X/Z distance between two spots, ignoring Y. Pure for tests. */
    static double flatDistance(Location from, Location to) {
        double dx = from.getX() - to.getX();
        double dz = from.getZ() - to.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean isTrackableTarget(UUID playerId, Role targetRole, GameInstance instance) {
        if (playerStates.role(playerId) != targetRole) {
            return false;
        }
        if (!instance.isActive(playerId)) {
            return false;
        }
        if (targetRole == Role.SPEEDRUNNER) {
            return playerStates.isActiveSpeedrunner(playerId);
        }
        return true;
    }
}
