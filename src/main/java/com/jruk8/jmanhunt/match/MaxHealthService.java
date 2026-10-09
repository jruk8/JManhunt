package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.TagMath;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/**
 * Shared max-health ledger for modifiers: each modifier owns id
 * contributions per player, and the engine owns the applied total
 * (base 20 plus every id). Setters refresh the attribute at once:
 * increases heal by the delta, decreases leave current health alone,
 * and totals at or below 0 eliminate permanently. Direct attribute
 * edits by other modifiers trip the Dirty Max HP Hack warning.
 */
public final class MaxHealthService {

    /** Base max health before any id contribution. */
    public static final double BASE = 20.0;

    /** Float tolerance for the dirty-attribute comparison. */
    private static final double DIRTY_EPSILON = 1e-6;

    /** Elimination reason for the zero-or-less death path. */
    private static final String DEATH_REASON = "their max health reached 0";

    private final GameManager game;
    private final Consumer<String> warn;
    private final Function<Player, AttributeInstance> attributes;
    private final Map<UUID, Map<String, Double>> entries = new HashMap<>();
    private final Map<UUID, Double> appliedCache = new HashMap<>();
    private final Set<UUID> dirtyWarned = new HashSet<>();

    /**
     * @param attributes max-health instance lookup; injected because
     *                 unit tests cannot link the attribute constant
     */
    public MaxHealthService(GameManager game, Consumer<String> warn,
            Function<Player, AttributeInstance> attributes) {
        this.game = game;
        this.warn = warn;
        this.attributes = attributes;
    }

    /** Stateless no-op service for game-less scopes: every op misses. */
    public static MaxHealthService inert() {
        return new MaxHealthService(null, text -> { }, player -> null);
    }

    /** Applied total for one player: base plus every id, base when empty. */
    public double totalFor(UUID playerId) {
        double total = BASE;
        Map<String, Double> owned = entries.get(playerId);
        if (owned != null) {
            for (double contribution : owned.values()) {
                total += contribution;
            }
        }
        return total;
    }

    /** Overwrites one id contribution, then refreshes the attribute. */
    public boolean setContribution(Player player, String id, double amount) {
        if (game == null) {
            return false;
        }
        entries.computeIfAbsent(player.getUniqueId(), uuid -> new HashMap<>())
                .put(id, amount);
        refresh(player);
        return true;
    }

    /** Adds to one id contribution, then refreshes the attribute. */
    public boolean modifyContribution(Player player, String id, double amount) {
        if (game == null) {
            return false;
        }
        Map<String, Double> owned =
                entries.computeIfAbsent(player.getUniqueId(), uuid -> new HashMap<>());
        owned.put(id, owned.getOrDefault(id, 0.0) + amount);
        refresh(player);
        return true;
    }

    /** One id contribution, 0.0 when never set. */
    public double getContribution(UUID playerId, String id) {
        Map<String, Double> owned = entries.get(playerId);
        if (owned == null) {
            return 0.0;
        }
        return owned.getOrDefault(id, 0.0);
    }

    /**
     * Deletes one id contribution, or every id when the id is null,
     * then refreshes the attribute.
     */
    public boolean clearContribution(Player player, String idOrNull) {
        if (game == null) {
            return false;
        }
        if (idOrNull == null) {
            entries.remove(player.getUniqueId());
        } else {
            Map<String, Double> owned = entries.get(player.getUniqueId());
            if (owned != null) {
                owned.remove(idOrNull);
            }
        }
        refresh(player);
        return true;
    }

    /** Drops every record for one player: quit and elimination. */
    public void clearPlayer(UUID playerId) {
        entries.remove(playerId);
        appliedCache.remove(playerId);
        dirtyWarned.remove(playerId);
    }

    /** Drops every record for many players: match end. */
    public void clearPlayers(Collection<UUID> playerIds) {
        for (UUID playerId : playerIds) {
            clearPlayer(playerId);
        }
    }

    /**
     * Recomputes and applies one player's total: warns once on
     * foreign attribute edits, eliminates permanently at zero or
     * less (skipping the write), else writes the total and heals
     * increases by the delta, clamped to the new total.
     */
    private void refresh(Player player) {
        AttributeInstance attribute = attributes.apply(player);
        if (attribute == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        double total = totalFor(playerId);
        double current = attribute.getValue();
        double expected = appliedCache.getOrDefault(playerId, BASE);
        if (Math.abs(current - expected) > DIRTY_EPSILON && !dirtyWarned.contains(playerId)) {
            dirtyWarned.add(playerId);
            warn.accept("Dirty Max HP Hack detected for " + player.getName() + ": max health is "
                    + TagMath.formatNumber(current) + " but the engine last set "
                    + TagMath.formatNumber(expected) + ". Another modifier is editing max "
                    + "health directly; switch it to the pmaxhp tags: "
                    + "https://jruk8.github.io/JManhunt/configuration/modifiers/dirty-max-hp/");
        }
        if (total <= 0) {
            Optional<GameInstance> match = game.instanceOf(playerId);
            if (match.isPresent()) {
                game.eliminateAnyRole(match.get().matchId(), player.getName(), DEATH_REASON);
            }
            clearPlayer(playerId);
            return;
        }
        attribute.setBaseValue(total);
        double delta = total - expected;
        if (delta > 0) {
            player.setHealth(Math.min(player.getHealth() + delta, total));
        }
        appliedCache.put(playerId, total);
    }
}
