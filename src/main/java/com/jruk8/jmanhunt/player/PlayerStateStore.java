package com.jruk8.jmanhunt.player;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Owns role and per-match player state, including dimension-aware sightings. */
public final class PlayerStateStore {
    /** Notified after a role change with the previous and new role. */
    public interface RoleChangeListener {
        void onRoleChange(UUID playerId, Role from, Role to);
    }

    private final Map<UUID, Role> roles = new HashMap<>();
    private final List<RoleChangeListener> roleListeners = new ArrayList<>();
    private final Map<UUID, Map<UUID, Location>> lastSeenByWorld = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private final Map<UUID, Boolean> speedrunnerAlive = new HashMap<>();
    private final Map<UUID, Integer> lives = new HashMap<>();

    public Role role(Player player) {
        return roles.getOrDefault(player.getUniqueId(), Role.NONE);
    }

    public Role role(UUID playerId) {
        return roles.getOrDefault(playerId, Role.NONE);
    }

    /** Registers a role-change listener; listeners never fire without one. */
    public void addRoleListener(RoleChangeListener listener) {
        roleListeners.add(listener);
    }

    public void setRole(Player player, Role role) {
        setRole(player.getUniqueId(), role);
    }

    public void setRole(UUID playerId, Role role) {
        Role from = roles.getOrDefault(playerId, Role.NONE);
        roles.put(playerId, role);
        if (from != role) {
            for (RoleChangeListener listener : List.copyOf(roleListeners)) {
                listener.onRoleChange(playerId, from, role);
            }
        }
    }

    /**
     * Resets hunter and speedrunner roles to NONE for exactly the given
     * players. Scoped per instance so ending one match never touches another.
     */
    public int resetRoles(Collection<UUID> playerIds) {
        int reset = 0;
        for (UUID playerId : playerIds) {
            Role role = roles.get(playerId);
            if (role == null || role == Role.NONE || role == Role.AFK) {
                continue;
            }
            setRole(playerId, Role.NONE);
            reset++;
        }
        return reset;
    }

    public void resetOfflinePlayers(Collection<? extends Player> onlinePlayers, Collection<UUID> scope) {
        for (UUID playerId : scope) {
            boolean isOnline = onlinePlayers.stream().anyMatch(p -> p.getUniqueId().equals(playerId));
            if (!isOnline && roles.get(playerId) != Role.AFK) {
                setRole(playerId, Role.NONE);
            }
        }
    }

    /**
     * Drops per-match state (sightings, names, alive flags, lives) for
     * exactly the given players. Roles are untouched.
     */
    public void clearMatchFor(Collection<UUID> playerIds) {
        for (UUID playerId : playerIds) {
            lastSeenByWorld.remove(playerId);
            playerNames.remove(playerId);
            speedrunnerAlive.remove(playerId);
            lives.remove(playerId);
        }
    }

    public void setSpeedrunnerAlive(UUID playerId, boolean alive) {
        speedrunnerAlive.put(playerId, alive);
    }

    public boolean isActiveSpeedrunner(UUID playerId) {
        return speedrunnerAlive.getOrDefault(playerId, false);
    }

    public void setLives(UUID playerId, int lives) {
        this.lives.put(playerId, lives);
    }

    public int getLives(UUID playerId) {
        return lives.getOrDefault(playerId, -1);
    }

    public void decrementLives(UUID playerId) {
        lives.computeIfPresent(playerId, (id, value) -> value - 1);
    }

    public Map<UUID, Map<UUID, Location>> sightings() {
        return lastSeenByWorld;
    }

    /**
     * One last-seen location for the player, preferring the overworld and
     * otherwise the lowest world id, so repeated calls agree. Empty when
     * nothing was ever recorded.
     */
    public Optional<Location> lastSeen(UUID playerId) {
        Map<UUID, Location> byWorld = lastSeenByWorld.get(playerId);
        if (byWorld == null || byWorld.isEmpty()) {
            return Optional.empty();
        }
        return byWorld.entrySet().stream()
                .sorted((left, right) -> {
                    boolean leftNormal = isNormal(left.getValue());
                    boolean rightNormal = isNormal(right.getValue());
                    if (leftNormal != rightNormal) {
                        return leftNormal ? -1 : 1;
                    }
                    return left.getKey().toString().compareTo(right.getKey().toString());
                })
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private static boolean isNormal(Location location) {
        return location != null && location.getWorld() != null
                && location.getWorld().getEnvironment() == World.Environment.NORMAL;
    }

    public void recordLastSeen(Player player, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        playerNames.put(player.getUniqueId(), player.getName());
        lastSeenByWorld.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(location.getWorld().getUID(), location.clone());
    }

    public String playerName(UUID playerId) {
        return playerNames.getOrDefault(playerId, "Unknown");
    }

    public void resetRolesIfAbsent(Player player) {
        roles.putIfAbsent(player.getUniqueId(), Role.NONE);
    }
}
