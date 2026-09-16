package com.jruk8.jmanhunt;

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
        assertEquals(7, messages.getInt("messages-version"));
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
}
