package com.jruk8.jmanhunt.modifiers.files;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Populates one freshly created kind dir with bundled defaults.
 * Production copies from the jar; tests inject fixtures or nothing.
 */
@FunctionalInterface
public interface ModsSeeder {

    /** Fill {@code dir} (which already exists) with this kind's defaults. */
    void seed(ModFileKind kind, Path dir) throws IOException;

    /** Seeder that writes nothing. */
    static ModsSeeder none() {
        return (kind, dir) -> {
        };
    }
}
