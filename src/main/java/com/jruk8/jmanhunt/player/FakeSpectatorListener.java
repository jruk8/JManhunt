package com.jruk8.jmanhunt.player;

import com.jruk8.jmanhunt.match.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Fake spectator protections plus disconnect-safe join and quit handling. */
public final class FakeSpectatorListener implements Listener {
    private final FakeSpectatorService fakes;
    private final PlayerStateStore playerStates;
    private final GameManager game;

    public FakeSpectatorListener(FakeSpectatorService fakes, PlayerStateStore playerStates,
            GameManager game) {
        this.fakes = fakes;
        this.playerStates = playerStates;
        this.game = game;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        fakes.clearDanglingState(player);
        fakes.hideFrom(player);
        if (playerStates.role(player) == Role.SPECTATOR
                && game.instanceOf(player.getUniqueId()).isPresent()) {
            fakes.enable(player);
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        fakes.handleQuit(event.getPlayer());
    }

    @EventHandler public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && fakes.isFakeSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && fakes.isFakeSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player && fakes.isFakeSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && fakes.isFakeSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onPlace(BlockPlaceEvent event) {
        if (fakes.isFakeSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onBreak(BlockBreakEvent event) {
        if (fakes.isFakeSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // Block clicks and physical triggers are gated; right-click air keeps
    // hotbar items usable.
    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (fakes.isFakeSpectator(event.getPlayer())
                && event.getAction() != Action.RIGHT_CLICK_AIR) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onPortal(PlayerPortalEvent event) {
        if (event.isCancelled()) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if ((cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.END_PORTAL)
                && fakes.isFakeSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onDealDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player damager
                && fakes.isFakeSpectator(damager)) {
            event.setCancelled(true);
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter
                && fakes.isFakeSpectator(shooter)) {
            event.setCancelled(true);
        }
    }
}
