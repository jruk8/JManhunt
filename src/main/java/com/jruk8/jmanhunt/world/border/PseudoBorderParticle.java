package com.jruk8.jmanhunt.world.border;

import org.bukkit.Particle;
import java.util.Arrays;
import java.util.List;

/**
 * Pseudoborder wall particle type. DUST is the default colored wall;
 * EFFECT types tint through the count-0 color protocol; the rest are
 * dataless one-shot shapes, from flames to hearts. Types with wild
 * inherent motion (smoke, firework, enchant) and the invisible FLASH
 * are excluded: no spawn arguments can steady or reveal them.
 */
public enum PseudoBorderParticle {
    DUST(Particle.DUST, false),
    HEART(Particle.HEART, false),
    HAPPY_VILLAGER(Particle.HAPPY_VILLAGER, false),
    ANGRY_VILLAGER(Particle.ANGRY_VILLAGER, false),
    WITCH(Particle.WITCH, false),
    FLAME(Particle.FLAME, false),
    SMALL_FLAME(Particle.SMALL_FLAME, false),
    EFFECT(Particle.EFFECT, true),
    INSTANT_EFFECT(Particle.INSTANT_EFFECT, true),
    PORTAL(Particle.PORTAL, false),
    END_ROD(Particle.END_ROD, false),
    NOTE(Particle.NOTE, false),
    SOUL(Particle.SOUL, false);

    private final Particle particle;
    private final boolean colorOffsets;

    PseudoBorderParticle(Particle particle, boolean colorOffsets) {
        this.particle = particle;
        this.colorOffsets = colorOffsets;
    }

    /** Bukkit particle to spawn for wall vertices. */
    public Particle particle() {
        return particle;
    }

    /** True only for DUST, which needs color DustOptions data. */
    public boolean usesDustOptions() {
        return this == DUST;
    }

    /**
     * True for types taking their color from the count-0 offset
     * protocol instead of plain one-shot spawns.
     */
    public boolean colorOffsets() {
        return colorOffsets;
    }

    /** Parses the type key case-insensitively, defaulting to DUST. */
    public static PseudoBorderParticle parse(String raw) {
        if (raw != null) {
            for (PseudoBorderParticle type : values()) {
                if (type.name().equalsIgnoreCase(raw.trim())) {
                    return type;
                }
            }
        }
        return DUST;
    }

    /** Type names for the setting options and completion. */
    public static List<String> names() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
