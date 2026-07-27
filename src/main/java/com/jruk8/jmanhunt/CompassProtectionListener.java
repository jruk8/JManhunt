package com.jruk8.jmanhunt;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class CompassProtectionListener implements Listener {
    private final CompassManager compass;
    private final GameManager game;

    public CompassProtectionListener(JManhuntPlugin plugin, CompassManager compass, GameManager game) {
        this.compass = compass; this.game = game;
    }

    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        if (compass.isCompass(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (compass.isCompass(event.getCurrentItem()) || compass.isCompass(event.getCursor())
                || compass.isCompass(event.getHotbarButton() >= 0
                ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null)) event.setCancelled(true);
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (compass.isCompass(event.getOldCursor()) || event.getNewItems().values().stream().anyMatch(compass::isCompass)) {
            event.setCancelled(true);
        }
    }
    @EventHandler public void onMove(InventoryMoveItemEvent event) {
        if (compass.isCompass(event.getItem())) event.setCancelled(true);
    }
    @EventHandler public void onPickup(InventoryPickupItemEvent event) {
        if (compass.isCompass(event.getItem().getItemStack())) event.setCancelled(true);
    }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent event) {
        if (compass.isCompass(event.getMainHandItem()) || compass.isCompass(event.getOffHandItem())) event.setCancelled(true);
    }
    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !compass.isCompass(event.getItem()) || !game.isActive()) return;
        compass.handleRightClick(event.getPlayer());
    }
}
