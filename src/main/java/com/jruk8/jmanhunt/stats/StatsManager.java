package com.jruk8.jmanhunt.stats;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.Set;

public final class StatsManager {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final StatisticsRepository repository;
    private final Map<Long, Map<UUID, Stats>> matchStats = new HashMap<>();
    private final Map<UUID, CareerStats> career = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lobbySessions = new ConcurrentHashMap<>();
    private final Set<UUID> careerLoading = ConcurrentHashMap.newKeySet();
    private final Set<UUID> careerLoaded = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<Void>> pendingSaves = ConcurrentHashMap.newKeySet();

    public StatsManager(JManhuntPlugin plugin, MessageService messages, StatisticsRepository repository) {
        this.plugin = plugin;
        this.messages = messages;
        this.repository = repository;
    }

    /** Drops one match's slice so concurrent matches never share numbers. */
    public void clearMatch(long matchId) { matchStats.remove(matchId); }

    public Stats create(String player) {
        Stats stat = new Stats();
        stat.player = player;
        return stat;
    }

    public Stats getOrCreate(long matchId, UUID id) {
        loadCareerAsync(id);
        return matchStats.computeIfAbsent(matchId, ignored -> new HashMap<>())
                .computeIfAbsent(id, ignored -> new Stats());
    }

    /** One player's match slice, or empty when they have no numbers yet. */
    public java.util.Optional<Stats> matchStats(long matchId, UUID id) {
        Map<UUID, Stats> slice = matchStats.get(matchId);
        return slice == null ? java.util.Optional.empty() : java.util.Optional.ofNullable(slice.get(id));
    }

