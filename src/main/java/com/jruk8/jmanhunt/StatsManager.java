package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class StatsManager {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final Map<UUID, Stats> stats = new HashMap<>();

    public StatsManager(JManhuntPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void clear() { stats.clear(); }

    public Stats create(String player) {
        Stats stat = new Stats();
        stat.player = player;
        return stat;
    }

    public Stats getOrCreate(UUID id) { return stats.computeIfAbsent(id, ignored -> new Stats()); }

    public void showStats(Role winner) {
        for (String statistic : plugin.getConfig().getStringList("end-statistics")) {
            if (statistic.equalsIgnoreCase("PROGRESSION") && winner == Role.SPEEDRUNNER) continue;
            var ranked = stats.values().stream()
                    .sorted(Comparator.comparingInt((Stats stat) -> stat.value(statistic)).reversed())
                    .filter(stat -> stat.value(statistic) > 0).limit(3).toList();
            if (ranked.isEmpty()) continue;
            String displayName = messages.string("game.stat-names." + statistic, statistic);
            broadcast("game.stat-header", Map.of("stat", displayName));
            for (int index = 0; index < ranked.size(); index++) {
                Stats stat = ranked.get(index);
                broadcast("game.stat-entry", Map.of("rank", String.valueOf(index + 1),
                        "player", stat.player, "value", String.valueOf(stat.value(statistic))));
            }
        }
    }

    private void broadcast(String key, Map<String, String> values) {
        Bukkit.broadcast(messages.component(key, values));
    }

    public static final class Stats {
        public String player = "unknown";
        public double damage;
        public int kills;
        public int finalKills;
        public int progression;

        public int value(String statistic) {
            return switch (statistic.toUpperCase(Locale.ROOT)) {
                case "DAMAGE_DEALT" -> (int) damage;
                case "KILLS" -> kills;
                case "FINAL_KILLS" -> finalKills;
                case "PROGRESSION" -> progression;
                default -> 0;
            };
        }
    }
}
