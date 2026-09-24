package com.jruk8.jmanhunt.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Upgrade detection: the retired inline sounds block and any
 * settings child outside the four current categories are named
 * so the startup warning can point at them before Okaeri drops
 * them as orphans.
 */
class ConfigRegistrarTest {

    @Test
    void namesStaleSoundsAndSettingsBlocks(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("config.yml");
        Files.writeString(file, """
                sounds:
                  compass:
                    left-click: x
                settings:
                  match: {}
                  headstarts:
                    hunter: true
                """, StandardCharsets.UTF_8);

        assertEquals(List.of("sounds", "settings.headstarts"),
                ConfigRegistrar.staleBlocks(file.toFile()));
    }

    @Test
    void currentFilesReportNothing(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("config.yml");
        Files.writeString(file, """
                settings:
                  match: {}
                  compass: {}
                  players: {}
                  server: {}
                """, StandardCharsets.UTF_8);

        assertTrue(ConfigRegistrar.staleBlocks(file.toFile()).isEmpty());
        assertTrue(ConfigRegistrar.staleBlocks(folder.resolve("missing.yml").toFile()).isEmpty());
        assertTrue(ConfigRegistrar.staleBlocks(null).isEmpty());
    }
}
