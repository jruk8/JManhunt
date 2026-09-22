package com.jruk8.jmanhunt.lobby.world;

import org.bukkit.generator.ChunkGenerator;

/**
 * Generates completely empty chunks: no terrain, bedrock, caves,
 * decorations, mobs, or structures. Used for the natively generated lobby
 * world, which is just a void with a build platform.
 */
public final class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
