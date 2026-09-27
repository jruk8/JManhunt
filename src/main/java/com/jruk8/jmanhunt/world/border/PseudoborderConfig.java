package com.jruk8.jmanhunt.world.border;

import com.jruk8.jmanhunt.config.ConfigService;

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

    public static PseudoborderConfig fromConfig(ConfigService config) {
        String base = "world-engine.world-border.particles.";
        return new PseudoborderConfig(
                PseudoBorderParticle.parse(config.getString(base + "type", "DUST")),
                Math.clamp(config.getInt(base + "particle-spacing", 2), 1, 8),
                config.getString(base + "color", DEFAULT_COLOR_HEX),
                Math.max(0.0, config.getDouble(base + "render-radius", 10.0)),
                PulseMode.parse(config.getString(base + "pulse-mode", "INTERVAL")),
                Math.clamp(config.getDouble(base + "interval", 0.5), 0.0, 3.0),
                Math.clamp(config.getDouble(base + "wave-direction-angle", 0.0), 0.0, 360.0),
                Math.clamp(config.getDouble(base + "wave-length", 8.0), 2.0, 64.0),
                Math.clamp(config.getDouble(base + "wave-speed", 0.5), 0.05, 3.0),
                Math.max(1, config.getInt(base + "max-particles-per-player", 1000)));
    }
}
