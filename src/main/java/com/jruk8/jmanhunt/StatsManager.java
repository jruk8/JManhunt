package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
            if (statistic.equalsIgnoreCase("PROGRESSION")) updateProgression();
            var ranked = stats.values().stream()
                    .sorted(Comparator.comparingDouble((Stats stat) -> stat.value(statistic)).reversed())
                    .filter(stat -> stat.appliesTo(statistic))
                    .filter(stat -> stat.value(statistic) > 0).limit(3).toList();
            if (ranked.isEmpty()) continue;
            String displayName = messages.string("game.stat-names." + statistic, statistic);
            broadcast("game.stat-header", Map.of("stat", displayName));
            for (int index = 0; index < ranked.size(); index++) {
                Stats stat = ranked.get(index);
                broadcast("game.stat-entry", Map.of("rank", String.valueOf(index + 1),
                        "player", stat.player, "value", stat.displayValue(statistic, messages)));
            }
        }
    }

    private void broadcast(String key, Map<String, String> values) {
        Bukkit.broadcast(messages.component(key, values));
    }

    private void updateProgression() {
        Map<String, String> milestones = new LinkedHashMap<>();
        milestones.put("got_wood", "story/mine_wood");
        milestones.put("got_iron", "story/smelt_iron");
        milestones.put("entered_nether", "story/enter_the_nether");
        milestones.put("found_bastion", "nether/find_bastion");
        milestones.put("found_fortress", "nether/find_fortress");
        milestones.put("entered_stronghold", "story/follow_ender_eye");
        milestones.put("entered_end", "story/enter_the_end");
        for (Stats stat : stats.values()) {
            if (stat.role != Role.SPEEDRUNNER) continue;
            Player player = Bukkit.getPlayer(stat.uuid);
            if (player == null) continue;
            stat.progression = 0;
            stat.progressionKey = null;
            int rank = 0;
            for (Map.Entry<String, String> milestone : milestones.entrySet()) {
                rank++;
                var advancement = Bukkit.getAdvancement(new NamespacedKey("minecraft", milestone.getValue()));
                if (advancement != null && player.getAdvancementProgress(advancement).isDone()) {
                    stat.progression = rank;
                    stat.progressionKey = milestone.getKey();
                }
            }
        }
    }

    public static final class Stats {
        public String player = "unknown";
        public UUID uuid;
        public Role role = Role.NONE;
        public double damage;
        public int kills;
        public int finalKills;
        public int progression;
        public String progressionKey;

        public double value(String statistic) {
            return switch (statistic.toUpperCase(Locale.ROOT)) {
                case "DAMAGE_DEALT" -> damage / 2.0;
                case "SPEEDRUNNER_KILLS", "KILLS" -> kills;
                case "HUNTER_FINAL_KILLS", "FINAL_KILLS" -> finalKills;
                case "PROGRESSION" -> progression;
                default -> 0;
            };
        }

        public boolean appliesTo(String statistic) {
            return switch (statistic.toUpperCase(Locale.ROOT)) {
                case "HUNTER_FINAL_KILLS", "FINAL_KILLS" -> role == Role.HUNTER;
                case "SPEEDRUNNER_KILLS", "KILLS" -> role == Role.SPEEDRUNNER;
                case "PROGRESSION" -> role == Role.SPEEDRUNNER;
                default -> true;
            };
        }

        public String displayValue(String statistic, MessageService messages) {
            if (statistic.equalsIgnoreCase("DAMAGE_DEALT"))
                return String.format(Locale.ROOT, "%.1f Hearts", value(statistic));
            if (statistic.equalsIgnoreCase("PROGRESSION"))
                return messages.string("game.progression-names." + progressionKey, progressionKey);
            return String.valueOf((int) value(statistic));
        }
    }
}
