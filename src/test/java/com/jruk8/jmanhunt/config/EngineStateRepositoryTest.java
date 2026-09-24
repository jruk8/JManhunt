package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.world.cell.WorldCellAllocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void setupDoneDefaultsFalseAndSurvivesReopen(@TempDir Path dataFolder) throws Exception {
        try (EngineStateRepository repository = EngineStateRepository.open(dataFolder.toFile())) {
            assertEquals(false, repository.getSetupDone());
            repository.setSetupDone(true);
            assertEquals(true, repository.getSetupDone());
        }
        try (EngineStateRepository repository = EngineStateRepository.open(dataFolder.toFile())) {
            assertEquals(true, repository.getSetupDone());
        }
    }

    @Test
    void cellIndexRoundTrip(@TempDir Path dataFolder) throws Exception {
        try (EngineStateRepository repository = EngineStateRepository.open(dataFolder.toFile())) {
            assertEquals(0L, repository.getWorldCellIndex());
            repository.setWorldCellIndex(41L);
            assertEquals(41L, repository.getWorldCellIndex());
        }
    }

    @Test
    void endReservationsRoundTrip(@TempDir Path dataFolder) throws Exception {
        try (EngineStateRepository repository = EngineStateRepository.open(dataFolder.toFile())) {
            assertTrue(repository.endReservations().isEmpty());

            repository.putEndReservation(7L, "world_the_end_3");
            repository.putEndReservation(9L, "world_the_end_9");
            assertEquals(Map.of(7L, "world_the_end_3", 9L, "world_the_end_9"),
                    repository.endReservations());

            repository.putEndReservation(7L, "world_the_end_4");
            assertEquals(Map.of(7L, "world_the_end_4", 9L, "world_the_end_9"),
                    repository.endReservations());

            repository.removeEndReservation(7L);
            assertEquals(Map.of(9L, "world_the_end_9"), repository.endReservations());
        }
    }

    @Test
    void endReservationsSurviveReopen(@TempDir Path dataFolder) throws Exception {
        try (EngineStateRepository repository = EngineStateRepository.open(dataFolder.toFile())) {
            repository.putEndReservation(7L, "world_the_end_3");
        }
        try (EngineStateRepository repository = EngineStateRepository.open(dataFolder.toFile())) {
            assertEquals(Map.of(7L, "world_the_end_3"), repository.endReservations());
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

    @Test
    void crashFlagRoundTrip(@TempDir Path dataFolder) throws Exception {
        try (EngineStateRepository repository = EngineStateRepository.open(dataFolder.toFile())) {
            assertEquals(false, repository.getCrashFlag());
            repository.setCrashFlag(true);
            assertEquals(true, repository.getCrashFlag());
            repository.setCrashFlag(false);
            assertEquals(false, repository.getCrashFlag());
        }
    }

    @Test
    void crashFlagSurvivesReopen(@TempDir Path dataFolder) throws Exception {
        try (EngineStateRepository repository = EngineStateRepository.open(dataFolder.toFile())) {
            repository.setCrashFlag(true);
        }
        try (EngineStateRepository repository = EngineStateRepository.open(dataFolder.toFile())) {
            assertEquals(true, repository.getCrashFlag());
        }
    }

    @Test
    void clearEndReservationsDropsAll(@TempDir Path dataFolder) throws Exception {
        try (EngineStateRepository repository = EngineStateRepository.open(dataFolder.toFile())) {
            repository.putEndReservation(7L, "world_the_end_3");
            repository.clearEndReservations();
            assertTrue(repository.endReservations().isEmpty());
        }
    }
}
