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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Live-match confinement on a quarter-second tick: the pseudo-border
 * rubber-band for cell players, the spectator travel cap, and the
 * elapsed-time cache refresh.
 */
public final class MatchBorderEnforcer {
    /** Enforcement cadence in ticks: rubber-band, travel cap, elapsed cache. */
    private static final long ENFORCE_PERIOD_TICKS = 5L;

    /** Inside-position snapshot cadence in ticks for the rubber-band. */
    private static final long SNAPSHOT_PERIOD_TICKS = 6L;

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
    private final Map<UUID, Location> lastInside = new HashMap<>();

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
        tasks.runTimer(this::snapshotInsidePositions, SNAPSHOT_PERIOD_TICKS, SNAPSHOT_PERIOD_TICKS);
    }

    /**
     * Confines every match to its cell: players outside their cell snap
     * back to their last recorded inside spot and take border damage
     * past the damage buffer. Escapes with no usable snapshot (or one
     * in another world) fall back to the edge clamp with height
     * unchanged. Spectators bypass it. The End is skipped: end
     * dimensions are assigned one per match, so no sharing needs
     * confining.
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
                Location snapshot = lastInside.get(player.getUniqueId());
                if (snapshot != null && snapshot.getWorld() != null
                        && snapshot.getWorld().equals(location.getWorld())) {
                    player.teleport(snapshot);
                } else {
                    rubberbandToEdge(player, bounds, nether, location);
                }
                if (outside > config.damageBuffer() && config.damageAmount() > 0.0) {
                    // damage.amount stays per-second across cadence changes.
                    player.damage(config.damageAmount() * ENFORCE_PERIOD_TICKS / 20.0);
                }
            }
        }
    }

    /**
     * Records every confined player's current spot while it is still
     * inside the cell, so escapes rubber-band back to real ground
     * instead of a computed clamp. Outside spots are never stored;
     * entries for departed players prune on every pass.
     */
    private void snapshotInsidePositions() {
        WorldEngineConfig config = WorldEngineConfig.fromSettings(engine.engineSettings());
        if (!config.enabled() || !config.worldBorderEnabled()) {
            lastInside.clear();
            return;
        }
        Set<UUID> seen = new HashSet<>();
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
                seen.add(player.getUniqueId());
                boolean nether = player.getWorld().getEnvironment() == World.Environment.NETHER;
                Location location = player.getLocation();
                if (bounds.outsideBy(location.getX(), location.getZ(), nether) <= 0.0) {
                    lastInside.put(player.getUniqueId(), location.clone());
                }
            }
        }
        lastInside.keySet().retainAll(seen);
    }

    /**
     * Clamp fallback for escapes with no usable snapshot: the escaped
     * axes land a quarter block inside the edge with height unchanged,
     * and the landing stores as the new snapshot.
     */
    private void rubberbandToEdge(Player player, CellBounds bounds, boolean nether,
            Location location) {
        double[] inside = bounds.clampInside(location.getX(), location.getZ(), nether,
                RUBBERBAND_MARGIN);
        if (inside == null) {
            return;
        }
        Location landing = new Location(location.getWorld(), inside[0],
                location.getY(), inside[1], location.getYaw(), location.getPitch());
        player.teleport(landing);
        storeSnapshot(player.getUniqueId(), bounds, nether, landing);
    }

    /**
     * Stores one landing after a clamp fallback: the guarded twin of the
     * snapshot pass, so even fallback escapes heal immediately.
     */
    private void storeSnapshot(UUID playerId, CellBounds bounds, boolean nether, Location landing) {
        if (bounds.outsideBy(landing.getX(), landing.getZ(), nether) <= 0.0) {
            lastInside.put(playerId, landing.clone());
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
}
