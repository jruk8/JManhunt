package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.MatchSettings;
import com.jruk8.jmanhunt.match.autostart.AutostartService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** VERSUS autostart countdown style: selection, counts, and rendering. */
class AutostartVersusTest {

    @Test
    void styleFallsBackToVersusUnlessExplicitlySimple() {
        assertTrue(AutostartService.isVersusStyle("VERSUS"));
        assertTrue(AutostartService.isVersusStyle("versus"));
        assertTrue(AutostartService.isVersusStyle(null));
        assertTrue(AutostartService.isVersusStyle(""));
        assertTrue(AutostartService.isVersusStyle("  "));
        assertTrue(AutostartService.isVersusStyle("garbage"));
        assertFalse(AutostartService.isVersusStyle("SIMPLE"));
        assertFalse(AutostartService.isVersusStyle("simple"));
    }

    @Test
    void schemaDefaultsToVersus() {
        assertEquals("VERSUS", new MatchSettings.Autostart().getCountdownStyle());
    }

    @Test
    void namedColorsCloseByName() {
        assertEquals("<red>5</red>", AutostartService.versusCount(5, "<red>"));
        assertEquals("<green>3</green>", AutostartService.versusCount(3, "<green>"));
    }

    @Test
    void hexColorsCloseByName() {
        assertEquals("<#de666e>5</#de666e>", AutostartService.versusCount(5, "<#de666e>"));
        assertEquals("5", AutostartService.versusCount(5, null));
    }

    @Test
    void gradientsCloseByBaseNameAndLegacyCodesConvert() {
        assertEquals("<gradient:red:blue>5</gradient>",
                AutostartService.versusCount(5, "<gradient:red:blue>"));
        assertEquals("&c5</red>", AutostartService.versusCount(5, "&c"));
    }

    @Test
    void versusValuesCarrySecondsAndCounts() {
        Map<String, String> values = AutostartService.versusValues(30, 5, "<red>", 3, "<green>");

        assertEquals(Map.of("seconds", "30", "hunters", "<red>5</red>", "runners", "<green>3</green>"),
                values);
    }

    @Test
    void versusMessageRendersNamedCountsWithoutColorBleed() {
        MessageService messages = messagesWith("<red>", "<green>");

        Map<String, String> values = AutostartService.versusValues(30, 5,
                messages.roleColor(Role.HUNTER), 3, messages.roleColor(Role.SPEEDRUNNER));
        Component rendered = messages.component("manhunt.autostart-versus-eligible", values);

        assertEquals("[T] 3v5 starts in 30s.", plain(rendered));
        String legacy = LegacyComponentSerializer.legacySection().serialize(rendered);
        assertTrue(legacy.contains("§c5§e"), legacy);
        assertTrue(legacy.contains("§a3§e"), legacy);
    }

    @Test
    void versusMessageRendersHexCountsWithoutColorBleed() {
        MessageService messages = new MessageService();
        messages.reload(new MessagesConfig());

        Map<String, String> values = AutostartService.versusValues(30, 5,
                messages.roleColor(Role.HUNTER), 3, messages.roleColor(Role.SPEEDRUNNER));
        Component rendered = messages.component("manhunt.autostart-versus-eligible", values);

        assertEquals("[JManhunt] 3v5 starts in 30s.", plain(rendered));
        String legacy = LegacyComponentSerializer.legacySection().serialize(rendered);
        assertTrue(legacy.contains("3§e"), legacy);
        assertTrue(legacy.contains("5§e"), legacy);
    }

    private static MessageService messagesWith(String hunterColor, String runnerColor) {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "prefix", "<gray>[T]</gray> ");
        ConfigPathMapper.set(config, "role-colors.hunter", hunterColor);
        ConfigPathMapper.set(config, "role-colors.speedrunner", runnerColor);
        MessageService messages = new MessageService();
        messages.reload(config);
        return messages;
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
