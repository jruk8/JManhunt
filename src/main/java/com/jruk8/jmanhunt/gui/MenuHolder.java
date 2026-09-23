package com.jruk8.jmanhunt.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder routing inventory events to their menu.
 *
 * <p>The holder carries the menu, so click and drag handlers identify JManhunt
 * menus with one instanceof check and no registry or title matching.
 */
public final class MenuHolder implements InventoryHolder {

    private final Menu menu;
    private Inventory inventory;

    public MenuHolder(Menu menu) {
        this.menu = menu;
    }

    public Menu menu() {
        return menu;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
