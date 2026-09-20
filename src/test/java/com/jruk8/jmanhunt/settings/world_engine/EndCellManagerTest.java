package com.jruk8.jmanhunt.settings.world_engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EndCellManagerTest {

    @Test
    void endCellNameAppendsCellIndex() {
        assertEquals("world_the_end_5", EndCellManager.endCellName("world", 5L));
        assertEquals("arena_the_end_0", EndCellManager.endCellName("arena", 0L));
    }

    @Test
    void pruneCandidateIsOldestBeyondBuffer() {
        List<String> dirs = List.of("world_the_end_9", "world_the_end_2", "world_the_end_7");

        assertEquals(Optional.of("world_the_end_2"),
                EndCellManager.selectPruneCandidate(dirs, "world_the_end_", Set.of(), 2));
        assertEquals(Optional.empty(),
                EndCellManager.selectPruneCandidate(dirs, "world_the_end_", Set.of(), 3));
    }

    @Test
    void pruneCandidateSkipsReservedAndUnrelated() {
        List<String> dirs = List.of(
                "world_the_end_2", "world_the_end_7", "world_the_end_9", "world_the_end_old", "other");

        assertEquals(Optional.of("world_the_end_7"),
                EndCellManager.selectPruneCandidate(dirs, "world_the_end_", Set.of("world_the_end_2"), 2));
    }

    @Test
    void pruneCandidateSortsUnparsableSuffixesLast() {
        List<String> dirs = List.of("world_the_end_old", "world_the_end_9");

        assertEquals(Optional.of("world_the_end_9"),
                EndCellManager.selectPruneCandidate(dirs, "world_the_end_", Set.of(), 0));
    }
}