    private void loadCareerAsync(UUID id) {
        if (repository == null || careerLoaded.contains(id) || !careerLoading.add(id)) {
            return;
        }
        career.computeIfAbsent(id, ignored -> new CareerStats());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CareerStats loaded = repository.load(id);
                repository.loadStreaks(id, loaded);
                CareerStats current = career.get(id);
                synchronized (current) {
                    if (current.isEmpty()) {
                        current.copyFrom(loaded);
                    }
                    else {
                        current.add(loaded);
                    }
                }
                careerLoaded.add(id);
            } catch (Exception exception) {
                plugin.logger().warning("Could not load career statistics for " + id + ": " + exception.getMessage());
            } finally {
                careerLoading.remove(id);
            }
        });
    }

    public CareerStats career(UUID id) {
        loadCareerAsync(id);
        return career.computeIfAbsent(id, ignored -> new CareerStats());
    }

    /** Records a career death for the given player (persisted with the next match save). */
    public void recordDeath(UUID id) {
        CareerStats total = career(id);
        synchronized (total) {
            total.deaths++;
            CareerStats delta = new CareerStats();
            delta.player = total.player;
            delta.deaths = 1;
            saveAsync(id, delta);
        }
    }

    public void completeMatch(long matchId, Role winner) {
        long now = System.currentTimeMillis();
        Map<UUID, Stats> slice = matchStats.getOrDefault(matchId, Map.of());
        for (Map.Entry<UUID, Stats> entry : slice.entrySet()) {
            foldMatchStats(entry.getKey(), entry.getValue(), now, winner);
        }
        // The slice stays until teardown clears it so the end-of-match screen,
        // shown during the end delay, still has numbers to display.
    }

    /** Folds one player's match slice into career totals and persists the delta. */
    private void foldMatchStats(UUID playerId, Stats match, long now, Role winner) {
        CareerStats total = career(playerId);
        synchronized (total) {
            total.player = match.player;
            long elapsed = Math.max(0L, now - match.matchStartedAt);
            accumulateRoleTotals(total, match, elapsed, winner);
            total.kills += match.kills;
            total.finalKills += match.finalKills;
            total.damage += match.damage / 2.0;
            if (match.role.isParticipant()) {
                total.sessions++;
                total.currentWinStreak = match.role == winner ? total.currentWinStreak + 1 : 0;
                total.bestWinStreak = Math.max(total.bestWinStreak, total.currentWinStreak);
            }
            CareerStats delta = new CareerStats();
            delta.player = match.player;
            accumulateRoleDelta(delta, match, elapsed, winner);
            delta.kills = match.kills;
            delta.finalKills = match.finalKills;
            delta.damage = match.damage / 2.0;
            if (match.role.isParticipant()) {
                delta.sessions = 1;
            }
            saveAsync(playerId, delta);
            if (match.role.isParticipant()) {
                saveStreaksAsync(playerId, total.currentWinStreak, total.bestWinStreak);
            }
        }
    }

    private void saveStreaksAsync(UUID id, int current, int best) {
        if (repository == null) {
            return;
        }
        CompletableFuture<Void> save = CompletableFuture.runAsync(() -> {
            try {
                repository.updateStreaks(id, current, best);
            } catch (Exception exception) {
                plugin.logger().warning("Could not save win streaks for " + id + ": " + exception.getMessage());
            }
        });
        pendingSaves.add(save);
        save.whenComplete((ignored, exception) -> pendingSaves.remove(save));
    }

    /** Accumulates role-specific career totals for one match slice. */
    private void accumulateRoleTotals(CareerStats total, Stats match, long elapsed, Role winner) {
        if (match.role == Role.HUNTER) {
            total.timeHunter += elapsed;
            total.hunterKills += match.kills;
            total.hunterSessions++;
            if (winner == Role.HUNTER) {
                total.hunterWins++;
                total.wins++;
            }
        } else if (match.role == Role.SPEEDRUNNER) {
            total.timeSpeedrunner += elapsed;
            total.speedrunnerKills += match.kills;
            total.speedrunnerSessions++;
            if (winner == Role.SPEEDRUNNER) {
                total.speedrunnerWins++;
                total.wins++;
            }
        }
    }

    /** Accumulates the role-specific persisted delta for one match slice. */
    private void accumulateRoleDelta(CareerStats delta, Stats match, long elapsed, Role winner) {
        if (match.role == Role.HUNTER) {
            delta.timeHunter = elapsed;
            delta.hunterKills = match.kills;
            delta.hunterSessions = 1;
            if (winner == Role.HUNTER) {
                delta.hunterWins = 1;
            }
        } else if (match.role == Role.SPEEDRUNNER) {
            delta.timeSpeedrunner = elapsed;
            delta.speedrunnerKills = match.kills;
            delta.speedrunnerSessions = 1;
            if (winner == Role.SPEEDRUNNER) {
                delta.speedrunnerWins = 1;
            }
        }
    }

    private void saveAsync(UUID id, CareerStats snapshot) {
        if (repository == null) {
            return;
        }
        CompletableFuture<Void> save = CompletableFuture.runAsync(() -> {
            try {
                repository.increment(id, snapshot);
            } catch (Exception exception) {
                plugin.logger().warning("Could not save career statistics for " + id + ": " + exception.getMessage());
            }
        });
        pendingSaves.add(save);
        save.whenComplete((ignored, exception) -> pendingSaves.remove(save));
    }

    public void flush() {
        pendingSaves.forEach(CompletableFuture::join);
    }

    /** Loads persisted lobby session counts; sums with any already recorded. */
    public void loadLobbySessionsAsync() {
        if (repository == null) {
            return;
        }
        CompletableFuture<Void> load = CompletableFuture.runAsync(() -> {
            try {
                for (Map.Entry<Integer, Integer> entry : repository.loadLobbySessions().entrySet()) {
                    lobbySessions.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            } catch (Exception exception) {
                plugin.logger().warning("Could not load lobby sessions: " + exception.getMessage());
            }
        });
        pendingSaves.add(load);
        load.whenComplete((ignored, exception) -> pendingSaves.remove(load));
    }

    /** Records one started session for a lobby, in memory and in the database. */
    public void recordLobbySession(int lobbyId) {
        lobbySessions.merge(lobbyId, 1, Integer::sum);
        if (repository == null) {
            return;
        }
        CompletableFuture<Void> save = CompletableFuture.runAsync(() -> {
            try {
                repository.incrementLobbySessions(lobbyId);
            } catch (Exception exception) {
                plugin.logger().warning("Could not save lobby sessions for lobby " + lobbyId
                        + ": " + exception.getMessage());
            }
        });
        pendingSaves.add(save);
        save.whenComplete((ignored, exception) -> pendingSaves.remove(save));
    }

    /** Lifetime started sessions for a lobby, or 0 when none are known. */
    public int lifetimeSessions(int lobbyId) {
        return lobbySessions.getOrDefault(lobbyId, 0);
    }

    /** End-screen lines go to the match plus the console, never other matches. */
    public void showStats(long matchId, Collection<? extends Player> recipients) {
        Map<UUID, Stats> slice = matchStats.getOrDefault(matchId, Map.of());
        for (String statistic : plugin.getConfig().getStringList("match.end-statistics")) {
            if (statistic.equalsIgnoreCase("PROGRESSION")) {
                updateProgression(matchId);
            }
            var ranked = slice.values().stream()
                    .sorted(Comparator.comparingDouble((Stats stat) -> stat.value(statistic)).reversed())
                    .filter(stat -> stat.appliesTo(statistic))
                    .filter(stat -> stat.value(statistic) > 0).limit(3).toList();
            if (ranked.isEmpty()) {
                continue;
            }
            String displayName = messages.string("game.stat-names." + statistic, statistic);
            String prefix = messages.string("game.stat-header-prefix", "<#de7766>");
            sendStat(recipients, "game.stat-header", Map.of("stat-prefix", prefix, "stat", displayName));
            for (int i = 0; i < ranked.size(); i++) {
                Stats stat = ranked.get(i);
                String placement = getPlacementName(i);
                var rankColor = messages.string("game.rank-colors." + placement, "&f");
                sendStat(recipients, "game.stat-entry", Map.of("rank-color", rankColor, "rank", String.valueOf(i + 1),
                        "player", stat.player, "value", stat.displayValue(statistic, messages)));
            }
        }
    }

    private String getPlacementName(int index) {
        return switch (index) { case 0 -> "first"; case 1 -> "second"; case 2 -> "third"; default -> "other"; };
    }

    private void sendStat(Collection<? extends Player> recipients, String key, Map<String, String> values) {
        Component rendered = messages.component(key, values);
        for (Player recipient : recipients) {
            recipient.sendMessage(rendered);
        }
        Bukkit.getConsoleSender().sendMessage(rendered);
    }

    private void updateProgression(long matchId) {
        Map<String, String> milestones = new LinkedHashMap<>();
        milestones.put("got_wood", "story/mine_wood"); milestones.put("got_iron", "story/smelt_iron");
        milestones.put("entered_nether", "story/enter_the_nether");
        milestones.put("found_bastion", "nether/find_bastion");
        milestones.put("found_fortress", "nether/find_fortress");
        milestones.put("entered_stronghold", "story/follow_ender_eye");
        milestones.put("entered_end", "story/enter_the_end");
        Map<UUID, Stats> slice = matchStats.getOrDefault(matchId, Map.of());
        for (Stats stat : slice.values()) {
            Player player = Bukkit.getPlayer(stat.uuid);
            if (player == null) {
                continue;
            }
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


}
