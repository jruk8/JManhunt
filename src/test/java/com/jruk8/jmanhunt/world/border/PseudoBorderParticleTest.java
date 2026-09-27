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
        assertEquals(PseudoBorderParticle.FIREWORK, PseudoBorderParticle.parse("firework"));
    }

    @Test
    void parseFallsBackToDust() {
        assertEquals(PseudoBorderParticle.DUST, PseudoBorderParticle.parse("DUST"));
        assertEquals(PseudoBorderParticle.DUST, PseudoBorderParticle.parse("star"));
        assertEquals(PseudoBorderParticle.DUST, PseudoBorderParticle.parse(null));
    }

    @Test
    void onlyDustUsesDustOptions() {
        assertTrue(PseudoBorderParticle.DUST.usesDustOptions());
        assertFalse(PseudoBorderParticle.HEART.usesDustOptions());
        assertEquals(Particle.HEART, PseudoBorderParticle.HEART.particle());
    }
}
