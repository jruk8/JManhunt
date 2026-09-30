package com.jruk8.jmanhunt.modifiers.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModFileWalkTest {

    @TempDir
    private Path tempDir;

    @Test
    void subdirsComeFirstThenOwnFiles() throws Exception {
        Path root = tempDir.resolve("mods");
        write(root, "z.yml", "z");
        write(root, "a.yml", "a");
        write(root, "note.txt", "n");
        write(root, "sub/m.yml", "m");
        write(root, "sub/deep/d.yml", "d");
        write(root, "aaa/n.yml", "n");

        List<Path> files = ModFileWalk.walk(root);

        assertEquals(List.of(
                root.resolve("aaa/n.yml"),
                root.resolve("sub/deep/d.yml"),
                root.resolve("sub/m.yml"),
                root.resolve("a.yml"),
                root.resolve("note.txt"),
                root.resolve("z.yml")), files);
    }

    @Test
    void dotfilesAndTempFilesAreSkippedSilently() throws Exception {
        Path root = tempDir.resolve("mods");
        write(root, ".hidden.yml", "enabled: [broken");
        write(root, "stale.yml.tmp", "junk");
        write(root, "x.tmp", "junk");
        write(root, "real.yml", "enabled: true\n");

        assertEquals(List.of(root.resolve("real.yml")), ModFileWalk.walk(root));
    }

    @Test
    void emptyDirsYieldNothing() throws Exception {
        Path root = tempDir.resolve("mods");
        Files.createDirectories(root.resolve("empty"));

        assertTrue(ModFileWalk.walk(root).isEmpty());
    }

    private static void write(Path root, String relative, String body) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, body, StandardCharsets.UTF_8);
    }
}
