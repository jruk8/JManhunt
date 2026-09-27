package com.jruk8.jmanhunt.player;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.message.MessageService;

import org.bukkit.entity.Player;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Rolling anti-spawn-camp guard: too many kills by one attacker on the
 * same victim inside the window punishes the camper, either by killing
 * them or by wiping their armor, offhand, and main hand. Every
 * punishment is broadcast server-wide.
 */
public final class SpawnCampService {
    /** Punishment for spawn camping, from settings.server.anti-spawn-camp.punishment. */
    public enum Punishment {
        KILL,
        GEAR_WIPE;

        /** Parses case-insensitively; unknown values fall back to KILL. */
        public static Punishment parse(String raw) {
            if (raw != null && raw.trim().equalsIgnoreCase("GEAR-WIPE")) {
                return GEAR_WIPE;
            }
            return KILL;
        }
    }

    private record KillKey(long matchId, UUID attacker, UUID victim) {
    }

    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final Map<KillKey, Deque<Long>> kills = new HashMap<>();
    private final Set<UUID> quietPunishment = new HashSet<>();
    private final Set<String> warnedRoles = new HashSet<>();
    private final LongSupplier clock;

    public SpawnCampService(JManhuntPlugin plugin, MessageService messages) {
        this(plugin, messages, System::currentTimeMillis);
    }

    SpawnCampService(JManhuntPlugin plugin, MessageService messages, LongSupplier clock) {
        this.plugin = plugin;
        this.messages = messages;
        this.clock = clock;
    }

    /**
     * True when the attacker role is punished under the monitored-roles
     * list. Matching is case-insensitive; unknown values never match,
     * and an empty or missing list punishes nobody. Pure for tests.
     */
    public static boolean isMonitored(Role attackerRole, List<String> monitoredRoles) {
        if (monitoredRoles == null || monitoredRoles.isEmpty()) {
            return false;
        }
        for (String raw : monitoredRoles) {
            if (raw != null && raw.trim().equalsIgnoreCase(attackerRole.name())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records one match kill and punishes the attacker when they cross
     * the configured rolling limit on the same victim. Attackers whose
     * role is not monitored are ignored entirely: no tracking, no
     * warning, no punishment.
     */
    public void handleKill(long matchId, Player attacker, Player victim, Role attackerRole) {
        if (!plugin.configService().getBoolean("settings.server.anti-spawn-camp.enabled", true)) {
            return;
        }
        List<String> monitoredRoles = plugin.configService()
                .getStringList("settings.server.anti-spawn-camp.monitored-roles");
        warnUnknownRoles(monitoredRoles);
        if (!isMonitored(attackerRole, monitoredRoles)) {
            return;
        }
        int limit = plugin.configService().getInt("settings.server.anti-spawn-camp.kills", 3);
        long windowMillis = (long) (plugin.configService()
                .getDouble("settings.server.anti-spawn-camp.window-seconds", 120.0) * 1000.0);
        int count = recordKill(matchId, attacker.getUniqueId(), victim.getUniqueId(), windowMillis);
        if (count < Math.max(1, limit)) {
            if (shouldWarn(count, limit)) {
                messages.message(attacker, "game.spawncamp-warning",
                        Map.of("victim", victim.getName()));
            }
            return;
        }
        Punishment punishment = Punishment.parse(
                plugin.configService().getString("settings.server.anti-spawn-camp.punishment", "KILL"));
        if (punishment == Punishment.GEAR_WIPE) {
            wipeGear(attacker);
            broadcast("game.spawncamp-gear-wipe", attacker, victim, count);
        } else {
            quietKill(attacker);
            broadcast("game.spawncamp-kill", attacker, victim, count);
        }
    }

    /**
     * Kills through the quiet death path: the death below re-enters
     * onDeath synchronously with the tag set, so the listener applies
     * state changes without chatter. try/finally keeps the tag exact
     * even if a totem saves them.
     */
    public void quietKill(Player player) {
        quietPunishment.add(player.getUniqueId());
        try {
            player.setHealth(0.0);
        } finally {
            quietPunishment.remove(player.getUniqueId());
        }
    }

    /** True while the player is dying from spawncamp punishment. */
    public boolean isQuietPunishment(UUID playerId) {
        return quietPunishment.contains(playerId);
    }

    /**
     * Records a kill and returns the attacker's rolling count on the
     * victim inside the window. Exposed for tests.
     */
    int recordKill(long matchId, UUID attacker, UUID victim, long windowMillis) {
        long now = clock.getAsLong();
        Deque<Long> stamps = kills.computeIfAbsent(
                new KillKey(matchId, attacker, victim), key -> new ArrayDeque<>());
        stamps.addLast(now);
        while (!stamps.isEmpty() && now - stamps.peekFirst() > windowMillis) {
            stamps.removeFirst();
        }
        return stamps.size();
    }

    /** Warns once per unknown monitored-roles value, then ignores it. */
    private void warnUnknownRoles(List<String> monitoredRoles) {
        if (monitoredRoles == null) {
            return;
        }
        for (String raw : monitoredRoles) {
            String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (normalized.equals("SPEEDRUNNER") || normalized.equals("HUNTER")
                    || normalized.isEmpty() || !warnedRoles.add(normalized)) {
                continue;
            }
            plugin.getLogger().warning(
                    "Unknown settings.server.anti-spawn-camp.monitored-roles value '"
                            + raw + "': expected SPEEDRUNNER or HUNTER.");
        }
    }

    /**
     * True when the killer has exactly one kill left in the quota: the
     * rolling count sits at limit - 1. Pure for tests.
     */
    static boolean shouldWarn(int count, int limit) {
        return count >= 1 && count == limit - 1;
    }

    /** Drops all tracking for a finished match. */
    public void clearMatch(long matchId) {
        kills.keySet().removeIf(key -> key.matchId() == matchId);
    }

    private void wipeGear(Player player) {
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
        player.getInventory().setItemInMainHand(null);
    }

    private void broadcast(String key, Player attacker, Player victim, int count) {
        messages.broadcast(key, Map.of("player", attacker.getName(),
                "victim", victim.getName(), "count", String.valueOf(count)));
    }
}
