package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.StatValues;
import com.jruk8.jmanhunt.command.TagMath;
import com.jruk8.jmanhunt.match.lifecycle.MatchStore;
import com.jruk8.jmanhunt.stats.Stats;
import com.jruk8.jmanhunt.stats.StatsManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Bukkit-backed stat values for one tag run, bound to its match id.
 * Vitals read the live player, counters read the match slice of any
 * assigned player (eliminated ones keep their numbers), duration
 * reads the match clock, and daytime reads the main world clock.
 */
public final class MatchStatValues implements StatValues {

    private final StatsManager stats;
    private final MatchStore store;
    private final long matchId;

    public MatchStatValues(StatsManager stats, MatchStore store, long matchId) {
        this.stats = stats;
        this.store = store;
        this.matchId = matchId;
    }

    @Override
    public Optional<String> player(String playerName, String key) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            return Optional.empty();
        }
        return switch (key) {
            case "health" -> Optional.of(TagMath.formatNumber(player.getHealth()));
            case "hunger" -> Optional.of(Integer.toString(player.getFoodLevel()));
            case "mobs-killed", "achievements-gained" ->
                    Optional.of(Integer.toString(counter(player.getUniqueId(), key)));
            default -> Optional.empty();
        };
    }

    @Override
    public Optional<String> global(String key) {
        return switch (key) {
            case "duration" -> duration();
            case "daytime" -> daytime();
            default -> Optional.empty();
        };
    }

    /** Match slice counter, or 0 without a slice or a match. */
    private int counter(UUID playerId, String key) {
        Optional<GameInstance> match = store.instances().values().stream()
                .filter(instance -> instance.assignedPlayerIds().contains(playerId))
                .findFirst();
        if (match.isEmpty()) {
            return 0;
        }
        Optional<Stats> slice = stats.matchStats(match.get().matchId(), playerId);
        if (slice.isEmpty()) {
            return 0;
        }
        return key.equals("mobs-killed") ? slice.get().mobsKilled : slice.get().achievementsGained;
    }

    /** Whole elapsed gameplay seconds, or empty without the match. */
    private Optional<String> duration() {
        return store.instance(matchId)
                .map(instance -> Long.toString(Math.max(0L,
                        (System.currentTimeMillis() - instance.startedAtMillis()) / 1000L)));
    }

    /** Main world clock in ticks, or empty with no worlds loaded. */
    private Optional<String> daytime() {
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? Optional.empty() : Optional.of(Long.toString(worlds.get(0).getTime()));
    }
}
