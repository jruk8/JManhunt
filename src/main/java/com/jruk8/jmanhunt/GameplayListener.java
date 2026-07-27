package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class GameplayListener implements Listener {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final CompassManager compass;
    private final StatsManager stats;

    public GameplayListener(JManhuntPlugin plugin, PlayerStateStore playerStates, GameManager game,
                            CompassManager compass, StatsManager stats) {
        this.plugin = plugin; this.playerStates = playerStates; this.game = game;
        this.compass = compass; this.stats = stats;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { playerStates.resetRolesIfAbsent(event.getPlayer()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        if (game.isActive() && playerStates.role(event.getPlayer()) == Role.HUNTER) {
            Bukkit.getScheduler().runTask(plugin, () -> compass.giveCompass(event.getPlayer()));
        }
    }
    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        playerStates.recordLastSeen(player, player.getLocation());
        if (!game.isActive() || !game.isGameBegun() || playerStates.role(player) != Role.SPEEDRUNNER) return;
        playerStates.setSpeedrunnerAlive(player.getUniqueId(), false);
        if (player.getKiller() != null) stats.getOrCreate(player.getKiller().getUniqueId()).finalKills++;
        Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
        if (Bukkit.getOnlinePlayers().stream().filter(p -> playerStates.role(p) == Role.SPEEDRUNNER)
                .filter(p -> playerStates.isActiveSpeedrunner(p.getUniqueId())).count() == 0) game.finishLater(Role.HUNTER);
    }
    @EventHandler public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (game.isActive() && playerStates.role(player) == Role.SPEEDRUNNER) {
            playerStates.recordLastSeen(player, event.getTo());
        }
        if (game.isActive() && game.isGameBegun() && playerStates.role(player) == Role.SPEEDRUNNER
                && playerStates.isActiveSpeedrunner(player.getUniqueId())
                && event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) game.finishLater(Role.SPEEDRUNNER);
    }
    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (game.isActive() && playerStates.role(event.getPlayer()) == Role.SPEEDRUNNER) {
            playerStates.recordLastSeen(event.getPlayer(), event.getTo());
        }
    }
    @EventHandler public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)
                || event.getFinalDamage() <= 0) return;
        if (game.isActive() && !game.isGameBegun() && playerStates.role(attacker) == Role.SPEEDRUNNER
                && playerStates.role(victim) == Role.HUNTER) game.beginGame();
        if (!game.isActive() || !game.isGameBegun() || playerStates.role(attacker) == Role.NONE
                || playerStates.role(victim) == Role.NONE) return;
        stats.getOrCreate(attacker.getUniqueId()).damage += event.getFinalDamage();
    }
    @EventHandler public void onEntityDeath(EntityDeathEvent event) {
        if (!game.isActive() || !game.isGameBegun() || !(event.getEntity() instanceof Player victim)
                || event.getEntity().getKiller() == null || playerStates.role(victim) == Role.NONE
                || playerStates.role(event.getEntity().getKiller()) == Role.NONE) return;
        stats.getOrCreate(event.getEntity().getKiller().getUniqueId()).kills++;
    }
}
