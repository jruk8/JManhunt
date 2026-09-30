package com.jruk8.jmanhunt.player;

import com.jruk8.jmanhunt.gui.menus.SpectatorMenus;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
/** Spectator toolbar clicks, lock breaks, crash recovery, and item guards. */
public final class SpectatorToolbarListener implements Listener {
    private final SpectatorToolbarService toolbar;
    private final SpectatorMenus menus;

    public SpectatorToolbarListener(SpectatorToolbarService toolbar, SpectatorMenus menus) {
        this.toolbar = toolbar;
        this.menus = menus;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        toolbar.handleJoin(event.getPlayer());
    }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Optional<Character> button = toolbar.toolbarButton(event.getItem());
        if (button.isEmpty()) {
            return;
        }
        if (button.get() == 's' && toolbar.isDeployed(player)) {
            // Snowball throws vanilla: the snowball service owns cooldown,
            // restore, and the zero-damage guarantee from here on.
            return;
        }
        // Runs even when the fake-spectator gate already cancelled: the
        // cancellation only stops vanilla behavior, never this handler.
        event.setCancelled(true);
        if (!toolbar.isDeployed(player)) {
            toolbar.handleJoin(player);
            return;
        }
        switch (button.get()) {
            case 'b' -> toolbar.returnToLobby(player);
            case 'p' -> menus.openPlayersMenu(player);
            case 'c' -> {
                if (event.getAction() == Action.RIGHT_CLICK_AIR
                        || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    menus.openLobbiesMenu(player);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler public void onSneak(PlayerToggleSneakEvent event) {
        // First tap only stamps the time (silent); the pair-completing
        // second tap exits the follow with the usual feedback.
        if (event.isSneaking()
                && toolbar.registerSneak(event.getPlayer().getUniqueId(),
                        System.currentTimeMillis())) {
            toolbar.exitFollow(event.getPlayer());
        }
    }

    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        if (toolbar.isDeployed(event.getPlayer())
                && toolbar.isToolbarItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Marker heads never move, even for fake spectators without a
        // deployed toolbar (respawn waits, headstart holds, and the rest
        // keep their own inventories).
        if (toolbar.isSpectatorHead(event.getCurrentItem(), player.getUniqueId())
                || toolbar.isSpectatorHead(event.getCursor(), player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!toolbar.isDeployed(player)) {
            return;
        }
        if (toolbar.isToolbarItem(event.getCurrentItem())
                || toolbar.isToolbarItem(event.getCursor())
                || isToolbarQuickSwap(event, player)) {
            event.setCancelled(true);
            return;
        }
        if (touchesOwnInventory(event, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (toolbar.isDeployed(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (toolbar.isSpectatorHead(event.getCursor(), player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!toolbar.isDeployed(player)) {
            return;
        }
        if (toolbar.isToolbarItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        int topSize = event.getInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * True when a keyboard shortcut would move a toolbar item: the hotbar
     * slot behind a number key, or the offhand behind the swap key, holds
     * a toolbar button.
     */
    private boolean isToolbarQuickSwap(InventoryClickEvent event, Player player) {
        PlayerInventory inventory = player.getInventory();
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            return toolbar.isToolbarItem(inventory.getItem(event.getHotbarButton()));
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            return toolbar.isToolbarItem(inventory.getItemInOffHand());
        }
        return false;
    }

    /**
     * True when the click lands in the player's own inventory (the bottom
     * half, or the whole crafting view). Menu tops are never ours: the
     * menu service owns those clicks.
     */
    private boolean touchesOwnInventory(InventoryClickEvent event, Player player) {
        Inventory clicked = event.getClickedInventory();
        return clicked != null && clicked.equals(player.getInventory());
    }
}
