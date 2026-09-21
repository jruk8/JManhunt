package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.core.JManhuntPlugin;
import com.jruk8.jmanhunt.match.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class CompassProtectionListener implements Listener {
    private final JManhuntPlugin plugin;
    private final CompassManager compass;
    private final GameManager game;

    public CompassProtectionListener(JManhuntPlugin plugin, CompassManager compass, GameManager game) {
        this.plugin = plugin;
        this.compass = compass;
        this.game = game;
    }

    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        if (!compass.mustBeInventory()) {
            return;
        }
        if (compass.isCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!compass.mustBeInventory()) {
            return;
        }
        boolean involvesCompass = compass.isCompass(event.getCurrentItem())
                || compass.isCompass(event.getCursor())
                || (event.getHotbarButton() >= 0
                    && compass.isCompass(event.getWhoClicked().getInventory().getItem(event.getHotbarButton())));
        if (!involvesCompass) {
            return;
        }

        // Allow non-shift-clicks within the player's own inventory
        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getBottomInventory())
                && !event.isShiftClick()) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (!compass.mustBeInventory()) {
            return;
        }
        if (!compass.isCompass(event.getOldCursor())
                && !event.getNewItems().values().stream().anyMatch(compass::isCompass)) {
            return;
        }

        // Cancel if any drag slot is in the top inventory (chest, crafting grid, etc.)
        int topSize = event.getView().getTopInventory().getSize();
        boolean hasTopSlot = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (hasTopSlot) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onMove(InventoryMoveItemEvent event) {
        if (!compass.mustBeInventory()) {
            return;
        }
        if (compass.isCompass(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onPickup(InventoryPickupItemEvent event) {
        if (!compass.mustBeInventory()) {
            return;
        }
        if (compass.isCompass(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onEntityPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!compass.isCompass(event.getItem().getItemStack())) {
            return;
        }
        if (!compass.mayHoldCompass(player)) {
            event.setCancelled(true);
            event.getItem().remove();
            return;
        }
        // Schedule next tick to handle multiple compasses picked up in
        // the same tick; the kept compass takes the picker's role text.
        Bukkit.getScheduler().runTask(plugin, () -> {
            compass.deduplicateCompasses(player);
            compass.refreshCompassIdentity(player);
        });
    }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !compass.isCompass(event.getItem()) || !game.isActive()) {
            return;
        }
        switch (event.getAction()) {
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> compass.handleLeftClick(event.getPlayer());
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> compass.handleRightClick(event.getPlayer());
            default -> { }
        }
    }
}