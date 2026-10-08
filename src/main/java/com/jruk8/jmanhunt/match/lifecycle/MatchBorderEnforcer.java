package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.config.PlayerSettings;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.SpectatorTravelService;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.world.WorldEngineService;
import com.jruk8.jmanhunt.world.cell.CellBounds;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Live-match confinement on a quarter-second tick: the pseudo-border
 * rubber-band for cell players, the spectator travel cap, and the
 * elapsed-time cache refresh.
 */
public final class MatchBorderEnforcer {
    /** Enforcement cadence in ticks: rubber-band, travel cap, elapsed cache. */
    private static final long ENFORCE_PERIOD_TICKS = 5L;

    /** Rubber-band landing: this far inside the escaped edge. */
    private static final double RUBBERBAND_MARGIN = 0.25;

    /** Engine settings plus service. */
    public record BorderEngine(com.jruk8.jmanhunt.config.WorldEngineConfig engineSettings,
            WorldEngineService worldEngine) {
    }

    /** Role plus fake-spectator reads. */
    public record BorderPlayers(PlayerStateStore states, FakeSpectatorService fakes) {
    }

    private final TaskScheduler tasks;
    private final BorderEngine engine;
    private final PlayerSettings.Spectator.Travel travel;
    private final MatchStore store;
    private final BorderPlayers players;

    public MatchBorderEnforcer(TaskScheduler tasks, BorderEngine engine,
            PlayerSettings.Spectator.Travel travel, MatchStore store, BorderPlayers players) {
        this.tasks = tasks;
        this.engine = engine;
        this.travel = travel;
        this.store = store;
        this.players = players;
        // Quarter-second tick: pseudo-border guard, spectator travel limit,
        // and the elapsed-time cache (ended matches ignore the refresh).
        tasks.runTimer(() -> {
            enforcePseudoBorders();
            enforceSpectatorTravel();
            long now = System.currentTimeMillis();
            for (GameInstance instance : store.liveInstances()) {
                instance.refreshElapsedCache(now);
            }
        }, ENFORCE_PERIOD_TICKS, ENFORCE_PERIOD_TICKS);
    }

    /**
     * Confines every match to its cell: players outside their cell are
     * rubber-banded back in and take border damage past the damage
     * buffer. Spectators bypass it. The End is skipped: end dimensions
     * are assigned one per match, so no sharing needs confining; if
     * that ever changes, the recovery helper below already gives
     * non-Nether worlds the surface treatment.
     */
    private void enforcePseudoBorders() {
        WorldEngineConfig config = WorldEngineConfig.fromSettings(engine.engineSettings());
        if (!config.enabled() || !config.worldBorderEnabled()) {
            return;
        }
        for (GameInstance instance : store.liveInstances()) {
            if (instance.cellIndex().isEmpty()) {
                continue;
            }
            CellBounds bounds = CellBounds.forCell(instance.cellIndex().getAsLong(),
                    config.cellSize(), config.startBorderDiameter(),
                    config.useStartBorder(instance.begun()));
            for (Player player : store.onlineActivePlayers(instance)) {
                if (players.fakes().isFakeSpectator(player)) {
                    continue;
                }
                if (!engine.worldEngine().isBorderedWorld(player.getWorld())) {
                    continue;
                }
                boolean nether = player.getWorld().getEnvironment() == World.Environment.NETHER;
                Location location = player.getLocation();
                double outside = bounds.outsideBy(location.getX(), location.getZ(), nether);
                if (outside <= 0.0) {
                    continue;
                }
                double[] inside = bounds.clampInside(location.getX(), location.getZ(), nether,
                        RUBBERBAND_MARGIN);
                if (inside != null) {
                    double y = recoveryY(location.getWorld(), inside[0], inside[1], location.getY());
                    player.teleport(new Location(location.getWorld(), inside[0], y, inside[1],
                            location.getYaw(), location.getPitch()));
                }
                if (outside > config.damageBuffer() && config.damageAmount() > 0.0) {
                    // damage.amount stays per-second across cadence changes.
                    player.damage(config.damageAmount() * ENFORCE_PERIOD_TICKS / 20.0);
                }
            }
        }
    }

    /**
     * Pulls roaming watchers back to the nearest player or last-seen
     * spot once they pass the travel cap, so spectators cannot farm
     * chunk generation far from the action. Cell center is the fallback
     * when no anchor exists. Silent: no message, no sound.
     */
    private void enforceSpectatorTravel() {
        if (!travel.isEnabled()) {
            return;
        }
        double maxDistance = travel.getMaxDistance();
        if (maxDistance <= 0.0) {
            return;
        }
        for (GameInstance instance : store.liveInstances()) {
            if (instance.cellIndex().isEmpty()) {
                continue;
            }
            Location center = engine.worldEngine().cellCenter(instance.cellIndex().getAsLong()).orElse(null);
            List<Location> anchors = travelAnchors(instance);
            for (Player player : store.onlineActivePlayers(instance)) {
                Role role = players.states().role(player);
                boolean watching = role == Role.SPECTATOR
                        || (players.fakes().isFakeSpectator(player) && !role.isParticipant());
                if (!watching) {
                    continue;
                }
                SpectatorTravelService.teleportTarget(player.getLocation(), anchors,
                        center, maxDistance).ifPresent(player::teleport);
            }
        }
    }

    /** Online participant spots plus every recorded last-seen spot. */
    private List<Location> travelAnchors(GameInstance instance) {
        List<Location> anchors = new ArrayList<>();
        for (Player player : store.onlineActivePlayers(instance)) {
            if (players.states().role(player).isParticipant()) {
                anchors.add(player.getLocation());
            }
        }
        Map<UUID, Map<UUID, Location>> sightings = players.states().sightings();
        for (UUID playerId : instance.assignedPlayerIds()) {
            if (!players.states().role(playerId).isParticipant()) {
                continue;
            }
            Map<UUID, Location> seen = sightings.get(playerId);
            if (seen != null) {
                anchors.addAll(seen.values());
            }
        }
        return anchors;
    }

    /**
     * Rubberband height at the clamped spot: the highest block plus one
     * for the Overworld and the End, capped below the ceiling; the live
     * Y in the Nether, where the roof would corrupt the lookup. Testable
     * with a stubbed world.
     */
    static double recoveryY(World world, double x, double z, double currentY) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            return currentY;
        }
        int top = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z));
        return Math.min(top + 1.0, world.getMaxHeight() - 2.0);
    }
}
