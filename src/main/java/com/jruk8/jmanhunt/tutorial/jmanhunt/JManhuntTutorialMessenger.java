package com.jruk8.jmanhunt.tutorial.jmanhunt;

import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.tutorial.ports.TutorialMessenger;
import org.bukkit.entity.Player;
import java.util.List;

/**
 * Sends tutorial lines as MiniMessage. Tutorial templates are MiniMessage
 * by design (like the hardcoded challenges footer), so the configured
 * text format does not apply here. Resolves {prefix} from messages.yml
 * and {logo} as the prefix minus its square brackets.
 */
public final class JManhuntTutorialMessenger implements TutorialMessenger {

    private final MessageService messages;

    public JManhuntTutorialMessenger(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public void send(Player player, List<String> lines) {
        String prefix = messages.string("prefix", "");
        String logo = logo(prefix);
        for (String line : lines) {
            String resolved = line.replace("{prefix}", prefix).replace("{logo}", logo);
            player.sendMessage(messages.miniMessage(resolved));
        }
    }

    /** Prefix minus its first square bracket pair and edge space. Pure for tests. */
    static String logo(String prefix) {
        if (prefix == null) {
            return "";
        }
        return prefix.replaceFirst("\\[", "").replaceFirst("\\]", "").trim();
    }
}
