package com.jruk8.jmanhunt.message;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the bundled defaults for the start-of-match role announcement.
 * Reads the shipped config.yml and messages.yml directly, so these tests run
 * without a Bukkit server.
 */
class RoleAnnounceDefaultsTest {

    private static YamlConfiguration bundled(String name) {
        var stream = RoleAnnounceDefaultsTest.class.getClassLoader().getResourceAsStream(name);
        return YamlConfiguration.loadConfiguration(
                new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8));
    }

    @Test
    void announceChatAndTitleDefaultToEnabled() {
        YamlConfiguration config = bundled("config.yml");

        assertTrue(config.getBoolean("settings.announce-roles.chat.enabled"));
        assertTrue(config.getBoolean("settings.announce-roles.title.enabled"));
    }

    @Test
    void announceTitleTimingDefaultsToSeconds() {
        YamlConfiguration config = bundled("config.yml");

        assertEquals(0.5, config.getDouble("settings.announce-roles.title.fade-in-seconds"), 0.0001);
        assertEquals(3.0, config.getDouble("settings.announce-roles.title.stay-seconds"), 0.0001);
        assertEquals(0.5, config.getDouble("settings.announce-roles.title.fade-out-seconds"), 0.0001);
    }

    @Test
    void announceSoundsDefaultPerRole() {
        YamlConfiguration config = bundled("config.yml");

        assertTrue(config.getBoolean("sounds.announce.hunter.enabled"));
        assertEquals("entity.wither.break_block", config.getString("sounds.announce.hunter.sound"));
        assertEquals(1.3, config.getDouble("sounds.announce.hunter.pitch"), 0.0001);
        assertEquals(0.8, config.getDouble("sounds.announce.hunter.volume"), 0.0001);

        assertTrue(config.getBoolean("sounds.announce.speedrunner.enabled"));
        assertEquals("block.copper_chest.close", config.getString("sounds.announce.speedrunner.sound"));
        assertEquals(1.0, config.getDouble("sounds.announce.speedrunner.pitch"), 0.0001);
        assertEquals(1.0, config.getDouble("sounds.announce.speedrunner.volume"), 0.0001);
    }

    @Test
    void announceMessagesExist() {
        YamlConfiguration messages = bundled("messages.yml");

        assertTrue(messages.getString("manhunt.role-announce-chat", "").contains("{role}"));
        assertTrue(messages.getString("manhunt.role-announce-title", "").contains("{role}"));
        assertEquals("<red>Kill all Speedrunners",
                messages.getString("manhunt.role-announce-subtitle-hunter"));
        assertEquals("<green>Outsmart the Hunters",
                messages.getString("manhunt.role-announce-subtitle-speedrunner"));
    }
}
