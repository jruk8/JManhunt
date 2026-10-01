package com.jruk8.jmanhunt.match.listeners;

import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.match.TeamChatService;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Catches prefixed team chat lines. The event fires async, so this
 * only decides synchronously whether the line is consumed; delivery
 * always runs on the main thread. Ineligible senders (wrong role,
 * outside a match, feature off) broadcast raw with the prefix intact.
 */
public final class TeamChatListener implements Listener {
    private final TaskScheduler tasks;
    private final TeamChatService teamChat;

    public TeamChatListener(TaskScheduler tasks, TeamChatService teamChat) {
        this.tasks = tasks;
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
            tasks.run(() -> teamChat.usage(sender));
            return;
        }
        String text = body.get();
        tasks.run(() -> teamChat.deliver(sender, text));
    }
}
