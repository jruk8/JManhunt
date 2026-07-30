package com.jruk8.jmanhunt;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns role and per-match player state, including dimension-aware sightings. */
public final class PlayerStateStore {
    private final Map<UUID, Role> roles = new HashMap<>();
    private final Map<UUID, Map<UUID, Location>> lastSeenByWorld = new HashMap<>();
    private final Map<UUID, Boolean> speedrunnerAlive = new HashMap<>();

    public Role role(Player player) {
        return roles.getOrDefault(player.getUniqueId(), Role.NONE);
    }

    public Role role(UUID playerId) {
        return roles.getOrDefault(playerId, Role.NONE);
    }

    public void setRole(Player player, Role role) {
        roles.put(player.getUniqueId(), role);
    }

    public void setRole(UUID playerId, Role role) {
        roles.put(playerId, role);
    }

    public int resetParticipatingRoles() {
        int reset = 0;
        for (Map.Entry<UUID, Role> entry : roles.entrySet()) {
            if (entry.getValue() == Role.NONE) continue;
            entry.setValue(Role.NONE);
            reset++;
        }
        return reset;
    }

    public void clearMatch() {
        lastSeenByWorld.clear();
        speedrunnerAlive.clear();
    }

    public void setSpeedrunnerAlive(UUID playerId, boolean alive) {
        speedrunnerAlive.put(playerId, alive);
    }

    public boolean isActiveSpeedrunner(UUID playerId) {
        return speedrunnerAlive.getOrDefault(playerId, false);
    }

    public int getActiveSpeedrunnerCount() {
        int count = 0;
        for (boolean alive : speedrunnerAlive.values()) {
            if (alive) count++;
        }
        return count;
    }

    public Map<UUID, Map<UUID, Location>> sightings() {
        return lastSeenByWorld;
    }

    public void recordLastSeen(Player player, Location location) {
        if (location == null || location.getWorld() == null) return;
        lastSeenByWorld.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(location.getWorld().getUID(), location.clone());
    }

    public void resetRolesIfAbsent(Player player) {
        roles.putIfAbsent(player.getUniqueId(), Role.NONE);
    }
}
