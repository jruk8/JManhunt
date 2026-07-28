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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.Set;

public final class StatsManager {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final StatsRepository repository;
    private final Map<UUID, Stats> stats = new HashMap<>();
    private final Map<UUID, CareerStats> career = new ConcurrentHashMap<>();
    private final Set<UUID> careerLoading = ConcurrentHashMap.newKeySet();
    private final Set<UUID> careerLoaded = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<Void>> pendingSaves = ConcurrentHashMap.newKeySet();

    public StatsManager(JManhuntPlugin plugin, MessageService messages, StatsRepository repository) {
        this.plugin = plugin;
        this.messages = messages;
        this.repository = repository;
    }

    public void clear() { stats.clear(); }

    public Stats create(String player) {
        Stats stat = new Stats();
        stat.player = player;
        return stat;
    }

    public Stats getOrCreate(UUID id) {
        loadCareerAsync(id);
        return stats.computeIfAbsent(id, ignored -> new Stats());
    }

    private void loadCareerAsync(UUID id) {
        if (repository == null || careerLoaded.contains(id) || !careerLoading.add(id)) return;
        career.computeIfAbsent(id, ignored -> new CareerStats());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CareerStats loaded = repository.load(id);
                CareerStats current = career.get(id);
                synchronized (current) {
                    if (current.isEmpty()) current.copyFrom(loaded);
                    else current.add(loaded);
                }
                careerLoaded.add(id);
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not load career statistics for " + id + ": " + exception.getMessage());
            } finally {
                careerLoading.remove(id);
            }
        });
    }

    public CareerStats career(UUID id) {
        loadCareerAsync(id);
        return career.computeIfAbsent(id, ignored -> new CareerStats());
    }

    public void completeMatch(Role winner) {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Stats> entry : stats.entrySet()) {
            Stats match = entry.getValue();
            CareerStats total = career(entry.getKey());
            synchronized (total) {
                total.player = match.player;
                long elapsed = Math.max(0L, now - match.matchStartedAt);
                if (match.role == Role.HUNTER) {
                    total.timeHunter += elapsed;
                    total.hunterKills += match.kills;
                    if (winner == Role.HUNTER) total.hunterWins++;
                } else if (match.role == Role.SPEEDRUNNER) {
                    total.timeSpeedrunner += elapsed;
                    total.speedrunnerKills += match.kills;
                    if (winner == Role.SPEEDRUNNER) total.speedrunnerWins++;
                }
                total.kills += match.kills;
                total.finalKills += match.finalKills;
                total.damage += match.damage / 2.0;
                CareerStats delta = new CareerStats();
                delta.player = match.player;
                if (match.role == Role.HUNTER) {
                    delta.timeHunter = elapsed; delta.hunterKills = match.kills;
                    if (winner == Role.HUNTER) delta.hunterWins = 1;
                } else if (match.role == Role.SPEEDRUNNER) {
                    delta.timeSpeedrunner = elapsed; delta.speedrunnerKills = match.kills;
                    if (winner == Role.SPEEDRUNNER) delta.speedrunnerWins = 1;
                }
                delta.kills = match.kills; delta.finalKills = match.finalKills; delta.damage = match.damage / 2.0;
                saveAsync(entry.getKey(), delta);
            }
        }
    }

    private void saveAsync(UUID id, CareerStats snapshot) {
        if (repository == null) return;
        CompletableFuture<Void> save = CompletableFuture.runAsync(() -> {
            try {
                repository.increment(id, snapshot);
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not save career statistics for " + id + ": " + exception.getMessage());
            }
        });
        pendingSaves.add(save);
        save.whenComplete((ignored, exception) -> pendingSaves.remove(save));
    }

    public void flush() {
        pendingSaves.forEach(CompletableFuture::join);
    }

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
            for (int i = 0; i < ranked.size(); i++) {
                Stats stat = ranked.get(i);
                String placement = getPlacementName(i);
                var rankColor = messages.string("game.rank-colors." + placement, "&f");
                broadcast("game.stat-entry", Map.of("rank-color", rankColor, "rank", String.valueOf(i + 1),
                        "player", stat.player, "value", stat.displayValue(statistic, messages)));
            }
        }
    }

    private String getPlacementName(int index) {
        return switch (index) { case 0 -> "first"; case 1 -> "second"; case 2 -> "third"; default -> "default"; };
    }

    private void broadcast(String key, Map<String, String> values) { Bukkit.broadcast(messages.component(key, values)); }

    private void updateProgression() {
        Map<String, String> milestones = new LinkedHashMap<>();
        milestones.put("got_wood", "story/mine_wood"); milestones.put("got_iron", "story/smelt_iron");
        milestones.put("entered_nether", "story/enter_the_nether"); milestones.put("found_bastion", "nether/find_bastion");
        milestones.put("found_fortress", "nether/find_fortress"); milestones.put("entered_stronghold", "story/follow_ender_eye");
        milestones.put("entered_end", "story/enter_the_end");
        for (Stats stat : stats.values()) {
            if (stat.role != Role.SPEEDRUNNER) continue;
            Player player = Bukkit.getPlayer(stat.uuid);
            if (player == null) continue;
            stat.progression = 0; stat.progressionKey = null; int rank = 0;
            for (Map.Entry<String, String> milestone : milestones.entrySet()) {
                rank++;
                var advancement = Bukkit.getAdvancement(new NamespacedKey("minecraft", milestone.getValue()));
                if (advancement != null && player.getAdvancementProgress(advancement).isDone()) {
                    stat.progression = rank; stat.progressionKey = milestone.getKey();
                }
            }
        }
    }

    public static final class CareerStats {
        public String player = "unknown";
        public long timeSpeedrunner;
        public long timeHunter;
        public int kills;
        public int hunterKills;
        public int speedrunnerKills;
        public int finalKills;
        public double damage;
        public int hunterWins;
        public int speedrunnerWins;

        public boolean isEmpty() {
            return timeSpeedrunner == 0 && timeHunter == 0 && kills == 0 && hunterKills == 0
                    && speedrunnerKills == 0 && finalKills == 0 && damage == 0 && hunterWins == 0 && speedrunnerWins == 0;
        }

        public void copyFrom(CareerStats source) {
            player = source.player; timeSpeedrunner = source.timeSpeedrunner; timeHunter = source.timeHunter;
            kills = source.kills; hunterKills = source.hunterKills; speedrunnerKills = source.speedrunnerKills;
            finalKills = source.finalKills; damage = source.damage; hunterWins = source.hunterWins;
            speedrunnerWins = source.speedrunnerWins;
        }

        public void add(CareerStats source) {
            timeSpeedrunner += source.timeSpeedrunner; timeHunter += source.timeHunter; kills += source.kills;
            hunterKills += source.hunterKills; speedrunnerKills += source.speedrunnerKills; finalKills += source.finalKills;
            damage += source.damage; hunterWins += source.hunterWins; speedrunnerWins += source.speedrunnerWins;
        }

        public CareerStats copy() {
            CareerStats copy = new CareerStats(); copy.player = player; copy.timeSpeedrunner = timeSpeedrunner;
            copy.timeHunter = timeHunter; copy.kills = kills; copy.hunterKills = hunterKills;
            copy.speedrunnerKills = speedrunnerKills; copy.finalKills = finalKills; copy.damage = damage;
            copy.hunterWins = hunterWins; copy.speedrunnerWins = speedrunnerWins; return copy;
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
        public long matchStartedAt = System.currentTimeMillis();

        public double value(String statistic) {
            return switch (statistic.toUpperCase(Locale.ROOT)) {
                case "DAMAGE_DEALT" -> damage / 2.0; case "SPEEDRUNNER_KILLS", "KILLS" -> kills;
                case "HUNTER_FINAL_KILLS", "FINAL_KILLS" -> finalKills; case "PROGRESSION" -> progression; default -> 0;
            };
        }

        public boolean appliesTo(String statistic) {
            return switch (statistic.toUpperCase(Locale.ROOT)) {
                case "HUNTER_FINAL_KILLS", "FINAL_KILLS" -> role == Role.HUNTER;
                case "SPEEDRUNNER_KILLS", "KILLS", "PROGRESSION" -> role == Role.SPEEDRUNNER;
                default -> true;
            };
        }

        public String displayValue(String statistic, MessageService messages) {
            if (statistic.equalsIgnoreCase("DAMAGE_DEALT")) return String.format(Locale.ROOT, "%.1f Hearts", value(statistic));
            if (statistic.equalsIgnoreCase("PROGRESSION")) return messages.string("game.progression-names." + progressionKey, progressionKey);
            return String.valueOf((int) value(statistic));
        }
    }
}
