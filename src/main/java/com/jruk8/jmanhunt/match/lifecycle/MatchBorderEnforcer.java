package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.SpectatorTravelService;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.world.WorldEngineService;
import com.jruk8.jmanhunt.world.cell.CellBounds;
import org.bukkit.Bukkit;
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

    private final JManhuntPlugin plugin;
    private final ConfigService configService;
    private final MatchStore store;
    private final WorldEngineService worldEngine;
    private final PlayerStateStore playerStates;

    public MatchBorderEnforcer(JManhuntPlugin plugin, ConfigService configService, MatchStore store,
            WorldEngineService worldEngine, PlayerStateStore playerStates) {
        this.plugin = plugin;
        this.configService = configService;
        this.store = store;
        this.worldEngine = worldEngine;
        this.playerStates = playerStates;
        // Quarter-second tick: pseudo-border guard, spectator travel limit,
        // and the elapsed-time cache (ended matches ignore the refresh).
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
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
        WorldEngineConfig config = WorldEngineConfig.fromConfig(configService);
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
                if (plugin.fakeSpectators().isFakeSpectator(player)) {
                    continue;
                }
                if (!worldEngine.isBorderedWorld(player.getWorld())) {
                    continue;
                }
                boolean nether = player.getWorld().getEnvironment() == World.Environment.NETHER;
                Location location = player.getLocation();
                double outside = bounds.outsideBy(location.getX(), location.getZ(), nether);
                if (outside <= 0.0) {
                    continue;
                }
                double[] inside = bounds.clampInside(location.getX(), location.getZ(), nether, 2.0);
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
        if (!configService.getBoolean("settings.players.spectator.travel.enabled", true)) {
            return;
        }
        double maxDistance = configService.getDouble(
                "settings.players.spectator.travel.max-distance", 125.0);
        if (maxDistance <= 0.0) {
            return;
        }
        for (GameInstance instance : store.liveInstances()) {
            if (instance.cellIndex().isEmpty()) {
                continue;
            }
            Location center = worldEngine.cellCenter(instance.cellIndex().getAsLong()).orElse(null);
            List<Location> anchors = travelAnchors(instance);
            for (Player player : store.onlineActivePlayers(instance)) {
                Role role = playerStates.role(player);
                boolean watching = role == Role.SPECTATOR
                        || (plugin.fakeSpectators().isFakeSpectator(player) && !role.isParticipant());
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
            if (playerStates.role(player).isParticipant()) {
                anchors.add(player.getLocation());
            }
        }
        Map<UUID, Map<UUID, Location>> sightings = playerStates.sightings();
        for (UUID playerId : instance.assignedPlayerIds()) {
            if (!playerStates.role(playerId).isParticipant()) {
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
