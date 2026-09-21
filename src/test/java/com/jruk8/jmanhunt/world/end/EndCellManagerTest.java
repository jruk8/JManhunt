package com.jruk8.jmanhunt.world.end;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EndCellManagerTest {

    @Test
    void endCellNameAppendsCellIndex() {
        assertEquals("world_the_end_5", EndCellManager.endCellName("world", 5L));
        assertEquals("arena_the_end_0", EndCellManager.endCellName("arena", 0L));
    }

    @Test
    void strayEndWorldsMatchPrefixSorted() {
        List<String> dirs = List.of(
                "world_the_end_9", "other", "world_the_end_2", "world", "world_the_end_old");

        assertEquals(
                List.of("world_the_end_2", "world_the_end_9", "world_the_end_old"),
                EndCellManager.strayEndWorlds(dirs, "world_the_end_"));
    }

    @Test
    void strayEndWorldsSpareTheSharedEnd() {
        List<String> dirs = List.of("world_the_end", "world_the_end_1");

        assertEquals(
                List.of("world_the_end_1"),
                EndCellManager.strayEndWorlds(dirs, "world_the_end_"));
    }
}
