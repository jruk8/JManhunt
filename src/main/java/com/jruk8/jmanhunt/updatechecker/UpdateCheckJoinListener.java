package com.jruk8.jmanhunt.updatechecker;

import com.jruk8.jmanhunt.updatechecker.ports.UpdateCheckNotifier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Repeats a pending update notice to joining admins. */
public final class UpdateCheckJoinListener implements Listener {
    private final UpdateCheckService service;
    private final UpdateCheckNotifier notifier;

    public UpdateCheckJoinListener(UpdateCheckService service, UpdateCheckNotifier notifier) {
        this.service = service;
        this.notifier = notifier;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.pendingUpdate()
                .ifPresent(update -> notifier.notifyPlayer(event.getPlayer(), update.message()));
    }
}
