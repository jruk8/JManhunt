package com.jruk8.jmanhunt.lobby.config;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Overrides storage survives an Okaeri save/load round trip: nested
 * maps, scalar types, string lists, modifier flags, and insertion
 * order all come back intact.
 */
class LobbyOverridesTest {

    @TempDir
    Path temp;

    private LobbyConfig load(Path file) {
        return ConfigManager.create(LobbyConfig.class, it -> {
            it.withConfigurer(new YamlBukkitConfigurer());
            it.withBindFile(file.toFile());
            it.withRemoveOrphans(true);
            it.saveDefaults();
            it.load(true);
        });
    }

    @Test
    void nestedOverridesRoundTrip() {
        Path file = temp.resolve("lobby-config.yml");
        LobbyConfig saved = load(file);
        assertTrue(saved.getLobbies().containsKey("0"));
        LobbyConfig.OverridesData overrides = saved.getLobbies().get("0").getOverrides();

        Map<String, Object> debuffs = new LinkedHashMap<>();
        debuffs.put("enabled", true);
        Map<String, Object> analyze = new LinkedHashMap<>();
        analyze.put("debuffs", debuffs);
        Map<String, Object> compass = new LinkedHashMap<>();
        compass.put("analyze", analyze);
        compass.put("refresh-interval", 5.5);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("settings", Map.of("compass", compass));
        settings.put("match", Map.of("end-delay", 30));
        settings.put("names", List.of("a", "b"));
        overrides.setSettings(settings);
        Map<String, Boolean> modifiers = new LinkedHashMap<>();
        modifiers.put("everyone-gets-beef", true);
        modifiers.put("gapple", false);
        overrides.setModifiers(modifiers);
        saved.save();

        LobbyConfig loaded = load(file);
        LobbyConfig.OverridesData back =
                loaded.getLobbies().get("0").getOverrides();
        assertEquals(List.of("settings", "match", "names"),
                new ArrayList<>(back.getSettings().keySet()));
        assertEquals(Map.of("everyone-gets-beef", true, "gapple", false),
                back.getModifiers());
        assertEquals(true, nested(back.getSettings(),
                "settings", "compass", "analyze", "debuffs", "enabled"));
        assertEquals(5.5, nested(back.getSettings(),
                "settings", "compass", "refresh-interval"));
        assertEquals(30, nested(back.getSettings(), "match", "end-delay"));
        assertEquals(List.of("a", "b"), back.getSettings().get("names"));
    }

    @Test
    void emptyOverridesSaveAndLoad() {
        Path file = temp.resolve("lobby-config.yml");
        LobbyConfig saved = load(file);
        saved.save();

        LobbyConfig loaded = load(file);
        LobbyConfig.OverridesData back =
                loaded.getLobbies().get("0").getOverrides();
        assertTrue(back.getSettings().isEmpty());
        assertTrue(back.getModifiers().isEmpty());
    }

    private static Object nested(Map<String, Object> root, String... segments) {
        Object current = root;
        for (String segment : segments) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(segment);
            } else if (current instanceof org.bukkit.configuration.ConfigurationSection section) {
                current = section.get(segment);
            } else {
                return null;
            }
        }
        return current;
    }
}
