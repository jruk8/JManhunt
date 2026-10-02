package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.lobby.config.CompassSettingsFacade;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.entity.Player;
import java.util.ArrayDeque;
import java.util.Collection;
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
    private final CompassSettingsFacade settings;
    private final PlayerStateStore playerStates;
    private final FakeSpectatorService fakes;
    /** Past positions per player, oldest first, x/z pairs only. */
    private final Map<UUID, ArrayDeque<double[]>> histories = new HashMap<>();
    /** Last sample time per origin lobby, for per-lobby intervals. */
    private final Map<Integer, Long> lastSampleByLobby = new HashMap<>();
    private GameManager game;

    HotspotService(CompassSettingsFacade settings, PlayerStateStore playerStates,
            FakeSpectatorService fakes) {
        this.settings = settings;
        this.playerStates = playerStates;
        this.fakes = fakes;
    }

    /** Wires the game after construction; sampling needs live matches. */
    void setGameManager(GameManager game) {
        this.game = game;
    }

    /**
     * One sampling tick: every live match whose origin lobby is due
     * under its own sample-interval records its online active
     * participants. Skipped players keep their history untouched:
     * nothing is ever pruned here.
     */
    void tick(long nowMillis) {
        if (game == null) {
            return;
        }
        for (GameInstance instance : game.liveInstances()) {
            tickLobby(instance, nowMillis);
        }
    }

    /** Samples one match when its lobby interval is due. */
    private void tickLobby(GameInstance instance, long nowMillis) {
        Integer lobby = instance.originLobbyId();
        if (!settings.hotspotEnabled(lobby) || headstartArmed(instance)) {
            return;
        }
        int interval = SignalInaccuracy.clampSampleInterval(
                settings.hotspotSampleInterval(lobby));
        long last = lastSampleByLobby.getOrDefault(lobby, 0L);
        if (nowMillis - last < (long) interval * 1000L) {
            return;
        }
        lastSampleByLobby.put(lobby, nowMillis);
        int maxPoints = SignalInaccuracy.clampMaxPoints(
                settings.hotspotMaxPoints(lobby));
        for (Player player : game.onlineActivePlayers(instance)) {
            if (!playerStates.role(player).isParticipant()) {
                continue;
            }
            // Dead, fake-spectating, and reviving players sit out
            // without losing history: revives route through fake
            // spectator mode, and vanilla death screens read dead.
            if (player.isDead() || fakes.isFakeSpectator(player)) {
                continue;
            }
            record(player.getUniqueId(), player.getLocation().getX(),
                    player.getLocation().getZ(), maxPoints);
        }
    }

    /** True while either side's headstart hold is still armed. */
    private static boolean headstartArmed(GameInstance instance) {
        return instance.headstart(Role.HUNTER).armed()
                || instance.headstart(Role.SPEEDRUNNER).armed();
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
        if (targetId == null || !settings.hotspotEnabled(lobby)) {
            return 0.0;
        }
        Deque<double[]> history = histories.get(targetId);
        if (history == null || history.isEmpty()) {
            return 0.0;
        }
        double radius = SignalInaccuracy.clampRadius(
                settings.hotspotRadius(lobby));
        int maxPoints = SignalInaccuracy.clampMaxPoints(
                settings.hotspotMaxPoints(lobby));
        double fraction = SignalInaccuracy.clampFraction(
                settings.hotspotFullAccuracyFraction(lobby));
        double maxReduction = SignalInaccuracy.clampMaxReduction(
                settings.hotspotMaxReduction(lobby));
        int inside = 0;
        for (double[] point : history) {
            if (SignalInaccuracy.countsInside(point[0], point[1], x, z, radius)) {
                inside++;
            }
        }
        return SignalInaccuracy.hotspotReduction(inside, maxPoints, fraction, maxReduction);
    }

    /** Drops one player's history: permanent-elimination cleanup. */
    void clear(UUID playerId) {
        histories.remove(playerId);
    }

    /** Drops every listed history: game-end cleanup for one match. */
    void clearAll(Collection<UUID> playerIds) {
        playerIds.forEach(histories::remove);
    }

    /** History size for one player, 0 when unknown. Pure for tests. */
    int historySize(UUID playerId) {
        ArrayDeque<double[]> history = histories.get(playerId);
        return history == null ? 0 : history.size();
    }
}
