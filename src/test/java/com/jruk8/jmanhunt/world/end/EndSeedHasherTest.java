package com.jruk8.jmanhunt.world.end;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndSeedHasherTest {

    @Test
    void resetSaltMatchesSpec() {
        assertEquals(1928348L, EndSeedHasher.RESET_SALT);
    }

    @Test
    void initialSeedIsDeterministic() {
        assertEquals(EndSeedHasher.initialSeed(12345L, 2L), EndSeedHasher.initialSeed(12345L, 2L));
    }

    @Test
    void initialSeedVariesAcrossPoolNumbers() {
        Set<Long> seeds = new HashSet<>();
        for (long n = 1; n <= 64; n++) {
            seeds.add(EndSeedHasher.initialSeed(987654321L, n));
        }
        assertEquals(64, seeds.size());
    }

    @Test
    void initialSeedAvalanchesAcrossAdjacentPoolNumbers() {
        for (long n = 1; n <= 8; n++) {
            long first = EndSeedHasher.initialSeed(987654321L, n);
            long second = EndSeedHasher.initialSeed(987654321L, n + 1);
            assertTrue(Long.bitCount(first ^ second) > 8,
                    "adjacent pool numbers must land on unrelated seeds");
        }
    }

    @Test
    void initialSeedVariesAcrossOverworldSeeds() {
        assertNotEquals(EndSeedHasher.initialSeed(1L, 1L), EndSeedHasher.initialSeed(2L, 1L));
    }

    @Test
    void resetSeedIsDeterministicAndChangesTheSeed() {
        long previous = 555L;
        assertEquals(EndSeedHasher.resetSeed(previous), EndSeedHasher.resetSeed(previous));
        assertNotEquals(previous, EndSeedHasher.resetSeed(previous));
    }
}
