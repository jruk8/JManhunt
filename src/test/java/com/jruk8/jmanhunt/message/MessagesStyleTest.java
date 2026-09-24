package com.jruk8.jmanhunt.message;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesStyleTest {

    @Test
    void statusBlockHasOnePrefixAtTop() throws Exception {
        YamlConfiguration messages = loadBundledMessages();

        String header = messages.getString("manhunt.status-header", "");
        assertEquals(1, count(header, "{prefix}"));
        for (String key : List.of("manhunt.speedrunners-header", "manhunt.hunters-header",
                "manhunt.afk-header", "manhunt.none-header", "manhunt.status-player")) {
            assertFalse(messages.getString(key, "").contains("{prefix}"), key);
        }
        assertTrue(messages.getString("manhunt.status-player", "").contains("»"));
    }

    @Test
    void statLinesHaveNoPrefix() throws Exception {
        YamlConfiguration messages = loadBundledMessages();

        assertFalse(messages.getString("game.stat-header", "").contains("{prefix}"));
        assertFalse(messages.getString("game.stat-entry", "").contains("{prefix}"));
    }

    @Test
    void cancelAndCellIndexKeysExist() throws Exception {
        YamlConfiguration messages = loadBundledMessages();

        assertTrue(messages.getString("game.cancelled", "").contains("{prefix}"));
        assertFalse(messages.getString("game.cancelled-title", "").contains("{prefix}"));
        assertTrue(messages.getString("manhunt.end-success", null) == null);
        for (String key : List.of("manhunt.worldengine-cellindex-usage",
                "manhunt.worldengine-cellindex-get", "manhunt.worldengine-cellindex-set",
                "manhunt.worldengine-cellindex-invalid",
                "manhunt.worldengine-cellindex-unavailable")) {
            assertTrue(messages.getString(key, null) != null, key);
        }
        assertFalse(messages.contains("messages-version"));
    }

    @Test
    void modifiersKeysExist() throws Exception {
        YamlConfiguration messages = loadBundledMessages();

        for (String key : List.of("modifiers.usage", "modifiers.setmod-usage",
                "modifiers.setpreset-usage", "modifiers.unknown-modifier",
                "modifiers.unknown-preset", "modifiers.invalid-state",
                "modifiers.setmod-success", "modifiers.setpreset-success",
                "modifiers.list-header", "modifiers.list-entry-on",
                "modifiers.list-entry-off", "modifiers.list-presets-header",
                "modifiers.list-empty")) {
            assertTrue(messages.getString(key, null) != null, key);
        }
        String announced = messages.getString("modifiers.toggle-announced", "");
        assertTrue(announced.contains("{player}"), "toggle-announced needs {player}");
        assertTrue(announced.contains("{key}"), "toggle-announced needs {key}");
        assertTrue(announced.contains("{value}"), "toggle-announced needs {value}");
    }

    @Test
    void lobbyChangeKeysExistWithSlots() throws Exception {
        YamlConfiguration messages = loadBundledMessages();

        for (String key : List.of("manhunt.lobby-left", "manhunt.lobby-joined")) {
            String value = messages.getString(key, "");
            assertTrue(value.contains("{lobby}"), key);
        }
        for (String key : List.of("manhunt.lobby-left-member", "manhunt.lobby-joined-member")) {
            String value = messages.getString(key, "");
            assertTrue(value.contains("{player}"), key);
            assertTrue(value.contains("{lobby}"), key);
        }
        assertTrue(messages.getString("manhunt.worldengine-lobbyconfig-duplicate-bounds", "")
                .contains("{other}"));
    }

    @Test
    void guiKeysExistAndCarryNoPrefix() throws Exception {
        YamlConfiguration messages = loadBundledMessages();

        for (String key : List.of("modifiers-gui.title-main", "modifiers-gui.title-modifiers",
                "modifiers-gui.title-presets", "modifiers-gui.to-modifiers",
                "modifiers-gui.to-modifiers-lore", "modifiers-gui.to-presets",
                "modifiers-gui.to-presets-lore", "modifiers-gui.scroll-up",
                "modifiers-gui.scroll-down", "modifiers-gui.back",
                "modifiers-gui.toggle-all", "modifiers-gui.toggle-all-modifiers-lore",
                "modifiers-gui.toggle-all-presets-lore",
                "modifiers-gui.state-on", "modifiers-gui.state-off")) {
            String value = messages.getString(key, null);
            assertTrue(value != null, key);
            assertFalse(value.contains("{prefix}"), key);
        }
        for (String key : List.of("modifiers-gui.toggle-all-modifiers-lore",
                "modifiers-gui.toggle-all-presets-lore")) {
            assertTrue(messages.getString(key, "").contains("{total}"), key);
        }
        for (String key : List.of("modifiers-gui.to-modifiers-lore",
                "modifiers-gui.to-presets-lore")) {
            String lore = messages.getString(key, "");
            assertTrue(lore.contains("{enabled}"), key);
            assertTrue(lore.contains("{total}"), key);
        }
    }

    @Test
    void bundledResourcesContainNoEmDashes() throws Exception {
        assertFalse(rawResource("config.yml").contains("\u2014"), "config.yml");
        assertFalse(rawResource("messages.yml").contains("\u2014"), "messages.yml");
        assertFalse(rawResource("sounds.yml").contains("\u2014"), "sounds.yml");
    }

    private static int count(String text, String token) {
        int found = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            found++;
            index += token.length();
        }
        return found;
    }

    private static YamlConfiguration loadBundledMessages() throws Exception {
        try (InputStream stream = Objects.requireNonNull(
                MessagesStyleTest.class.getClassLoader().getResourceAsStream("messages.yml"),
                "missing test resource: messages.yml")) {
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static String rawResource(String name) throws Exception {
        try (InputStream stream = Objects.requireNonNull(
                MessagesStyleTest.class.getClassLoader().getResourceAsStream(name),
                "missing test resource: " + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
