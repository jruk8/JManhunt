package com.jruk8.jmanhunt.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Forwards inventory events to the menu service.
 *
 * <p>Pre-cancelled events are left to whichever plugin cancelled them; menus
 * are identified by holder, so non-menu inventories pass through untouched.
 */
public final class GuiListener implements Listener {

    private final GuiService guiService;

    public GuiListener(GuiService guiService) {
        this.guiService = guiService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        guiService.handleClick(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        guiService.handleDrag(event);
    }
}
