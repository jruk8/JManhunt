package com.jruk8.jmanhunt.world.border;

import com.jruk8.jmanhunt.config.WorldEngineParticles;

/** Pseudoborder wall particles for concurrent matches. */
public record PseudoborderConfig(
        PseudoBorderParticle type,
        int particleSpacing,
        String colorHex,
        double renderRadius,
        PulseMode pulseMode,
        double intervalSeconds,
        double waveDirectionAngle,
        double waveLength,
        double waveSpeedHz,
        int maxParticlesPerPlayer) {

    /** Default wall color: the plugin brand color. */
    public static final String DEFAULT_COLOR_HEX = "#de7766";

    public static PseudoborderConfig fromSettings(WorldEngineParticles settings) {
        return new PseudoborderConfig(
                PseudoBorderParticle.parse(settings.getType()),
                Math.clamp(settings.getParticleSpacing(), 1, 8),
                settings.getColor(),
                Math.max(0.0, settings.getRenderRadius()),
                PulseMode.parse(settings.getPulseMode()),
                Math.clamp(settings.getInterval(), 0.0, 3.0),
                Math.clamp(settings.getWaveDirectionAngle(), 0.0, 360.0),
                Math.clamp(settings.getWaveLength(), 2.0, 64.0),
                Math.clamp(settings.getWaveSpeed(), 0.05, 3.0),
                Math.max(1, settings.getMaxParticlesPerPlayer()));
    }
}
