package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesStyleTest {

    @Test
    void statusBlockHasOnePrefixAtTop() {
        YamlConfiguration messages = loadSchemaMessages();

        String header = messages.getString("manhunt.status-header", "");
        assertEquals(1, count(header, "{prefix}"));
        for (String key : List.of("manhunt.speedrunners-header", "manhunt.hunters-header",
                "manhunt.afk-header", "manhunt.none-header", "manhunt.status-player")) {
            assertFalse(messages.getString(key, "").contains("{prefix}"), key);
        }
        assertTrue(messages.getString("manhunt.status-player", "").contains("»"));
    }

    @Test
    void statLinesHaveNoPrefix() {
        YamlConfiguration messages = loadSchemaMessages();

        assertFalse(messages.getString("game.stat-header", "").contains("{prefix}"));
        assertFalse(messages.getString("game.stat-entry", "").contains("{prefix}"));
    }

    @Test
    void winBlockKeysHaveNoPrefixAndReasonSlots() {
        YamlConfiguration messages = loadSchemaMessages();

        for (String key : List.of("game.hunters-win", "game.speedrunners-win",
                "game.hunters-title", "game.speedrunners-title")) {
            assertFalse(messages.getString(key, "").contains("{prefix}"), key);
        }
        String reason = messages.getString("game.win-reason", "");
        assertTrue(reason.contains("{rolecolor}"), "win-reason needs {rolecolor}");
        assertTrue(reason.contains("{wincon}"), "win-reason needs {wincon}");
    }

    @Test
    void cancelAndCellIndexKeysExist() {
        YamlConfiguration messages = loadSchemaMessages();

        assertTrue(messages.getString("game.cancelled", "").contains("{prefix}"));
        assertFalse(messages.getString("game.cancelled-title", "").contains("{prefix}"));
        assertTrue(messages.getString("manhunt.end-success", null) == null);
        for (String key : List.of("manhunt.worldengine-cellindex-usage",
                "manhunt.worldengine-cellindex-get", "manhunt.worldengine-cellindex-set",
                "manhunt.worldengine-cellindex-invalid",
                "manhunt.worldengine-cellindex-unavailable")) {
            assertFalse(messages.getString(key, "").isBlank(), key);
        }
        assertFalse(messages.contains("messages-version"));
    }

    @Test
    void modifiersKeysExist() {
        YamlConfiguration messages = loadSchemaMessages();

        for (String key : List.of("modifiers.usage", "modifiers.setmod-usage",
                "modifiers.setpreset-usage", "modifiers.unknown-modifier",
                "modifiers.unknown-preset", "modifiers.invalid-state",
                "modifiers.setmod-success", "modifiers.setpreset-success",
                "modifiers.list-header", "modifiers.list-entry-on",
                "modifiers.list-entry-off", "modifiers.list-presets-header",
                "modifiers.list-empty", "modifiers.edit-id-changed",
                "modifiers.loop-limit")) {
            assertFalse(messages.getString(key, "").isBlank(), key);
        }
        String announced = messages.getString("modifiers.toggle-announced", "");
        assertTrue(announced.contains("{player}"), "toggle-announced needs {player}");
        assertTrue(announced.contains("{key}"), "toggle-announced needs {key}");
        assertTrue(announced.contains("{value}"), "toggle-announced needs {value}");
    }

    @Test
    void lobbyChangeKeysExistWithSlots() {
        YamlConfiguration messages = loadSchemaMessages();

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
    void guiKeysExistAndCarryNoPrefix() {
        YamlConfiguration messages = loadSchemaMessages();

        for (String key : List.of("modifiers-gui.title-main", "modifiers-gui.title-modifiers",
                "modifiers-gui.title-presets", "modifiers-gui.to-modifiers",
                "modifiers-gui.to-modifiers-lore", "modifiers-gui.to-presets",
                "modifiers-gui.to-presets-lore", "modifiers-gui.scroll-up",
                "modifiers-gui.scroll-down", "modifiers-gui.back",
                "modifiers-gui.toggle-all", "modifiers-gui.toggle-all-modifiers-lore",
                "modifiers-gui.toggle-all-presets-lore",
                "modifiers-gui.state-on", "modifiers-gui.state-off")) {
            String value = messages.getString(key, "");
            assertFalse(value.isBlank(), key);
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
    void sublobbyQueueKeysExistWithSlots() {
        YamlConfiguration messages = loadSchemaMessages();

        String queued = messages.getString("manhunt.setplayer-queued-sublobby", "");
        assertTrue(queued.contains("{role}"), "queued-sublobby needs {role}");
        assertFalse(queued.contains("{lobby}"), "queued-sublobby must not name a lobby");
        String summary = messages.getString("manhunt.setplayer-queued-sublobby-summary", "");
        assertTrue(summary.contains("{count}"), "queued-sublobby-summary needs {count}");
    }

    @Test
    void intervalSkipKeyCarriesModifierPlayerAndWhy() {
        YamlConfiguration messages = loadSchemaMessages();

        String value = messages.getString("debug.interval-skip", "");
        assertTrue(value.contains("{modifier}"), "interval-skip needs {modifier}");
        assertTrue(value.contains("{player}"), "interval-skip needs {player}");
        assertTrue(value.contains("{why}"), "interval-skip needs {why}");
    }

    @Test
    void bundledResourcesContainNoEmDashes() throws Exception {
        assertFalse(rawResource("modifiers.yml").contains("\u2014"), "modifiers.yml");
        assertFalse(rawResource("Core/gui.yml").contains("\u2014"), "Core/gui.yml");
        assertFalse(rawResource("Core/tutorial.yml").contains("\u2014"), "Core/tutorial.yml");
        assertFalse(rawResource("Core/dev.yml").contains("\u2014"), "Core/dev.yml");
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

    /**
     * Schema defaults rendered through Okaeri exactly as first-run
     * generation writes them. No Bukkit serdes: messages are plain
     * strings, so the configurer alone suffices.
     */
    private static YamlConfiguration loadSchemaMessages() {
        MessagesConfig config = ConfigManager.create(MessagesConfig.class,
                it -> it.withConfigurer(new YamlBukkitConfigurer()));
        return YamlConfiguration.loadConfiguration(new StringReader(config.saveToString()));
    }

    private static String rawResource(String name) throws Exception {
        try (InputStream stream = Objects.requireNonNull(
                MessagesStyleTest.class.getClassLoader().getResourceAsStream(name),
                "missing test resource: " + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
