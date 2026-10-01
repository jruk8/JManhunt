package com.jruk8.jmanhunt.message;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.PendingConfirmations;
import com.jruk8.jmanhunt.command.units.HelpUnit;
import com.jruk8.jmanhunt.message.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests for the code-configurable Challenges announcement printed by
 * /manhunt challenges. Building the components only needs MessageService and
 * the Adventure API, so these tests run without a Bukkit server.
 */
class ChallengesMessageTest {

    @Test
    void messageIsParsedAsMiniMessage() {
        // The constant embeds MiniMessage tags, which must render, not
        // print as literal tag soup.
        assertTrue(joined(true)
                .contains("you can find the optional addon here."));
    }

    @Test
    void hereWordOpensTheBuiltByBitResource() {
        ClickEvent expected = ClickEvent.openUrl(HelpUnit.CHALLENGES_URL);
        List<Component> children = HelpUnit.challengesComponents(support(), true).stream()
                .flatMap(line -> line.children().stream()).toList();
        assertTrue(children.stream().anyMatch(child -> isHereLink(child, expected)));
    }

    @Test
    void statusShowsActiveWhenCompanionPluginIsEnabled() {
        assertTrue(joined(true).contains("Challenges status: [ACTIVE]"));
    }

    @Test
    void statusShowsInactiveWhenCompanionPluginIsMissing() {
        assertTrue(joined(false).contains("Challenges status: [INACTIVE]"));
    }

    @Test
    void statusValueIsColoredGreenOrRed() {
        List<Component> active = HelpUnit.challengesComponents(support(), true).stream()
                .flatMap(line -> line.children().stream()).toList();
        assertTrue(active.stream().anyMatch(child ->
                "ACTIVE".equals(plain(child)) && NamedTextColor.GREEN == child.color()));
        List<Component> inactive = HelpUnit.challengesComponents(support(), false).stream()
                .flatMap(line -> line.children().stream()).toList();
        assertTrue(inactive.stream().anyMatch(child ->
                "INACTIVE".equals(plain(child)) && NamedTextColor.RED == child.color()));
    }

    @Test
    void urlPointsAtTheBuiltByBitResource() {
        assertEquals("https://builtbybit.com/resources/jmanhunt-challenges.121574/",
                HelpUnit.CHALLENGES_URL);
    }

    private MessageService messages() {
        MessageService messages = new MessageService();
        messages.reload(new MessagesConfig());
        return messages;
    }

    private CommandSupport support() {
        return new CommandSupport(messages(), mock(SoundService.class),
                new PendingConfirmations());
    }

    private String joined(boolean companionEnabled) {
        return HelpUnit.challengesComponents(support(), companionEnabled).stream()
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
