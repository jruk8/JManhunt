package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Collects live opponents and last-seen sightings for compass tracking. */
final class CompassTargetService {
    private final PlayerStateStore playerStates;

    CompassTargetService(PlayerStateStore playerStates) {
        this.playerStates = playerStates;
    }

    /**
     * Live opponents of the given role in the same world and match,
     * nearest first.
     */
    List<CompassCandidate> collectOpponents(Player holder, Role targetRole, GameInstance instance) {
        Location origin = holder.getLocation();
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> playerStates.role(p) == targetRole
                        && isTrackableTarget(p.getUniqueId(), targetRole, instance)
                        && p.getGameMode() != GameMode.SPECTATOR
                        && !p.getUniqueId().equals(holder.getUniqueId())
                        && p.getWorld().equals(holder.getWorld()))
                .map(player -> new CompassCandidate(player.getUniqueId(), player.getName(),
                        origin.distance(player.getLocation()),
                        flatDistance(origin, player.getLocation())))
                .sorted(Comparator.comparingDouble(CompassCandidate::distance))
                .toList();
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
                            player == null ? null : player.getGameMode())) {
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
