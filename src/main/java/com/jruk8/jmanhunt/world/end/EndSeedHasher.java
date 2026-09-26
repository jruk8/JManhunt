package com.jruk8.jmanhunt.world.end;

/**
 * Deterministic seeds for pooled end dimensions. Fresh pool members derive
 * their terrain from the overworld seed plus their pool number, so the same
 * number on the same overworld always generates the same end. Used members
 * reset to a hash of their previous seed, so every reuse lands on fresh
 * terrain without storing anything.
 */
public final class EndSeedHasher {
    /** Salt mixed into the previous seed when a used end dimension resets. */
    public static final long RESET_SALT = 1928348L;
    /** Golden ratio constant, spreads adjacent pool numbers across the hash space. */
    private static final long GOLDEN_RATIO = 0x9E3779B97F4A7C15L;

    private EndSeedHasher() {
    }

    /**
     * Seed for a freshly generated pool member: the overworld seed mixed
     * with the pool number.
     */
    public static long initialSeed(long overworldSeed, long n) {
        return mix64(overworldSeed ^ (n * GOLDEN_RATIO));
    }

    /** Seed for a used dimension returning to the pool after a reset. */
    public static long resetSeed(long previousSeed) {
        return mix64(previousSeed + RESET_SALT);
    }

    /**
     * MurmurHash3 64-bit finalizer (fmix64, Austin Appleby, public domain).
     * A single dependency-free avalanche mix: flipping one input bit flips
     * about half the output bits, so adjacent pool numbers and successive
     * resets land on unrelated terrain.
     */
    private static long mix64(long hash) {
        hash ^= hash >>> 33;
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= hash >>> 33;
        hash *= 0xC4CEB9FE1A85EC53L;
        hash ^= hash >>> 33;
        return hash;
    }
}
