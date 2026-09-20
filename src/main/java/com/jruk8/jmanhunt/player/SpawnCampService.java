package com.jruk8.jmanhunt.player;

import com.jruk8.jmanhunt.core.JManhuntPlugin;
import com.jruk8.jmanhunt.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Rolling anti-spawn-camp guard: too many kills by one attacker on the
 * same victim inside the window punishes the camper, either by killing
 * them or by wiping their armor, offhand, and main hand. Every
 * punishment is broadcast server-wide.
 */
public final class SpawnCampService {
    /** Punishment for spawn camping, from settings.anti-spawn-camp.punishment. */
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
     * Records one match kill and punishes the attacker when they cross
     * the configured rolling limit on the same victim.
     */
    public void handleKill(long matchId, Player attacker, Player victim) {
        if (!plugin.getConfig().getBoolean("settings.anti-spawn-camp.enabled", true)) {
            return;
        }
        int limit = plugin.getConfig().getInt("settings.anti-spawn-camp.kills", 3);
        long windowMillis = (long) (plugin.getConfig()
                .getDouble("settings.anti-spawn-camp.window-seconds", 120.0) * 1000.0);
        int count = recordKill(matchId, attacker.getUniqueId(), victim.getUniqueId(), windowMillis);
        if (count < Math.max(1, limit)) {
            return;
        }
        Punishment punishment = Punishment.parse(
                plugin.getConfig().getString("settings.anti-spawn-camp.punishment", "KILL"));
        if (punishment == Punishment.GEAR_WIPE) {
            wipeGear(attacker);
            broadcast("game.spawncamp-gear-wipe", attacker, victim, count);
        } else {
            attacker.setHealth(0.0);
            broadcast("game.spawncamp-kill", attacker, victim, count);
        }
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
        Bukkit.broadcast(messages.component(key, Map.of("player", attacker.getName(),
                "victim", victim.getName(), "count", String.valueOf(count))));
    }
}
