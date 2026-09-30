package com.jruk8.jmanhunt.match.listeners;

import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;

/**
 * Pre-start mob shield: mobs cannot target players active in a live
 * match that has not begun yet. Mobs already holding a target from
 * before match start drop it on their next retarget.
 */
public final class PrestartTargetListener implements Listener {
    private final GameManager game;

    public PrestartTargetListener(GameManager game) {
        this.game = game;
    }

    @EventHandler public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player && shields(player)) {
            event.setCancelled(true);
        }
    }

    /** True when the player is active in a live match that never began. */
    boolean shields(Player player) {
        return game.instanceOf(player.getUniqueId())
                .map(GameInstance::begun)
                .map(begun -> !begun)
                .orElse(false);
    }
}
