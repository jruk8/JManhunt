package com.jruk8.jmanhunt.world.border;

import org.bukkit.Particle;
import java.util.Arrays;
import java.util.List;

/**
 * Pseudoborder wall particle type. DUST is the default colored wall;
 * the rest are dataless one-shot shapes, from smoke to hearts.
 */
public enum PseudoBorderParticle {
    DUST(Particle.DUST),
    SMOKE(Particle.SMOKE),
    LARGE_SMOKE(Particle.LARGE_SMOKE),
    HEART(Particle.HEART),
    HAPPY_VILLAGER(Particle.HAPPY_VILLAGER),
    ANGRY_VILLAGER(Particle.ANGRY_VILLAGER),
    WITCH(Particle.WITCH),
    FIREWORK(Particle.FIREWORK),
    FLASH(Particle.FLASH),
    FLAME(Particle.FLAME),
    SMALL_FLAME(Particle.SMALL_FLAME),
    EFFECT(Particle.EFFECT),
    INSTANT_EFFECT(Particle.INSTANT_EFFECT),
    PORTAL(Particle.PORTAL),
    END_ROD(Particle.END_ROD),
    NOTE(Particle.NOTE),
    SOUL(Particle.SOUL),
    ENCHANT(Particle.ENCHANT);

    private final Particle particle;

    PseudoBorderParticle(Particle particle) {
        this.particle = particle;
    }

    /** Bukkit particle to spawn for wall vertices. */
    public Particle particle() {
        return particle;
    }

    /** True only for DUST, which needs color DustOptions data. */
    public boolean usesDustOptions() {
        return this == DUST;
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
