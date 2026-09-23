package com.jruk8.jmanhunt.updatechecker.jmanhunt;

import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.updatechecker.ports.UpdateCheckNotifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Sends update notices as MiniMessage to permission holders. */
public final class JManhuntUpdateCheckNotifier implements UpdateCheckNotifier {
    /** Admins holding this permission hear about updates. */
    public static final String ADMIN_PERMISSION = "jmanhunt.admin";

    private final MessageService messages;

    public JManhuntUpdateCheckNotifier(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public void notifyAdmins(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            notifyPlayer(player, message);
        }
    }

    @Override
    public void notifyPlayer(Player player, String message) {
        if (player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(messages.miniMessage("<yellow>" + message));
        }
    }
}
