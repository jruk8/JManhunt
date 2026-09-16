package com.jruk8.jmanhunt;

import com.jruk8.jmanhunt.settings.world_engine.WorldCellAllocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EngineStateRepositoryTest {

    @Test
    void allocatesIncreasingCellIndexes(@TempDir Path dataFolder) throws Exception {
        try (EngineStateRepository repository = EngineStateRepository.open(dataFolder.toFile())) {
            assertEquals(0L, repository.consumeWorldCellIndexes(1));
            assertEquals(1L, repository.consumeWorldCellIndexes(1));
            assertEquals(2L, repository.consumeWorldCellIndexes(5));
        }
    }

    @Test
    void cellIndexSurvivesReopen(@TempDir Path dataFolder) throws Exception {
        try (EngineStateRepository repository = EngineStateRepository.open(dataFolder.toFile())) {
            repository.consumeWorldCellIndexes(3);
        }
        try (EngineStateRepository repository = EngineStateRepository.open(dataFolder.toFile())) {
            assertEquals(3L, repository.consumeWorldCellIndexes(1));
        }
    }

    @Test
    void allocatorFallsBackWithoutRepository() {
        WorldCellAllocator allocator = new WorldCellAllocator(null);

        assertEquals(0L, allocator.reserveStartIndex(1).orElseThrow());
        assertEquals(1L, allocator.reserveStartIndex(1).orElseThrow());
    }

    @Test
    void allocatorDelegatesToRepository(@TempDir Path dataFolder) throws Exception {
        try (EngineStateRepository repository = EngineStateRepository.open(dataFolder.toFile())) {
            WorldCellAllocator allocator = new WorldCellAllocator(repository);

            assertEquals(0L, allocator.reserveStartIndex(1).orElseThrow());
            assertEquals(1L, allocator.reserveStartIndex(1).orElseThrow());
        }
    }
}
