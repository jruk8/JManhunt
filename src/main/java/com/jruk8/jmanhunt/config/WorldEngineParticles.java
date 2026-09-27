package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;

/** Pseudoborder wall particles for concurrent matches. */
@SuppressWarnings("FieldMayBeFinal")
public class WorldEngineParticles extends OkaeriConfig {

    @CustomKey("particle-spacing")
    @Comment({
            "Blocks between adjacent grid vertices on each wall.",
            "1 is the tightest grid (most detail, most expensive),",
            "8 is the loosest. Minimum: 1. Maximum: 8.",
            "Default: 2"
    })
    private int particleSpacing = 2;

    @Comment({
            "Wall color as a hex value, with or without a leading #.",
            "Default: #de7766"
    })
    private String color = "#de7766";

    @CustomKey("render-radius")
    @Comment({
            "Perpendicular blocks from a wall at which it starts",
            "rendering for a player. 0 disables the walls.",
            "Default: 10.0"
    })
    private double renderRadius = 10.0;

    @CustomKey("pulse-mode")
    @Comment({
            "Pulse style: INTERVAL blinks every wall in sync,",
            "SINE_WAVE sweeps a traveling band across each wall.",
            "Default: INTERVAL"
    })
    private String pulseMode = "INTERVAL";

    @Comment({
            "Seconds between INTERVAL blinks. 0 shows every tick",
            "(not recommended, lag). Minimum: 0. Maximum: 3.",
            "Default: 0.5"
    })
    private double interval = 0.5;

    @CustomKey("wave-direction-angle")
    @Comment({
            "SINE_WAVE travel direction in degrees,",
            "counter-clockwise from the wall horizontal when facing",
            "the wall. 0 travels along the wall, 90 travels up.",
            "Minimum: 0. Maximum: 360.",
            "Default: 0.0"
    })
    private double waveDirectionAngle = 0.0;

    @CustomKey("wave-length")
    @Comment({
            "SINE_WAVE spatial period in blocks. Minimum: 2.",
            "Maximum: 64.",
            "Default: 8.0"
    })
    private double waveLength = 8.0;

    @CustomKey("wave-speed")
    @Comment({
            "SINE_WAVE temporal frequency in Hz. Minimum: 0.05.",
            "Maximum: 3.0.",
            "Default: 0.5"
    })
    private double waveSpeed = 0.5;

    @CustomKey("max-particles-per-player")
    @Comment({
            "Hard ceiling on wall particles per player per tick, spread",
            "evenly across every visible wall. Minimum: 1.",
            "Default: 1000"
    })
    private int maxParticlesPerPlayer = 1000;

    public int getParticleSpacing() {
        return particleSpacing;
    }

    public void setParticleSpacing(int particleSpacing) {
        this.particleSpacing = particleSpacing;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public double getRenderRadius() {
        return renderRadius;
    }

    public void setRenderRadius(double renderRadius) {
        this.renderRadius = renderRadius;
    }

    public String getPulseMode() {
        return pulseMode;
    }

    public void setPulseMode(String pulseMode) {
        this.pulseMode = pulseMode;
    }

    public double getInterval() {
        return interval;
    }

    public void setInterval(double interval) {
        this.interval = interval;
    }

    public double getWaveDirectionAngle() {
        return waveDirectionAngle;
    }

    public void setWaveDirectionAngle(double waveDirectionAngle) {
        this.waveDirectionAngle = waveDirectionAngle;
    }

    public double getWaveLength() {
        return waveLength;
    }

    public void setWaveLength(double waveLength) {
        this.waveLength = waveLength;
    }

    public double getWaveSpeed() {
        return waveSpeed;
    }

    public void setWaveSpeed(double waveSpeed) {
        this.waveSpeed = waveSpeed;
    }

    public int getMaxParticlesPerPlayer() {
        return maxParticlesPerPlayer;
    }

    public void setMaxParticlesPerPlayer(int maxParticlesPerPlayer) {
        this.maxParticlesPerPlayer = maxParticlesPerPlayer;
    }
}
