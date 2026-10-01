package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@code send-anonymous-statistics} toggle: it sits at the
 * very top of the generated config and stays hidden from the in-game
 * configuration command.
 *
 * <p>Deliberately Mockito-free: the plugin class extends Bukkit's
 * {@code JavaPlugin}, which the unit-test classpath cannot load, so the
 * name logic is exercised through the static setting registry.
 */
class AnonymousStatisticsTest {

    @Test
    void toggleSitsDirectlyBelowConfigVersion(@TempDir Path folder) throws Exception {
        // Rendered through Okaeri exactly as first-run generation writes
        // it. No Bukkit serdes and no load-back: both need a running
        // server, and the written file is all the order check needs.
        File file = folder.resolve("config.yml").toFile();
        JManhuntConfig config = ConfigManager.create(JManhuntConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer());
            it.withBindFile(file);
        });
        config.saveDefaults();
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

        int versionIndex = indexOfKey(lines, "config-version:");
        int toggleIndex = indexOfKey(lines, "send-anonymous-statistics:");
        assertTrue(versionIndex >= 0, "generated config must define config-version");
        assertTrue(toggleIndex > versionIndex,
                "send-anonymous-statistics must come after config-version");

        // Only blank lines and comments may sit between the two keys.
        for (String line : lines.subList(versionIndex + 1, toggleIndex)) {
            String trimmed = line.trim();
            assertTrue(trimmed.isEmpty() || trimmed.startsWith("#"),
                    "unexpected content between config-version and send-anonymous-statistics: " + line);
        }
    }

    @Test
    void toggleIsHiddenFromInGameConfiguration() {
        var names = SettingRegistry.settingNames();

        assertFalse(names.contains("send-anonymous-statistics"));
        assertFalse(names.contains("config-version"));
        assertTrue(names.contains("settings.match.autostart.enabled"));
    }

    @Test
    void inGameDrillCannotReachToggleAsEditableLeaf() {
        var resolved = ConfigDrill.resolveDrill(
                List.of("send-anonymous-statistics"), path -> null);

        // The key exists in the file, but it must never resolve as a leaf the
        // configuration command would view or update, nor appear in tab
        // completion at the top level.
        if (resolved != null) {
            assertFalse(resolved.leaf());
        }
        assertFalse(ConfigDrill.drillChildren(List.of(), path -> null)
                .contains("send-anonymous-statistics"));
    }

    private static int indexOfKey(List<String> lines, String key) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith(key)) {
                return i;
            }
        }
        return -1;
    }
}
