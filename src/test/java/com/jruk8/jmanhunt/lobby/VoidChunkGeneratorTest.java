package com.jruk8.jmanhunt.lobby;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VoidChunkGeneratorTest {

    @Test
    void generatesNothing() {
        VoidChunkGenerator generator = new VoidChunkGenerator();

        assertFalse(generator.shouldGenerateNoise());
        assertFalse(generator.shouldGenerateSurface());
        assertFalse(generator.shouldGenerateBedrock());
        assertFalse(generator.shouldGenerateCaves());
        assertFalse(generator.shouldGenerateDecorations());
        assertFalse(generator.shouldGenerateMobs());
        assertFalse(generator.shouldGenerateStructures());
    }
}
