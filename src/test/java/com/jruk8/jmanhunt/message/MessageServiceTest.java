package com.jruk8.jmanhunt.message;

import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MessageService rendering that runs without a Bukkit server. */
class MessageServiceTest {

    @Test
    void debugPrefixTokenResolves() {
        MessageService messages = messages();

        Component rendered = messages.component("debug.probe", Map.of("value", "7"));

        assertEquals("[D] value 7.", plain(rendered));
    }

    @Test
    void regularPrefixStillResolves() {
        MessageService messages = messages();

        Component rendered = messages.component("manhunt.probe", Map.of("value", "7"));

        assertEquals("[T] value 7.", plain(rendered));
    }

    @Test
    void renderLiteralResolvesPrefixInComposedText() {
        MessageService messages = messages();

        // Composed literals (like the separator-wrapped win announcement)
        // carry a raw {prefix} that parse() alone would leave behind.
        Component rendered = messages.renderLiteral(
                "---\n{prefix}<gray>value <white>{value}<gray>.\n---", Map.of("value", "7"));

        assertEquals("---\n[T] value 7.\n---", plain(rendered));
    }

    @Test
    void renderLiteralConvertsLegacyPrefixCodes() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("prefix", "&8[T]&7 ");
        MessageService messages = new MessageService();
        messages.reload(config);

        Component rendered = messages.renderLiteral("{prefix}plain win.", Map.of());

        assertEquals("[T] plain win.", plain(rendered));
    }

    @Test
    void legacyCodesConvertToMiniMessageTags() {
        assertEquals("<gray>hi", MessageService.legacyToMiniMessage("&7hi"));
        assertEquals("<gold>hi", MessageService.legacyToMiniMessage("&6hi"));
        assertEquals("<bold>hi", MessageService.legacyToMiniMessage("&Lhi"));
        assertEquals("Tom & Jerry <red>hi", MessageService.legacyToMiniMessage("Tom & Jerry &chi"));
    }

    @Test
    void parseAppliesLegacyCodes() {
        MessageService messages = messages();

        assertEquals("hi", plain(messages.parse("&7hi")));
        assertEquals("hi", plain(messages.parse("<gray>hi")));
    }

    @Test
    void emptyStringDisablesButWhitespaceDoesNot() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("a.gone", "");
        config.set("a.space", " ");
        config.set("a.kept", "hi");
        MessageService messages = new MessageService();
        messages.reload(config);

        assertTrue(messages.isDisabled("a.gone"));
        assertFalse(messages.isDisabled("a.space"));
        assertFalse(messages.isDisabled("a.kept"));
        assertFalse(messages.isDisabled("a.missing"));
    }

    @Test
    void roleNameUsesConfiguredColorOrDefault() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("role-colors.hunter", "&c");
        MessageService messages = new MessageService();
        messages.reload(config);

        assertEquals("&cHunter", messages.roleName(Role.HUNTER));
        assertEquals("<#74de66>Speedrunner", messages.roleName(Role.SPEEDRUNNER));
    }

    @Test
    void roleColorPlaceholdersResolveInTemplates() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("role-colors.hunter", "<red>");
        MessageService messages = new MessageService();
        messages.reload(config);

        Component rendered = messages.renderLiteral(
                "No {role-color-hunter}Hunter<gray> here.", Map.of());

        assertEquals("No Hunter here.", plain(rendered));
    }

    private static MessageService messages() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("prefix", "<gray>[T]</gray> ");
        config.set("debug.prefix", "<gray>[D]</gray> ");
        config.set("debug.probe", "{debug-prefix}<gray>value <white>{value}<gray>.");
        config.set("manhunt.probe", "{prefix}<gray>value <white>{value}<gray>.");
        MessageService messages = new MessageService();
        messages.reload(config);
        return messages;
    }

    private static String plain(Component component) {
        StringBuilder text = new StringBuilder(
                component instanceof TextComponent textComponent ? textComponent.content() : "");
        for (Component child : component.children()) {
            text.append(plain(child));
        }
        return text.toString();
    }
}
