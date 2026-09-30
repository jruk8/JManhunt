package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hotspot histories for signal inaccuracy: records where live
 * participants linger and reports how much the error shrinks for
 * idling targets. Histories hold x/z pairs only; membership tests use
 * squared distances with the radius squared at call time.
 */
final class HotspotService {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    /** Past positions per player, oldest first, x/z pairs only. */
    private final Map<UUID, ArrayDeque<double[]>> histories = new HashMap<>();
    /** Last sample time per origin lobby, for per-lobby intervals. */
    private final Map<Integer, Long> lastSampleByLobby = new HashMap<>();
    private GameManager game;

    HotspotService(JManhuntPlugin plugin, PlayerStateStore playerStates) {
        this.plugin = plugin;
        this.playerStates = playerStates;
    }

    /** Wires the game after construction; sampling needs live matches. */
    void setGameManager(GameManager game) {
        this.game = game;
    }

    /**
     * One sampling tick: every live match whose origin lobby is due
     * under its own sample-interval records its online active
     * participants. Offline histories are pruned first.
     */
    void tick(long nowMillis) {
        pruneOffline();
        if (game == null) {
            return;
        }
        for (GameInstance instance : game.liveInstances()) {
            tickLobby(instance, nowMillis);
        }
    }

    /** Samples one match when its lobby interval is due. */
    private void tickLobby(GameInstance instance, long nowMillis) {
        String base = "settings.compass.signal.inaccuracy.accuracy-hotspot.";
        var overrides = plugin.overrides();
        Integer lobby = instance.originLobbyId();
        if (!overrides.getBoolean(lobby, base + "enabled", false)) {
            return;
        }
        int interval = SignalInaccuracy.clampSampleInterval(
                overrides.getInt(lobby, base + "sample-interval", 10));
        long last = lastSampleByLobby.getOrDefault(lobby, 0L);
        if (nowMillis - last < (long) interval * 1000L) {
            return;
        }
        lastSampleByLobby.put(lobby, nowMillis);
        int maxPoints = SignalInaccuracy.clampMaxPoints(
                overrides.getInt(lobby, base + "max-points", 40));
        for (Player player : game.onlineActivePlayers(instance)) {
            if (!playerStates.role(player).isParticipant()) {
                continue;
            }
            record(player.getUniqueId(), player.getLocation().getX(),
                    player.getLocation().getZ(), maxPoints);
        }
    }

    /**
     * Appends one x/z sample, trimming the history to maxPoints (which
     * also shrinks histories when the cap drops at runtime).
     */
    void record(UUID playerId, double x, double z, int maxPoints) {
        ArrayDeque<double[]> history =
                histories.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        history.addLast(new double[] {x, z});
        int cap = Math.max(1, maxPoints);
        while (history.size() > cap) {
            history.pollFirst();
        }
    }

    /**
     * Error reduction for a target at its live spot: the inside share
     * of its history against this lobby's hotspot settings, or 0 when
     * the feature is off or the target has no history.
     */
    double reductionFor(UUID targetId, double x, double z, Integer lobby) {
        String base = "settings.compass.signal.inaccuracy.accuracy-hotspot.";
        var overrides = plugin.overrides();
        if (targetId == null || !overrides.getBoolean(lobby, base + "enabled", false)) {
            return 0.0;
        }
        Deque<double[]> history = histories.get(targetId);
        if (history == null || history.isEmpty()) {
            return 0.0;
        }
        double radius = SignalInaccuracy.clampRadius(
                overrides.getDouble(lobby, base + "hotspot-radius", 50.0));
        int maxPoints = SignalInaccuracy.clampMaxPoints(
                overrides.getInt(lobby, base + "max-points", 40));
        double fraction = SignalInaccuracy.clampFraction(
                overrides.getDouble(lobby, base + "full-accuracy-fraction", 0.5));
        double maxReduction = SignalInaccuracy.clampMaxReduction(
                overrides.getDouble(lobby, base + "max-reduction", 0.9));
        int inside = 0;
        for (double[] point : history) {
            if (SignalInaccuracy.countsInside(point[0], point[1], x, z, radius)) {
                inside++;
            }
        }
        return SignalInaccuracy.hotspotReduction(inside, maxPoints, fraction, maxReduction);
    }

    /** Drops one player's history: leave and death cleanup. */
    void clear(UUID playerId) {
        histories.remove(playerId);
    }

    /** History size for one player, 0 when unknown. Pure for tests. */
    int historySize(UUID playerId) {
        ArrayDeque<double[]> history = histories.get(playerId);
        return history == null ? 0 : history.size();
    }

    /** Drops histories of offline players. */
    void pruneOffline() {
        histories.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }
}
