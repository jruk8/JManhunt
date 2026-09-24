package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import java.util.function.Supplier;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Guards the lobby world while lobby protection is on: breaking and
 * placing blocks, interacting, hurting entities, and losing hunger all
 * need jmanhunt.editlobby. Movement, chat, and commands pass through,
 * and void rescue still applies on top of this.
 */
public final class LobbyProtectionService implements Listener {

    public static final String EDIT_PERMISSION = "jmanhunt.editlobby";

    private final JManhuntPlugin plugin;
    private final Supplier<String> lobbyWorldName;

    public LobbyProtectionService(JManhuntPlugin plugin, Supplier<String> lobbyWorldName) {
        this.plugin = plugin;
        this.lobbyWorldName = lobbyWorldName;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (denies(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (denies(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (denies(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (denies(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (denies(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player && denies(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event.getDamager());
        if (attacker != null && denies(attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && protects(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    /** True when the player acts in a protected lobby world without the bypass. */
    boolean denies(Player player) {
        return protects(player.getWorld()) && !player.hasPermission(EDIT_PERMISSION);
    }

    /** True when the world is the lobby world and protection is enabled. */
    boolean protects(World world) {
        if (world == null || !world.getName().equals(lobbyWorldName.get())) {
            return false;
        }
        LobbyConfig config = plugin.lobbyConfig();
        return config != null && config.isProtectedWorld();
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player direct) {
            return direct;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
