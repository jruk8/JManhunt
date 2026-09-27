package com.jruk8.jmanhunt.player;

import com.jruk8.jmanhunt.gui.menus.SpectatorMenus;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
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
        if (event.isSneaking()) {
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
        if (!(event.getWhoClicked() instanceof Player player)
                || !toolbar.isDeployed(player)) {
            return;
        }
        if (toolbar.isToolbarItem(event.getCurrentItem())
                || toolbar.isToolbarItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }
}
