package com.jruk8.jmanhunt.tutorial;

import com.jruk8.jmanhunt.core.TaskScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Routes tutorial participants' chat into the engine. The event fires
 * async, so this only decides synchronously whether the line is consumed;
 * the engine itself always runs on the main thread. Sessions die quietly
 * when the player disconnects.
 */
public final class TutorialChatListener implements Listener {

    private final TaskScheduler tasks;
    private final TutorialService tutorial;

    public TutorialChatListener(TaskScheduler tasks, TutorialService tutorial) {
        this.tasks = tasks;
        this.tutorial = tutorial;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!tutorial.isInTutorial(player.getUniqueId())) {
            return;
        }
        String trimmed = event.getMessage().strip();
        if (tutorial.isConsumableInput(player.getUniqueId(), trimmed)) {
            event.setCancelled(true);
        }
        tasks.run(() -> tutorial.handleInput(player, trimmed));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tutorial.removeOnQuit(event.getPlayer().getUniqueId());
    }
}
