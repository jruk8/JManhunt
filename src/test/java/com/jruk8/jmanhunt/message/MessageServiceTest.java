package com.jruk8.jmanhunt.message;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MessageService rendering that runs without a Bukkit server. */
class MessageServiceTest {

    @Test
    void debugPrefixTokenResolves() {
        Fixture fixture = messages();

        Component rendered = fixture.messages().componentRaw(
                fixture.config().getDebug().getCellFetched(), Map.of("value", "7"));

        assertEquals("[D] value 7.", plain(rendered));
    }

    @Test
    void regularPrefixStillResolves() {
        Fixture fixture = messages();

        Component rendered = fixture.messages().componentRaw(
                fixture.config().getManhunt().getNotInMatch(), Map.of("value", "7"));

        assertEquals("[T] value 7.", plain(rendered));
    }

    @Test
    void renderLiteralResolvesPrefixInComposedText() {
        MessageService messages = messages().messages();

        // Composed literals (like the separator-wrapped win announcement)
        // carry a raw {prefix} that parse() alone would leave behind.
        Component rendered = messages.renderLiteral(
                "---\n{prefix}<gray>value <white>{value}<gray>.\n---", Map.of("value", "7"));

        assertEquals("---\n[T] value 7.\n---", plain(rendered));
    }

    @Test
    void renderLiteralConvertsLegacyPrefixCodes() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "prefix", "&8[T]&7 ");
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
        MessageService messages = messages().messages();

        assertEquals("hi", plain(messages.parse("&7hi")));
        assertEquals("hi", plain(messages.parse("<gray>hi")));
    }

    @Test
    void sectionCodesConvertToMiniMessageTags() {
        assertEquals("<gray>hi", MessageService.legacyToMiniMessage("§7hi"));
        assertEquals("<#de666e>Hunter",
                MessageService.legacyToMiniMessage("§x§d§e§6§6§6§eHunter"));
        assertEquals("Tom § Jerry", MessageService.legacyToMiniMessage("Tom § Jerry"));
    }

    @Test
    void doubleFormatNeverThrows() {
        MessageService messages = messages().messages();
        String once = messages.formatPlaceholder(messages.roleName(Role.HUNTER));

        assertDoesNotThrow(() -> messages.formatPlaceholder(once));
    }

    @Test
    void blankTreatsNullAndEmptyAsDisabled() {
        MessageService messages = new MessageService();

        assertTrue(messages.blank(null));
        assertTrue(messages.blank(""));
        assertFalse(messages.blank(" "));
        assertFalse(messages.blank("hi"));
    }

    @Test
    void roleNameUsesConfiguredColors() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "role-colors.hunter", "&c");
        ConfigPathMapper.set(config, "role-colors.speedrunner", "&a");
        MessageService messages = new MessageService();
        messages.reload(config);

        assertEquals("&cHunter", messages.roleName(Role.HUNTER));
        assertEquals("&aSpeedrunner", messages.roleName(Role.SPEEDRUNNER));
    }

    @Test
    void roleColorPlaceholdersResolveInTemplates() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "role-colors.hunter", "<red>");
        MessageService messages = new MessageService();
        messages.reload(config);

        Component rendered = messages.renderLiteral(
                "No {role-color-hunter}Hunter<gray> here.", Map.of());

        assertEquals("No Hunter here.", plain(rendered));
    }






    private record Fixture(MessageService messages, MessagesConfig config) {
    }

    private static Fixture messages() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "prefix", "<gray>[T]</gray> ");
        ConfigPathMapper.set(config, "debug.prefix", "<gray>[D]</gray> ");
        // Probe text rides on real keys: the store is typed, so there
        // are no ad-hoc keys to hang fixtures on anymore.
        ConfigPathMapper.set(config, "debug.cell-fetched",
                "{debug-prefix}<gray>value <white>{value}<gray>.");
        ConfigPathMapper.set(config, "manhunt.not-in-match",
                "{prefix}<gray>value <white>{value}<gray>.");
        MessageService messages = new MessageService();
        messages.reload(config);
        return new Fixture(messages, config);
    }

    private static String plain(Component component) {
        StringBuilder text = new StringBuilder(
                component instanceof TextComponent textComponent ? textComponent.content() : "");
        for (Component child : component.children()) {
            text.append(plain(child));
        }
        return text.toString();
    }

    @Test
    void winBlockHasBlankPrefixSeparatorsTitleAndReason() {
        assertEquals("\n{prefix}\n---\ntitle\nreason\n---",
                MessageService.winBlock("---", "title", "reason"));
    }
}
