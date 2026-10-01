package com.jruk8.jmanhunt.world.border;

import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.lifecycle.MatchStore;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.world.WorldEngineService;
import com.jruk8.jmanhunt.world.cell.CellBounds;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders every match's pseudoborder walls as particles. Each tick,
 * every active match player sees a grid patch on each wall within render
 * radius of their own cell box: the same box the enforcement guard
 * confines, so rendering never moves or resizes anything. Walls show
 * every vertex at any distance, pulse on the configured mode, and
 * spread a per-player budget evenly across every visible wall.
 */
public final class PseudoborderParticleService {
    private static final float PARTICLE_SIZE = 1.0f;
    private static final double NETHER_SCALE = 8.0;

    private final FakeSpectatorService fakeSpectators;
    private final com.jruk8.jmanhunt.config.WorldEngineConfig engineSettings;
    private final MatchStore store;
    private final WorldEngineService worldEngine;
    /** Shared clock: ticks since enable, driving both pulse modes. */
    private long tick;

    public PseudoborderParticleService(TaskScheduler tasks, FakeSpectatorService fakeSpectators,
            com.jruk8.jmanhunt.config.WorldEngineConfig engineSettings, MatchStore store,
            WorldEngineService worldEngine) {
        this.fakeSpectators = fakeSpectators;
        this.engineSettings = engineSettings;
        this.store = store;
        this.worldEngine = worldEngine;
        tasks.runTimer(this::tick, 1L, 1L);
    }

    private void tick() {
        tick++;
        WorldEngineConfig engine = WorldEngineConfig.fromSettings(engineSettings);
        if (!engine.enabled() || !engine.worldBorderEnabled()) {
            return;
        }
        PseudoborderConfig particles = PseudoborderConfig.fromSettings(
                engineSettings.getWorldBorder().getParticles());
        if (particles.renderRadius() <= 0.0) {
            return;
        }
        Color color = particleColor(particles);
        Particle.DustOptions dust = particles.type().usesDustOptions()
                ? new Particle.DustOptions(color, PARTICLE_SIZE)
                : null;
        for (GameInstance instance : store.liveInstances()) {
            if (instance.cellIndex().isEmpty()) {
                continue;
            }
            CellBounds bounds = CellBounds.forCell(instance.cellIndex().getAsLong(),
                    engine.cellSize(), engine.startBorderDiameter(),
                    engine.useStartBorder(instance.begun()));
            for (Player player : store.onlineActivePlayers(instance)) {
                if (fakeSpectators.isFakeSpectator(player)) {
                    continue;
                }
                renderForPlayer(player, bounds, particles, dust, color);
            }
        }
    }

    private static Color particleColor(PseudoborderConfig particles) {
        Color fallback = BorderParticles.parseHexColor(PseudoborderConfig.DEFAULT_COLOR_HEX, Color.WHITE);
        return BorderParticles.parseHexColor(particles.colorHex(), fallback);
    }

    /**
     * Gathers one player's wall vertices, spreads the budget across
     * every visible wall, then applies the pulse gate to the survivors.
     */
    private void renderForPlayer(Player player, CellBounds bounds, PseudoborderConfig particles,
            Particle.DustOptions dust, Color color) {
        World world = player.getWorld();
        if (!worldEngine.isBorderedWorld(world)) {
            return;
        }
        boolean nether = world.getEnvironment() == World.Environment.NETHER;
        double scale = nether ? NETHER_SCALE : 1.0;
        BorderBox box = BorderBox.forCell(bounds.centerX(), bounds.centerZ(), bounds.halfSize(), scale);
        Location at = player.getLocation();
        List<BorderGrid.BorderVertex> candidates = new ArrayList<>();
        for (BorderPlane plane : BorderPlane.values()) {
            List<BorderGrid.BorderVertex> vertices = BorderGrid.verticesForPlane(plane, box,
                    at.getX(), at.getY(), at.getZ(), particles.particleSpacing(),
                    particles.renderRadius(), world.getMinHeight(), world.getMaxHeight());
            candidates.addAll(vertices);
        }
        if (candidates.isEmpty()) {
            return;
        }
        List<BorderGrid.BorderVertex> shown =
                BorderGrid.spreadBudget(candidates, particles.maxParticlesPerPlayer());
        double[] tint = particles.type().colorOffsets()
                ? BorderParticles.colorOffsets(color)
                : null;
        for (BorderGrid.BorderVertex vertex : shown) {
            if (!pulseVisible(vertex, particles)) {
                continue;
            }
            if (dust != null) {
                player.spawnParticle(Particle.DUST, vertex.x(), vertex.y(), vertex.z(), 1, dust);
            } else if (tint != null) {
                player.spawnParticle(particles.type().particle(),
                        vertex.x(), vertex.y(), vertex.z(), 0, tint[0], tint[1], tint[2], 1);
            } else {
                player.spawnParticle(particles.type().particle(),
                        vertex.x(), vertex.y(), vertex.z(), 1);
            }
        }
    }

    /** Pulse gate for one budgeted vertex. */
    private boolean pulseVisible(BorderGrid.BorderVertex vertex, PseudoborderConfig particles) {
        if (particles.pulseMode() == PulseMode.SINE_WAVE) {
            double phase = BorderParticles.wavePhase(vertex.u(), vertex.v(),
                    particles.waveDirectionAngle(), particles.waveLength());
            return BorderParticles.waveVisible(phase, particles.waveSpeedHz(), tick);
        }
        return BorderParticles.intervalVisible(tick, particles.intervalSeconds());
    }
}
