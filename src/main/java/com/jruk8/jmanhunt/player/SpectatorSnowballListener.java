package com.jruk8.jmanhunt.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

/** Thin listener delegating spectator snowball rules to the service. */
public class SpectatorSnowballListener implements Listener {

    private final SpectatorSnowballService snowballs;

    public SpectatorSnowballListener(SpectatorSnowballService snowballs) {
        this.snowballs = snowballs;
    }

    /** Tags spectator snowball throws at normal priority. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onLaunch(ProjectileLaunchEvent event) {
        snowballs.onLaunch(event);
    }

    /** Cancels spectator snowball damage at high priority, last word. */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        snowballs.onDamage(event);
    }
}
