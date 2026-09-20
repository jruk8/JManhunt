package com.jruk8.jmanhunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static MessageService messages() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("prefix", "<gray>[T]</gray> ");
        config.set("debug.prefix", "<gray>[D]</gray> ");
        config.set("debug.probe", "{debug-prefix}<gray>value <white>{value}<gray>.");
        config.set("manhunt.probe", "{prefix}<gray>value <white>{value}<gray>.");
        MessageService messages = new MessageService();
        messages.reload(config, "minimessage");
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
