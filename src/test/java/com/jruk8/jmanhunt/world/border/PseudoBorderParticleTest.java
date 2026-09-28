package com.jruk8.jmanhunt.world.border;

import org.bukkit.Particle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PseudoBorderParticleTest {

    @Test
    void parseMatchesCaseInsensitively() {
        assertEquals(PseudoBorderParticle.HEART, PseudoBorderParticle.parse("HEART"));
        assertEquals(PseudoBorderParticle.HEART, PseudoBorderParticle.parse("heart"));
        assertEquals(PseudoBorderParticle.EFFECT, PseudoBorderParticle.parse("effect"));
    }

    @Test
    void parseFallsBackToDust() {
        assertEquals(PseudoBorderParticle.DUST, PseudoBorderParticle.parse("DUST"));
        assertEquals(PseudoBorderParticle.DUST, PseudoBorderParticle.parse("star"));
        assertEquals(PseudoBorderParticle.DUST, PseudoBorderParticle.parse(null));
    }

    @Test
    void parseFallsBackToDustForRemovedTypes() {
        assertEquals(PseudoBorderParticle.DUST, PseudoBorderParticle.parse("FLASH"));
        assertEquals(PseudoBorderParticle.DUST, PseudoBorderParticle.parse("SMOKE"));
        assertEquals(PseudoBorderParticle.DUST, PseudoBorderParticle.parse("LARGE_SMOKE"));
        assertEquals(PseudoBorderParticle.DUST, PseudoBorderParticle.parse("FIREWORK"));
        assertEquals(PseudoBorderParticle.DUST, PseudoBorderParticle.parse("ENCHANT"));
    }

    @Test
    void onlyDustUsesDustOptions() {
        assertTrue(PseudoBorderParticle.DUST.usesDustOptions());
        assertFalse(PseudoBorderParticle.HEART.usesDustOptions());
        assertEquals(Particle.HEART, PseudoBorderParticle.HEART.particle());
    }

    @Test
    void onlyEffectTypesUseColorOffsets() {
        assertTrue(PseudoBorderParticle.EFFECT.colorOffsets());
        assertTrue(PseudoBorderParticle.INSTANT_EFFECT.colorOffsets());
        assertFalse(PseudoBorderParticle.DUST.colorOffsets());
        assertFalse(PseudoBorderParticle.HEART.colorOffsets());
    }
}
