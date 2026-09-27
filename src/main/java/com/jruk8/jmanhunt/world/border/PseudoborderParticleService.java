package com.jruk8.jmanhunt.world.border;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.lifecycle.MatchStore;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.world.cell.CellBounds;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the concurrent-match pseudoborder walls as particles. Each tick,
 * every active match player sees a grid patch on each wall within render
 * radius of their own cell box: the same box the enforcement guard
 * confines, so rendering never moves or resizes anything. Walls thin with
 * distance, pulse on the configured mode, and spread a per-player
 * budget evenly across every visible wall.
 */
public final class PseudoborderParticleService {
    private static final float PARTICLE_SIZE = 1.0f;
    private static final double NETHER_SCALE = 8.0;

    private final JManhuntPlugin plugin;
    private final ConfigService configService;
    private final MatchStore store;
    /** Shared clock: ticks since enable, driving both pulse modes. */
    private long tick;

    public PseudoborderParticleService(JManhuntPlugin plugin, ConfigService configService, MatchStore store) {
        this.plugin = plugin;
        this.configService = configService;
        this.store = store;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        tick++;
        if (store.instances().size() < 2) {
            return;
        }
        WorldEngineConfig engine = WorldEngineConfig.fromConfig(configService);
        if (!engine.enabled() || !engine.worldBorderEnabled()) {
            return;
        }
        PseudoborderConfig particles = PseudoborderConfig.fromConfig(configService);
        if (particles.renderRadius() <= 0.0) {
            return;
        }
        Particle.DustOptions dust = particles.type().usesDustOptions()
                ? new Particle.DustOptions(particleColor(particles), PARTICLE_SIZE)
                : null;
        for (GameInstance instance : store.liveInstances()) {
            if (instance.cellIndex().isEmpty()) {
                continue;
            }
            CellBounds bounds = CellBounds.forCell(instance.cellIndex().getAsLong(),
                    engine.cellSize(), engine.startBorderDiameter(), !instance.begun());
            for (Player player : store.onlineActivePlayers(instance)) {
                if (plugin.fakeSpectators().isFakeSpectator(player)) {
                    continue;
                }
                renderForPlayer(player, bounds, particles, dust);
            }
        }
    }

    private static Color particleColor(PseudoborderConfig particles) {
        Color fallback = BorderParticles.parseHexColor(PseudoborderConfig.DEFAULT_COLOR_HEX, Color.WHITE);
        return BorderParticles.parseHexColor(particles.colorHex(), fallback);
    }

    /**
     * Gathers one player's wall vertices post-thinning and post-dedup,
     * spreads the budget across every visible wall, then applies the
     * pulse gate to the survivors.
     */
    private void renderForPlayer(Player player, CellBounds bounds, PseudoborderConfig particles,
            Particle.DustOptions dust) {
        World world = player.getWorld();
        boolean nether = world.getEnvironment() == World.Environment.NETHER;
        if (!nether && world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        double scale = nether ? NETHER_SCALE : 1.0;
        BorderBox box = BorderBox.forCell(bounds.centerX(), bounds.centerZ(), bounds.halfSize(), scale);
        Location at = player.getLocation();
        List<BorderGrid.BorderVertex> candidates = new ArrayList<>();
        for (BorderPlane plane : BorderPlane.values()) {
            List<BorderGrid.BorderVertex> vertices = BorderGrid.verticesForPlane(plane, box,
                    at.getX(), at.getY(), at.getZ(), particles.particleSpacing(),
                    particles.renderRadius(), world.getMinHeight(), world.getMaxHeight());
            if (vertices.isEmpty()) {
                continue;
            }
            double fraction = BorderParticles.thinningFraction(
                    vertices.get(0).planeDistance(), particles.renderRadius());
            for (BorderGrid.BorderVertex vertex : vertices) {
                if (BorderParticles.hash01(vertex.x(), vertex.y(), vertex.z()) < fraction) {
                    candidates.add(vertex);
                }
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        List<BorderGrid.BorderVertex> shown =
                BorderGrid.spreadBudget(candidates, particles.maxParticlesPerPlayer());
        Vector look = at.getDirection();
        double[] angles = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
        for (BorderGrid.BorderVertex vertex : shown) {
            if (!pulseVisible(vertex, look, angles, particles)) {
                continue;
            }
            if (dust != null) {
                player.spawnParticle(Particle.DUST, vertex.x(), vertex.y(), vertex.z(), 1, dust);
            } else {
                player.spawnParticle(particles.type().particle(),
                        vertex.x(), vertex.y(), vertex.z(), 1);
            }
        }
    }

    /** Pulse gate for one budgeted vertex; INTERVAL angles memoize per wall. */
    private boolean pulseVisible(BorderGrid.BorderVertex vertex, Vector look,
            double[] angles, PseudoborderConfig particles) {
        if (particles.pulseMode() == PulseMode.SINE_WAVE) {
            double phase = BorderParticles.wavePhase(vertex.u(), vertex.v(),
                    particles.waveDirectionAngle(), particles.waveLength());
            return BorderParticles.waveVisible(phase, particles.waveSpeedHz(), tick);
        }
        int plane = vertex.plane().index();
        if (Double.isNaN(angles[plane])) {
            angles[plane] = BorderParticles.viewingAngleDegrees(look.getX(), look.getZ(), vertex.plane());
        }
        return BorderParticles.intervalVisible(tick, particles.intervalSeconds(), angles[plane]);
    }
}
