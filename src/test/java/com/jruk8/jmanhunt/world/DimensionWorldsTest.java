package com.jruk8.jmanhunt.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Two-location dimension lookups behind the lobby and end worlds. */
class DimensionWorldsTest {

    @Test
    void unloadedFolderPrefersContainerRoot(@TempDir Path container) throws IOException {
        Files.createDirectory(container.resolve("jmh_end_1"));

        assertEquals(container.resolve("jmh_end_1").toFile(), DimensionWorlds.unloadedFolder(
                container.toFile(), "world", "jmh_end_1"));
    }

    @Test
    void unloadedFolderFallsBackToDimensionsDir(@TempDir Path container) throws IOException {
        Path dimensions = Files.createDirectories(container.resolve("world/dimensions/minecraft"));
        Files.createDirectory(dimensions.resolve("jmh_end_1"));

        assertEquals(dimensions.resolve("jmh_end_1").toFile(), DimensionWorlds.unloadedFolder(
                container.toFile(), "world", "jmh_end_1"));
    }

    @Test
    void unloadedFolderReturnsDimensionsCandidateWhenMissing(@TempDir Path container) {
        assertEquals(new File(container.toFile(), "world/dimensions/minecraft/jmh_end_9"),
                DimensionWorlds.unloadedFolder(container.toFile(), "world", "jmh_end_9"));
    }

    @Test
    void folderExistsInEitherLayout(@TempDir Path container) throws IOException {
        Files.createDirectory(container.resolve("jmh-lobby"));
        Path dimensions = Files.createDirectories(container.resolve("world/dimensions/minecraft"));
        Files.createDirectory(dimensions.resolve("jmh_end_1"));

        assertTrue(DimensionWorlds.folderExists(container.toFile(), "world", "jmh-lobby"));
        assertTrue(DimensionWorlds.folderExists(container.toFile(), "world", "jmh_end_1"));
        assertFalse(DimensionWorlds.folderExists(container.toFile(), "world", "missing"));
    }

    @Test
    void dimensionRootPointsUnderParentWorld(@TempDir Path container) {
        assertEquals(new File(container.toFile(), "world/dimensions/minecraft"),
                DimensionWorlds.dimensionRoot(container.toFile(), "world"));
    }

    @Test
    void dimensionDirsListsFoldersOnly(@TempDir Path container) throws IOException {
        Path dimensions = Files.createDirectories(container.resolve("world/dimensions/minecraft"));
        Files.createDirectory(dimensions.resolve("jmh_end_1"));
        Files.createFile(dimensions.resolve("stray.txt"));

        assertEquals(List.of("jmh_end_1"),
                DimensionWorlds.dimensionDirs(container.toFile(), "world"));
        assertEquals(List.of(), DimensionWorlds.dimensionDirs(container.toFile(), "missing-parent"));
    }
}
