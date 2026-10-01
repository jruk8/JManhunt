package com.jruk8.jmanhunt.message;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Raw value send primitives. Fixtures are inline literals, never schema defaults. */
class MessageRawPrimitivesTest {

    @Test
    void blankMeansNullOrEmptyOnly() {
        MessageService messages = new MessageService();

        assertTrue(messages.blank(null));
        assertTrue(messages.blank(""));
        assertFalse(messages.blank(" "));
        assertFalse(messages.blank("hi"));
    }

    @Test
    void componentRawSubstitutesPrefixAndValues() {
        MessageService messages = messages();

        Component rendered = messages.componentRaw(
                "{prefix}<gray>value <white>{value}<gray>.", Map.of("value", "7"));

        assertEquals("[T] value 7.", plain(rendered));
    }

    @Test
    void messageRawSkipsBlankSendsRest() {
        MessageService messages = messages();
        CommandSender sender = mock(CommandSender.class);

        messages.messageRaw(sender, "", Map.of());
        messages.messageRaw(sender, null, Map.of());
        messages.messageRaw(sender, "{prefix}hi", Map.of());

        verify(sender, times(1)).sendMessage(any(Component.class));
    }

    @Test
    void sendToRawFansOutAndSkipsBlank() {
        MessageService messages = messages();
        Player one = mock(Player.class);
        Player two = mock(Player.class);

        messages.sendToRaw(List.of(one, two), "{prefix}hi", Map.of());

        verify(one).sendMessage(any(Component.class));
        verify(two).sendMessage(any(Component.class));

        messages.sendToRaw(List.of(one), "", Map.of());

        verify(one, times(1)).sendMessage(any(Component.class));
    }

    @Test
    void typedGettersReadFixtureValues() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "prefix", "<gray>[T]</gray> ");
        ConfigPathMapper.set(config, "spectator.no-matches", "inline none");

        assertEquals("<gray>[T]</gray> ", config.getPrefix());
        assertEquals("inline none", config.getSpectator().getNoMatches());
    }

    private static MessageService messages() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "prefix", "<gray>[T]</gray> ");
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
