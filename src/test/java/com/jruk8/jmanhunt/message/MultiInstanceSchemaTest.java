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
        assertTrue(config.getBoolean("lobbies.join-teleports-to-lobby"));
        assertEquals(-1, config.getInt("lobbies.queue-caps.speedrunner"));
        assertEquals(-1, config.getInt("lobbies.queue-caps.hunter"));
    }

    @Test
    void headstartDefaults() {
        YamlConfiguration config = bundledConfig();

        assertFalse(config.getBoolean("settings.headstarts.hunter.enabled"));
        assertEquals(30, config.getInt("settings.headstarts.hunter.delay-seconds"));
        assertTrue(config.getBoolean("settings.headstarts.speedrunner.enabled"));
        assertEquals(30, config.getInt("settings.headstarts.speedrunner.delay-seconds"));
    }

    @Test
    void respawnDefaults() {
        YamlConfiguration config = bundledConfig();
        YamlConfiguration messages = bundledMessages();

        assertFalse(config.contains("settings.hunter-respawn"));
        assertTrue(config.getBoolean("settings.respawn.hunter.enabled"));
        assertEquals(15, config.getInt("settings.respawn.hunter.delay-seconds"));
        assertEquals(-1, config.getInt("settings.respawn.hunter.lives"));
        assertFalse(config.getBoolean("settings.respawn.speedrunner.enabled"));
        assertEquals(60, config.getInt("settings.respawn.speedrunner.delay-seconds"));
        assertEquals(1, config.getInt("settings.respawn.speedrunner.lives"));
        assertTrue(messages.getString("game.speedrunner-respawn-scheduled", "").contains("{player}"));
        assertTrue(messages.getString("game.speedrunner-respawn-scheduled", "").contains("{seconds}"));
        assertTrue(messages.getString("game.speedrunner-respawn-imminent", "").contains("{player}"));
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
        assertEquals("jmh-lobby", config.getString("world-engine.lobby-world-name"));
        assertTrue(config.getBoolean("world-engine.lobby-world-void-rescue"));
    }

    @Test
    void hunterWinConditionDefaults() {
        YamlConfiguration config = bundledConfig();

        assertFalse(config.getBoolean("settings.win-conditions.speedrunner.kill-mob.enabled"));
        assertEquals("minecraft:ender_dragon", config.getString("settings.win-conditions.speedrunner.kill-mob.mob"));
        assertFalse(config.getBoolean("settings.win-conditions.hunter.survive-time.enabled"));
        assertEquals(3600.0, config.getDouble("settings.win-conditions.hunter.survive-time.time"));
        assertFalse(config.getBoolean("settings.win-conditions.hunter.acquire-item.enabled"));
        assertFalse(config.getBoolean("settings.win-conditions.hunter.kill-mob.enabled"));
        assertFalse(config.getBoolean("settings.win-conditions.hunter.reach-advancement.enabled"));
        assertEquals("minecraft:story/enter_the_nether",
                config.getString("settings.win-conditions.hunter.reach-advancement.advancement"));
    }

    @Test
    void statusDisplayDefaults() {
        YamlConfiguration config = bundledConfig();
        YamlConfiguration messages = bundledMessages();

        assertFalse(config.getBoolean("settings.status.show-win-conditions"));
        assertFalse(config.getBoolean("settings.status.show-elapsed-time"));
        assertFalse(config.getBoolean("settings.status.show-modifiers"));
        assertTrue(config.getBoolean("settings.status.show-ids"));
        assertTrue(messages.getString("manhunt.status-win-speedrunners", "").contains("{conditions}"));
        assertTrue(messages.getString("manhunt.status-win-hunters", "").contains("{conditions}"));
        assertTrue(messages.getString("manhunt.status-elapsed", "").contains("{duration}"));
        assertTrue(messages.getString("manhunt.status-ids", "").contains("{value}"));
        assertTrue(messages.getString("game.time-left", "").contains("{time}"));
        assertTrue(messages.getString("game.time-left", "").contains("{winner}"));
    }

    @Test
    void roleColorKeysExistWithDefaults() {
        YamlConfiguration messages = bundledMessages();

        assertEquals("<#74de66>", messages.getString("role-colors.speedrunner"));
        assertEquals("<#de666e>", messages.getString("role-colors.hunter"));
        assertEquals("<#6e728a>", messages.getString("role-colors.spectator"));
        assertEquals("<#a18e68>", messages.getString("role-colors.afk"));
        assertEquals("<#7d7d7d>", messages.getString("role-colors.none"));
    }

    @Test
    void commandUsageAndEdgeKeysExist() {
        YamlConfiguration messages = bundledMessages();

        for (String key : List.of(
                "manhunt.status-usage",
                "manhunt.setplayer-usage",
                "manhunt.set-in-match",
                "manhunt.set-afk-confirm",
                "manhunt.start-usage",
                "manhunt.end-usage",
                "manhunt.game-usage",
                "manhunt.game-join-usage",
                "manhunt.game-leave-usage",
                "manhunt.lobby-usage",
                "manhunt.lobby-join-usage",
                "manhunt.lobby-leave-usage",
                "manhunt.lobby-already-member",
                "manhunt.lobby-join-in-match",
                "manhunt.lobby-leave-not-member",
                "manhunt.lobby-leave-in-match",
                "manhunt.quickstart-usage",
                "manhunt.configuration-usage",
                "manhunt.debug-usage",
                "manhunt.worldengine-setlobby-usage",
                "manhunt.worldengine-setlobbytp-usage",
                "manhunt.worldengine-tpto-usage",
                "manhunt.worldengine-tpto-lobby-world-clash",
                "game.join-no-change")) {
            assertTrue(messages.getString(key, null) != null, key);
        }
        assertTrue(messages.getString("manhunt.lobby-not-member", null) == null);
        assertTrue(messages.getString("manhunt.set-afk-confirm", "").contains("{count}"));
        assertTrue(messages.getString("manhunt.worldengine-cellindex-set", "").contains("{was}"));
    }

    @Test
    void compassAnalyzeDefaults() {
        YamlConfiguration config = bundledConfig();
        YamlConfiguration messages = bundledMessages();

        assertFalse(config.getBoolean("settings.compass.analyze.right-click"));
        assertFalse(config.getBoolean("settings.compass.analyze.auto"));
        assertEquals(1.0, config.getDouble("settings.compass.analyze.delay-seconds"));
        assertTrue(messages.getString("compass.analyzing-actionbar", "").contains("Analyzing..."));
    }

    @Test
    void compassLeftClickDefaults() {
        YamlConfiguration config = bundledConfig();
        YamlConfiguration messages = bundledMessages();

        assertFalse(config.getBoolean("settings.compass.left-click.enabled"));
        assertEquals(5, config.getInt("settings.compass.left-click.max-targets"));
        assertTrue(messages.getString("compass.compass-locked-actionbar", "").contains("[LOCKED]"));
        assertTrue(messages.getString("compass.compass-last-seen-locked-actionbar", "").contains("[LOCKED]"));
    }

    @Test
    void antiSpawnCampDefaults() {
        YamlConfiguration config = bundledConfig();
        YamlConfiguration messages = bundledMessages();

        assertTrue(config.getBoolean("settings.anti-spawn-camp.enabled"));
        assertEquals(3, config.getInt("settings.anti-spawn-camp.kills"));
        assertEquals(120.0, config.getDouble("settings.anti-spawn-camp.window-seconds"));
        assertEquals("KILL", config.getString("settings.anti-spawn-camp.punishment"));
        assertTrue(messages.getString("game.spawncamp-kill", "").contains("{victim}"));
        assertTrue(messages.getString("game.spawncamp-gear-wipe", "").contains("{victim}"));
        assertTrue(messages.getString("game.spawncamp-warning", "").contains("{victim}"));
    }

    @Test
    void roleChangeBroadcastDefaults() {
        YamlConfiguration config = bundledConfig();
        YamlConfiguration messages = bundledMessages();

        assertFalse(config.getBoolean("settings.announce-role-changes"));
        assertTrue(messages.getString("manhunt.role-is-now", "").contains("{active-role}"));
        assertTrue(messages.getString("manhunt.role-no-longer", "").contains("{active-role}"));
    }

    @Test
    void noneHandlingDefaults() {
        YamlConfiguration config = bundledConfig();

        assertFalse(config.getBoolean("settings.roles.turn-nones-spectator.enabled"));
        assertTrue(config.getBoolean("settings.start-on-speedrunner-damage.start-in-adventure-mode"));
    }

    @Test
    void gameLeaveDefaults() {
        YamlConfiguration config = bundledConfig();

        assertEquals("SPECTATOR", config.getString("settings.game-leave.destination"));
    }

    @Test
    void spectatorAnnounceDefaults() {
        YamlConfiguration messages = bundledMessages();
        YamlConfiguration config = bundledConfig();

        assertTrue(messages.getString("manhunt.spectators-line", "").contains("{value}"));
        assertTrue(messages.getString("manhunt.spectators-line", "").contains("Spectators"));
        assertEquals("<gray>Watch the Hunt",
                messages.getString("manhunt.role-announce-subtitle-spectator"));
        assertTrue(config.getBoolean("sounds.announce.spectator.enabled"));
        assertEquals("block.note_block.chime", config.getString("sounds.announce.spectator.sound"));
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
                "manhunt.worldengine-tpto-confirm",
                "manhunt.worldengine-tpto-creating",
                "manhunt.worldengine-tpto-success",
                "manhunt.worldengine-tpto-no-world",
                "manhunt.worldengine-tpto-failed",
                "manhunt.status-all-header",
                "manhunt.status-all-entry",
                "manhunt.status-all-empty",
                "manhunt.invalid-instance-id",
                "manhunt.not-in-match",
                "manhunt.console-requires-id",
                "game.join-success",
                "game.join-announce",
                "game.join-invalid-role",
                "game.leave-confirm",
                "game.leave-not-in-match",
                "game.leave-success",
                "game.leave-removed",
                "game.hunter-left",
                "game.speedrunner-left",
                "game.auto-left-bounds",
                "game.auto-left-lobby-world",
                "manhunt.lobby-join-success",
                "manhunt.lobby-leave-success",
                "manhunt.lobby-invalid-id",
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
        assertTrue(messages.getString("game.join-announce", "").contains("{player}"));
        assertTrue(messages.getString("game.join-announce", "").contains("{role}"));
        assertTrue(messages.getString("game.hunter-left", "").contains("{remaining}"));
        assertTrue(messages.getString("game.speedrunner-left", "").contains("{remaining}"));
        assertTrue(messages.getString("manhunt.status-all-entry", "").contains("{id}"));
        assertTrue(messages.getString("manhunt.status-all-entry", "").contains("{duration}"));
        assertTrue(messages.getString("manhunt.status-all-entry", "").contains("{lobby}"));
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
