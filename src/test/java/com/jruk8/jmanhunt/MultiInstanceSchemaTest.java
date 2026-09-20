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

/**
 * Guards the bundled multi-instance schema: versions, new section defaults,
 * and new message keys. Reads the shipped config.yml and messages.yml
 * directly, so these tests run without a Bukkit server.
 */
class MultiInstanceSchemaTest {

    @Test
    void bundledVersionsAreCurrent() {
        assertEquals(5, bundledConfig().getInt("config-version"));
        assertEquals(8, bundledMessages().getInt("messages-version"));
    }

    @Test
    void lobbyDefaults() {
        YamlConfiguration config = bundledConfig();

        assertEquals(0, config.getInt("lobbies.default-lobby-id"));
        assertFalse(config.getBoolean("lobbies.join-teleports-to-lobby"));
        assertEquals(-1, config.getInt("lobbies.caps.speedrunner"));
        assertEquals(-1, config.getInt("lobbies.caps.hunter"));
    }

    @Test
    void headstartDefaults() {
        YamlConfiguration config = bundledConfig();

        assertFalse(config.getBoolean("settings.headstarts.hunter.enabled"));
        assertEquals(30, config.getInt("settings.headstarts.hunter.delay-seconds"));
        assertFalse(config.getBoolean("settings.headstarts.speedrunner.enabled"));
        assertEquals(30, config.getInt("settings.headstarts.speedrunner.delay-seconds"));
    }

    @Test
    void preloadingDefaults() {
        YamlConfiguration config = bundledConfig();

        assertTrue(config.getStringList("world-engine.preloading.commands").isEmpty());
        assertEquals(1, config.getInt("world-engine.preloading.cell-buffer.stored-cells-buffer"));
        assertEquals("ALWAYS", config.getString("world-engine.preloading.cell-buffer.increment-when"));
        assertEquals("NEVER", config.getString("world-engine.end-cell-prune-when"));
        assertFalse(config.getBoolean("debug.enabled"));
        assertFalse(config.contains("world-engine.on-fetch-new-cell"));
    }

    @Test
    void lobbyLocationDefaults() {
        YamlConfiguration config = bundledConfig();

        assertEquals("world", config.getString("world-engine.lobby-locations.0.world"));
        assertEquals(0.5, config.getDouble("world-engine.lobby-locations.0.x"), 0.0001);
        assertEquals(100.0, config.getDouble("world-engine.lobby-locations.0.y"), 0.0001);
        assertEquals(0.5, config.getDouble("world-engine.lobby-locations.0.z"), 0.0001);
    }

    @Test
    void instanceAndLobbyMessageKeysExist() {
        YamlConfiguration messages = bundledMessages();

        for (String key : List.of(
                "manhunt.headstart-active",
                "manhunt.headstart-ending",
                "manhunt.headstart-ended",
                "manhunt.status-all-header",
                "manhunt.status-all-entry",
                "manhunt.status-all-empty",
                "manhunt.invalid-instance-id",
                "manhunt.not-in-match",
                "manhunt.console-requires-id",
                "manhunt.joingame-success",
                "manhunt.joingame-announce",
                "manhunt.joingame-invalid-role",
                "manhunt.lobby-join-success",
                "manhunt.lobby-leave-success",
                "manhunt.lobby-invalid-id",
                "manhunt.lobby-not-member",
                "manhunt.lobby-full",
                "manhunt.lobby-no-location",
                "manhunt.lobby-worldengine-required",
                "manhunt.debug-enabled",
                "manhunt.debug-disabled",
                "manhunt.worldengine-cellindex-buffer-header",
                "manhunt.worldengine-cellindex-buffer-entry")) {
            assertTrue(messages.getString(key, null) != null, key);
        }
        assertTrue(messages.getString("command.no-targets", null) != null);
        assertTrue(messages.getString("manhunt.joingame-announce", "").contains("{player}"));
        assertTrue(messages.getString("manhunt.joingame-announce", "").contains("{role}"));
        assertTrue(messages.getString("manhunt.status-all-entry", "").contains("{id}"));
        assertTrue(messages.getString("manhunt.status-all-entry", "").contains("{duration}"));
        assertTrue(messages.getString("manhunt.headstart-active", "").contains("{role}"));
        assertTrue(messages.getString("manhunt.headstart-active", "").contains("{seconds}"));
    }

    @Test
    void debugMessageKeysExist() {
        YamlConfiguration messages = bundledMessages();

        assertEquals("<gray>[<bold>J</bold>ManhuntDebug]</gray> ",
                messages.getString("debug.prefix"));
        for (String key : List.of(
                "debug.cell-fetched",
                "debug.cell-buffer-add",
                "debug.cell-fetch-failed",
                "debug.match-start",
                "debug.match-end",
                "debug.end-cell-reserved",
                "debug.end-cell-created",
                "debug.end-cell-reset",
                "debug.end-cell-pruned",
                "debug.border-mode",
                "debug.portal-reroute")) {
            String value = messages.getString(key, null);
            assertTrue(value != null, key);
            assertTrue(value.contains("{debug-prefix}"), key);
        }
    }

    @Test
    void bundledResourcesContainNoEmDashes() throws Exception {
        assertFalse(rawResource("config.yml").contains("\u2014"), "config.yml");
        assertFalse(rawResource("messages.yml").contains("\u2014"), "messages.yml");
    }

    private static YamlConfiguration bundledConfig() {
        return bundled("config.yml");
    }

    private static YamlConfiguration bundledMessages() {
        return bundled("messages.yml");
    }

    private static YamlConfiguration bundled(String name) {
        var stream = MultiInstanceSchemaTest.class.getClassLoader().getResourceAsStream(name);
        return YamlConfiguration.loadConfiguration(
                new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8));
    }

    private static String rawResource(String name) throws Exception {
        try (InputStream stream = Objects.requireNonNull(
                MultiInstanceSchemaTest.class.getClassLoader().getResourceAsStream(name),
                "missing test resource: " + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
