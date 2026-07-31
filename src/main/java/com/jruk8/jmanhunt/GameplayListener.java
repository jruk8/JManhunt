package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;

public final class GameplayListener implements Listener {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final MessageService messages;
    private final ConfigService config;
    private final SoundService sounds;
    private final CompassManager compass;
    private final StatsManager stats;

    public GameplayListener(JManhuntPlugin plugin, PlayerStateStore playerStates, GameManager game,
                            MessageService messages, ConfigService config, SoundService sounds, CompassManager compass,
                            StatsManager stats) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.game = game;
        this.messages = messages;
        this.config = config;
        this.sounds = sounds;
        this.compass = compass;
        this.stats = stats;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { playerStates.resetRolesIfAbsent(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!game.isActive() && config.getBoolSetting("extras.reset-role-on-leave.enabled", false)) {
            playerStates.setRole(player.getUniqueId(), Role.NONE);
        }
        game.updateAutostartState();
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        if (game.isActive() && playerStates.role(event.getPlayer()) == Role.HUNTER) {
            Bukkit.getScheduler().runTask(plugin, () -> compass.giveCompass(event.getPlayer()));
        }
    }
    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        playerStates.recordLastSeen(player, player.getLocation());

        if (!game.isActive() || !game.isGameBegun()) return;
        if (playerStates.role(player) == Role.SPEEDRUNNER) {
            playerStates.setSpeedrunnerAlive(player.getUniqueId(), false);
            if (player.getKiller() != null) stats.getOrCreate(player.getKiller().getUniqueId()).finalKills++;
            Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
            if (Bukkit.getOnlinePlayers().stream().filter(p -> playerStates.role(p) == Role.SPEEDRUNNER)
                    .filter(p -> playerStates.isActiveSpeedrunner(p.getUniqueId())).count() == 0) game.finishLater(Role.HUNTER);

            int playerCount = playerStates.getActiveSpeedrunnerCount();
            if (playerCount > 0) {
                messages.broadcast("game.speedrunner-death", Map.of("value", Integer.toString(playerCount)));
            }
            else {
                messages.broadcast("game.last-speedrunner-death");
            }

            sounds.playGlobalSound("game.speedrunner-death");
        }
        else if (playerStates.role(player) == Role.HUNTER) {
            messages.broadcast("game.hunter-death");
            sounds.playGlobalSound("game.hunter-death");
        }
    }
    @EventHandler public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (game.isActive() && playerStates.role(player) == Role.SPEEDRUNNER) {
            playerStates.recordLastSeen(player, event.getTo());
        }
        if (game.isActive() && game.isGameBegun() && playerStates.role(player) == Role.SPEEDRUNNER
                && playerStates.isActiveSpeedrunner(player.getUniqueId())
                && event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL
                && event.getFrom().getWorld().getEnvironment() == World.Environment.THE_END
                && event.getTo().getWorld().getEnvironment() == World.Environment.NORMAL) {
            game.finishLater(Role.SPEEDRUNNER);
        }
    }
    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (game.isActive() && playerStates.role(event.getPlayer()) == Role.SPEEDRUNNER) {
            playerStates.recordLastSeen(event.getPlayer(), event.getTo());
        }
    }
    @EventHandler public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || event.getFinalDamage() <= 0) return;
        if (game.isActive() && !game.isGameBegun()
                && playerStates.role(victim) == Role.SPEEDRUNNER) {
            event.setCancelled(true);
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity)
                || !(byEntity.getDamager() instanceof Player attacker)) return;
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
