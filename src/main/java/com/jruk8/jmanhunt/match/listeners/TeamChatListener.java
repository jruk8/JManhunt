package com.jruk8.jmanhunt.match.listeners;

import com.jruk8.jmanhunt.match.TeamChatService;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Catches prefixed team chat lines. The event fires async, so this
 * only decides synchronously whether the line is consumed; delivery
 * always runs on the main thread. Ineligible senders (wrong role,
 * outside a match, feature off) broadcast raw with the prefix intact.
 */
public final class TeamChatListener implements Listener {
    private final JavaPlugin plugin;
    private final TeamChatService teamChat;

    public TeamChatListener(JavaPlugin plugin, TeamChatService teamChat) {
        this.plugin = plugin;
        this.teamChat = teamChat;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        Optional<String> body = TeamChatService.parsePrefix(
                event.getMessage(), teamChat.prefixes());
        if (body.isEmpty()) {
            return;
        }
        if (!teamChat.enabled() || !teamChat.isEligible(sender)) {
            return;
        }
        event.setCancelled(true);
        if (body.get().isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> teamChat.usage(sender));
            return;
        }
        String text = body.get();
        Bukkit.getScheduler().runTask(plugin, () -> teamChat.deliver(sender, text));
    }
}
