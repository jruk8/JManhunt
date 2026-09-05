package com.jruk8.jmanhunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the code-configurable Challenges announcement printed by
 * /manhunt challenges. Building the components only needs MessageService and
 * the Adventure API, so these tests run without a Bukkit server.
 */
class ChallengesMessageTest {

    @Test
    void messageIsAlwaysParsedAsMiniMessage() {
        // The constant embeds MiniMessage tags, so a server configured with the
        // legacy text format must not turn them into literal tag soup.
        assertTrue(joined("legacy", true)
                .contains("you can find the optional addon here."));
    }

    @Test
    void hereWordOpensTheBuiltByBitResource() {
        ClickEvent expected = ClickEvent.openUrl(ManhuntCommand.CHALLENGES_URL);
        List<Component> children = ManhuntCommand.challengesComponents(messages("minimessage"), true).stream()
                .flatMap(line -> line.children().stream()).toList();
        assertTrue(children.stream().anyMatch(child -> isHereLink(child, expected)));
    }

    @Test
    void statusShowsActiveWhenCompanionPluginIsEnabled() {
        assertTrue(joined("minimessage", true).contains("Challenges status: [ACTIVE]"));
    }

    @Test
    void statusShowsInactiveWhenCompanionPluginIsMissing() {
        assertTrue(joined("minimessage", false).contains("Challenges status: [INACTIVE]"));
    }

    @Test
    void statusValueIsColoredGreenOrRed() {
        List<Component> active = ManhuntCommand.challengesComponents(messages("minimessage"), true).stream()
                .flatMap(line -> line.children().stream()).toList();
        assertTrue(active.stream().anyMatch(child ->
                "ACTIVE".equals(plain(child)) && NamedTextColor.GREEN == child.color()));
        List<Component> inactive = ManhuntCommand.challengesComponents(messages("minimessage"), false).stream()
                .flatMap(line -> line.children().stream()).toList();
        assertTrue(inactive.stream().anyMatch(child ->
                "INACTIVE".equals(plain(child)) && NamedTextColor.RED == child.color()));
    }

    @Test
    void urlPointsAtTheBuiltByBitResource() {
        assertEquals("https://builtbybit.com/resources/jmanhunt-challenges.121574/",
                ManhuntCommand.CHALLENGES_URL);
    }

    private MessageService messages(String format) {
        MessageService messages = new MessageService();
        messages.reload(new YamlConfiguration(), format);
        return messages;
    }

    private String joined(String format, boolean companionEnabled) {
        return ManhuntCommand.challengesComponents(messages(format), companionEnabled).stream()
                .map(this::plain)
                .collect(Collectors.joining(" "));
    }

    private boolean isHereLink(Component child, ClickEvent expected) {
        ClickEvent event = child.clickEvent();
        return "here".equals(plain(child)) && event != null
                && event.action() == ClickEvent.Action.OPEN_URL
                && expected.equals(event);
    }

    private String plain(Component component) {
        StringBuilder text = new StringBuilder(
                component instanceof TextComponent textComponent ? textComponent.content() : "");
        for (Component child : component.children()) {
            text.append(plain(child));
        }
        return text.toString();
    }
}
