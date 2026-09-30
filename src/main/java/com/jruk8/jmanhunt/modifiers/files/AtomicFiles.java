package com.jruk8.jmanhunt.modifiers.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Crash-safe single-file writes: content goes to a dot-leading temp
 * file in the target directory (which the loader ignores), then
 * moves over the target atomically when the filesystem allows it.
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /** Writes text to {@code target} via temp file plus move. */
    public static void writeString(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = target.resolveSibling("." + target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            move(temp, target);
        } catch (IOException failed) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanup) {
                // The temp file is loader-ignored, so a leftover is harmless.
            }
            throw failed;
        }
    }

    /** Moves one file, atomically when the filesystem allows it. */
    public static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException fallback) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
