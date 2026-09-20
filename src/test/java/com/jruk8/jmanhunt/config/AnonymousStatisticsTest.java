package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.command.ManhuntCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@code send-anonymous-statistics} toggle: it defaults to
 * true, sits at the very top of the bundled config, and stays hidden from
 * the in-game configuration command.
 *
 * <p>Deliberately Mockito-free: the plugin class extends Bukkit's
 * {@code JavaPlugin}, which the unit-test classpath cannot load, so the
 * name logic is exercised through the static
 * {@link ConfigService#settingNames} overload with a plain
 * {@link YamlConfiguration}.
 */
class AnonymousStatisticsTest {

    @Test
    void bundledDefaultEnablesAnonymousStatistics() throws Exception {
        YamlConfiguration defaults = loadBundledConfig();

        // A false fallback proves the key is present and true, not just missing.
        assertTrue(defaults.getBoolean("send-anonymous-statistics", false));
    }

    @Test
    void toggleSitsDirectlyBelowConfigVersion() throws Exception {
        List<String> lines = new ArrayList<>();
        try (InputStream stream = resource("config.yml");
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        int versionIndex = indexOfKey(lines, "config-version:");
        int toggleIndex = indexOfKey(lines, "send-anonymous-statistics:");
        assertTrue(versionIndex >= 0, "bundled config must define config-version");
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
        YamlConfiguration config = new YamlConfiguration();
        config.set("config-version", 3);
        config.set("send-anonymous-statistics", true);
        config.set("settings.autostart.enabled", true);

        var names = ConfigService.settingNames(config);

        assertFalse(names.contains("send-anonymous-statistics"));
        assertFalse(names.contains("config-version"));
        assertTrue(names.contains("settings.autostart.enabled"));
    }

    @Test
    void inGameDrillCannotReachToggleAsEditableLeaf() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("config-version", 3);
        config.set("send-anonymous-statistics", true);
        config.set("settings.autostart.enabled", true);

        var editable = ConfigService.settingNames(config);
        var resolved = ManhuntCommand.resolveDrill(
                config, editable, List.of("send-anonymous-statistics"));

        // The key exists in the file, but it must never resolve as a leaf the
        // configuration command would view or update, nor appear in tab
        // completion at the top level.
        if (resolved != null) {
            assertFalse(resolved.leaf());
        }
        assertFalse(ManhuntCommand.drillChildren(config, editable, List.of())
                .contains("send-anonymous-statistics"));
    }

    private static YamlConfiguration loadBundledConfig() throws Exception {
        try (InputStream stream = resource("config.yml")) {
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static InputStream resource(String name) {
        return Objects.requireNonNull(
                AnonymousStatisticsTest.class.getClassLoader().getResourceAsStream(name),
                "missing test resource: " + name);
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
