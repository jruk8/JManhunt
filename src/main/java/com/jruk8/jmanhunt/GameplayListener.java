package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GameplayListener implements Listener {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final MessageService messages;
    private final ConfigService config;
    private final SoundService sounds;
    private final CompassManager compass;
    private final StatsManager stats;
    private final LobbyTeleporter lobbyTeleporter;
    private final WinConditionEngine winConditionEngine;
    private final SpeedrunnerDisconnectTracker disconnects = new SpeedrunnerDisconnectTracker();
    private final Map<UUID, BukkitTask> disconnectTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();
    private final Set<UUID> enteredNether = new HashSet<>();
    private final Set<UUID> enteredEnd = new HashSet<>();

    public GameplayListener(JManhuntPlugin plugin, PlayerStateStore playerStates, GameManager game,
                            MessageService messages, ConfigService config, SoundService sounds, CompassManager compass,
                            StatsManager stats, LobbyTeleporter lobbyTeleporter,
                            WinConditionEngine winConditionEngine) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.game = game;
        this.messages = messages;
        this.config = config;
        this.sounds = sounds;
        this.compass = compass;
        this.stats = stats;
        this.lobbyTeleporter = lobbyTeleporter;
        this.winConditionEngine = winConditionEngine;
        // Cancel any pending respawn tasks when the match ends so players
        // are not revived during the end sequence or after the match.
        game.addGameEndListener(this::cancelAllRespawnTasks);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        playerStates.resetRolesIfAbsent(player);

        if (game.isActive()) {
            if (!playerStates.isMatchParticipant(player.getUniqueId())) {
                // Preserve AFK role; only reset non-AFK non-participants to NONE
                if (playerStates.role(player) != Role.AFK) {
                    playerStates.setRole(player.getUniqueId(), Role.NONE);
                }
                playerStates.markMatchSpectator(player.getUniqueId());
                if (config.getBoolean("settings.set-none-gamemode-spectator.enabled", true)) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
                // Non-participants are sent to the lobby and respawn there
                lobbyTeleporter.teleportToLobby(List.of(player));
                lobbyTeleporter.setSpawnToLobby(List.of(player));
            } else if (playerStates.role(player) == Role.SPEEDRUNNER
                    || playerStates.role(player) == Role.HUNTER) {
                handleRejoin(player);
            }
            // Active match participants (hunters/speedrunners) are not
            // teleported to the lobby; they rejoin the match in progress
        } else {
            // No active match: send everyone to the lobby
            lobbyTeleporter.teleportToLobby(List.of(player));
            lobbyTeleporter.setSpawnToLobby(List.of(player));
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!game.isActive() && config.getBoolean("settings.reset-role-on-leave.enabled", false)
                && playerStates.role(player) != Role.AFK) {
            playerStates.setRole(player.getUniqueId(), Role.NONE);
        }
        if (game.isActive() && playerStates.isMatchParticipant(player.getUniqueId())) {
            Role role = playerStates.role(player);
            if (role == Role.SPEEDRUNNER && playerStates.isActiveSpeedrunner(player.getUniqueId())) {
                handleDisconnect(player, Role.SPEEDRUNNER);
            } else if (role == Role.HUNTER) {
                handleDisconnect(player, Role.HUNTER);
            }
        }
        game.updateAutostartState();
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (game.isActive() && playerStates.role(player) == Role.HUNTER) {
            Bukkit.getScheduler().runTask(plugin, () -> compass.giveCompass(player));
        }
        if (!game.isActive() || !game.isGameBegun() || !playerStates.role(player).isParticipant()) return;
        game.stateCommands().runEventModifiers("ON_RESPAWN", player);
        Role role = playerStates.role(player);
        if (role == Role.HUNTER) {
            game.stateCommands().runEventModifiers("ON_HUNTER_RESPAWN", player);
        } else if (role == Role.SPEEDRUNNER) {
            game.stateCommands().runEventModifiers("ON_SPEEDRUNNER_RESPAWN", player);
        }
    }
    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        playerStates.recordLastSeen(player, player.getLocation());

        if (!game.isActive() || !game.isGameBegun()) return;
        stats.recordDeath(player.getUniqueId());
        Role role = playerStates.role(player);
        if (role == Role.SPEEDRUNNER) {
            handleSpeedrunnerDeath(player);
        } else if (role == Role.HUNTER) {
            handleHunterDeath(player);
        }
    }

    private void handleSpeedrunnerDeath(Player player) {
        cancelDisconnectTask(player.getUniqueId());
        disconnects.clear(player.getUniqueId());
        playerStates.setSpeedrunnerAlive(player.getUniqueId(), false);
        int lives = playerStates.getLives(player.getUniqueId());
        if (lives != -1) {
            playerStates.decrementLives(player.getUniqueId());
            if (playerStates.getLives(player.getUniqueId()) <= 0) {
                // Out of lives: eliminate permanently.
                if (player.getKiller() != null) stats.getOrCreate(player.getKiller().getUniqueId()).finalKills++;
                Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
                messages.broadcast("game.speedrunner-out-of-lives");
                if (Bukkit.getOnlinePlayers().stream().filter(p -> playerStates.role(p) == Role.SPEEDRUNNER)
                        .filter(p -> playerStates.isActiveSpeedrunner(p.getUniqueId())).count() == 0) game.finishLater(Role.HUNTER);
                int playerCount = playerStates.getActiveSpeedrunnerCount();
                if (playerCount > 0) {
                    messages.broadcast("game.speedrunner-death", Map.of("value", Integer.toString(playerCount)));
                } else {
                    messages.broadcast("game.last-speedrunner-death");
                }
                sounds.playGlobalSound("game.speedrunner-death");
                return;
            }
            // Lives remain: keep the speedrunner in the game.
            messages.broadcast("game.speedrunner-death", Map.of("value", Integer.toString(playerStates.getActiveSpeedrunnerCount())));
            sounds.playGlobalSound("game.speedrunner-death");
            respawnParticipant(player, 0);
            return;
        }
        // Unlimited lives (-1): never eliminated permanently by lives.
        messages.broadcast("game.speedrunners-unlimited-lives");
        messages.broadcast("game.speedrunner-death", Map.of("value", Integer.toString(playerStates.getActiveSpeedrunnerCount())));
        sounds.playGlobalSound("game.speedrunner-death");
        respawnParticipant(player, 0);
    }

    private void handleHunterDeath(Player player) {
        cancelDisconnectTask(player.getUniqueId());
        disconnects.clear(player.getUniqueId());
        int lives = playerStates.getLives(player.getUniqueId());
        boolean delayed = plugin.getConfig().getBoolean("settings.hunter-respawn.enabled", false);
        int delaySeconds = plugin.getConfig().getInt("settings.hunter-respawn.delay-seconds", 60);
        if (lives != -1) {
            playerStates.decrementLives(player.getUniqueId());
            if (playerStates.getLives(player.getUniqueId()) <= 0) {
                // Out of lives: eliminate permanently.
                messages.broadcast("game.hunter-out-of-lives");
                playerStates.setRole(player.getUniqueId(), Role.NONE);
                playerStates.removeMatchParticipant(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.setGameMode(GameMode.SPECTATOR);
                    compass.removeCompasses(player);
                });
                checkHuntersRemaining();
                sounds.playGlobalSound("game.hunter-death");
                return;
            }
        }
        messages.broadcast("game.hunter-death");
        sounds.playGlobalSound("game.hunter-death");
        if (lives == -1) {
            messages.broadcast("game.hunters-unlimited-lives");
        }
        if (delayed && delaySeconds > 0) {
            messages.broadcast("game.hunter-respawn-scheduled",
                    Map.of("player", player.getName(), "seconds", Integer.toString(delaySeconds)));
            respawnParticipant(player, delaySeconds);
        }
    }

    /**
     * Puts the player in spectator mode, then revives them after the given
     * delay (in seconds). A delay of 0 or less revives them immediately.
     */
    private void respawnParticipant(Player player, int delaySeconds) {
        UUID playerId = player.getUniqueId();
        BukkitTask existing = respawnTasks.remove(playerId);
        if (existing != null) existing.cancel();
        Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
        if (delaySeconds <= 0) {
            Bukkit.getScheduler().runTask(plugin, () -> revivePlayer(player));
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            respawnTasks.remove(playerId);
            if (!game.isActive() || !playerStates.isMatchParticipant(playerId)) return;
            revivePlayer(player);
        }, delaySeconds * 20L);
        respawnTasks.put(playerId, task);
    }

    private void revivePlayer(Player player) {
        if (playerStates.role(player) == Role.SPEEDRUNNER) {
            playerStates.setSpeedrunnerAlive(player.getUniqueId(), true);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        Location respawn = player.getBedSpawnLocation();
        if (respawn != null) player.teleport(respawn);
        else player.teleport(player.getWorld().getSpawnLocation());
        if (playerStates.role(player) == Role.HUNTER) {
            compass.giveCompass(player);
            compass.refreshCompass(player);
            messages.broadcast("game.hunter-respawn-imminent", Map.of("player", player.getName()));
        }
        if (!game.isActive() || !game.isGameBegun() || !playerStates.role(player).isParticipant()) return;
        game.stateCommands().runEventModifiers("ON_RESPAWN", player);
        Role role = playerStates.role(player);
        if (role == Role.HUNTER) {
            game.stateCommands().runEventModifiers("ON_HUNTER_RESPAWN", player);
        } else if (role == Role.SPEEDRUNNER) {
            game.stateCommands().runEventModifiers("ON_SPEEDRUNNER_RESPAWN", player);
        }
    }

    private void checkHuntersRemaining() {
        int hunterCount = (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> playerStates.role(p) == Role.HUNTER)
                .filter(p -> playerStates.isMatchParticipant(p.getUniqueId()))
                .count();
        if (hunterCount == 0) {
            messages.broadcast("game.last-hunter-removed");
            game.finishLater(Role.SPEEDRUNNER);
        }
    }
    @EventHandler public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (game.isActive() && playerStates.role(player).isParticipant() && player.getGameMode() != GameMode.SPECTATOR) {
            playerStates.recordLastSeen(player, event.getTo());
        }
        if (winConditionEngine.isExitEndEnabled()
                && game.isActive() && game.isGameBegun() && playerStates.role(player) == Role.SPEEDRUNNER
                && playerStates.isActiveSpeedrunner(player.getUniqueId())
                && event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL
                && event.getFrom().getWorld() != null
                && event.getFrom().getWorld().getEnvironment() == World.Environment.THE_END
                && event.getTo() != null && event.getTo().getWorld() != null
                && event.getTo().getWorld().getEnvironment() == World.Environment.NORMAL) {
            game.finishLater(Role.SPEEDRUNNER);
        }
    }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (winConditionEngine.isExitEndEnabled()
                && game.isActive() && game.isGameBegun() && playerStates.role(player) == Role.SPEEDRUNNER
                && playerStates.isActiveSpeedrunner(player.getUniqueId())
                && event.getFrom().getEnvironment() == World.Environment.THE_END
                && player.getWorld().getEnvironment() == World.Environment.NORMAL) {
            game.finishLater(Role.SPEEDRUNNER);
        }
        if (!game.isActive() || !game.isGameBegun() || !playerStates.role(player).isParticipant()) return;
        World.Environment to = player.getWorld().getEnvironment();
        if (to == World.Environment.NETHER && enteredNether.add(player.getUniqueId())) {
            game.stateCommands().runEventModifiers("ON_FIRST_ENTER_NETHER", player);
        } else if (to == World.Environment.THE_END && enteredEnd.add(player.getUniqueId())) {
            game.stateCommands().runEventModifiers("ON_FIRST_ENTER_END", player);
        }
    }
    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (game.isActive() && playerStates.role(event.getPlayer()).isParticipant()
                && event.getPlayer().getGameMode() != GameMode.SPECTATOR) {
            playerStates.recordLastSeen(event.getPlayer(), event.getTo());
        }
    }
    @EventHandler public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        // NONE and AFK players are always invulnerable if configured
        if (!playerStates.role(victim).isParticipant()
                && config.getBoolean("settings.invulnerability.none-players.enabled", true)) {
            event.setCancelled(true);
            return;
        }

        if (event.getFinalDamage() <= 0) return;
        if (game.isActive() && !game.isGameBegun()) {
            // A speedrunner hitting a hunter starts the game. That specific hit
            // is allowed through so the first hit actually deals damage
            // (fixes the first-hit-deals-no-damage bug).
            boolean startsGame = event instanceof EntityDamageByEntityEvent byEntity
                    && byEntity.getDamager() instanceof Player attacker
                    && playerStates.role(attacker) == Role.SPEEDRUNNER
                    && playerStates.role(victim) == Role.HUNTER;
            // During the pre-start window, all participants are protected from
            // damage (including fall damage from wacky world-engine spawns).
            if (playerStates.role(victim).isParticipant() && !startsGame) {
                event.setCancelled(true);
            }
            if (startsGame) {
                game.beginGame();
            }
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity)
                || !(byEntity.getDamager() instanceof Player attacker)) return;
        if (!game.isActive() || !game.isGameBegun() || !playerStates.role(attacker).isParticipant()
                || !playerStates.role(victim).isParticipant()) return;
        // Friendly fire: participants cannot damage their own team unless
        // enabled for their role. This only applies once the game has begun,
        // so it is never active during the pre-start window.
        if (playerStates.role(attacker) == playerStates.role(victim)) {
            boolean friendlyFire = playerStates.role(attacker) == Role.HUNTER
                    ? config.getBoolean("settings.friendly-fire.hunter", false)
                    : config.getBoolean("settings.friendly-fire.speedrunner", false);
            if (!friendlyFire) {
                event.setCancelled(true);
                return;
            }
        }
        stats.getOrCreate(attacker.getUniqueId()).damage += event.getFinalDamage();
    }
    @EventHandler public void onEntityDeath(EntityDeathEvent event) {
        if (!game.isActive() || !game.isGameBegun() || event.getEntity().getKiller() == null
                || !(event.getEntity().getKiller() instanceof Player killer)
                || !playerStates.role(killer).isParticipant()) return;
        boolean victimIsPlayer = event.getEntity() instanceof Player;
        if (!victimIsPlayer || !playerStates.role(event.getEntity().getUniqueId()).isParticipant()) {
            return;
        }
        stats.getOrCreate(killer.getUniqueId()).kills++;
        game.stateCommands().runEventModifiers("ON_EVERY_KILL", killer);
        if (victimIsPlayer) {
            game.stateCommands().runEventModifiers("ON_PLAYER_KILL", killer);
            Role victimRole = playerStates.role(event.getEntity().getUniqueId());
            if (victimRole == Role.HUNTER) {
                game.stateCommands().runEventModifiers("ON_HUNTER_KILL", killer);
            } else if (victimRole == Role.SPEEDRUNNER) {
                game.stateCommands().runEventModifiers("ON_SPEEDRUNNER_KILL", killer);
            }
        }
    }

    @EventHandler public void onPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        if (!game.isActive() || !game.isGameBegun() || !playerStates.role(player).isParticipant()) return;
        if (winConditionEngine.hasAcquireItem(player)) {
            game.finishLater(Role.SPEEDRUNNER);
        }
    }

    @EventHandler public void onAdvancement(org.bukkit.event.player.PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        if (!game.isActive() || !game.isGameBegun() || !playerStates.role(player).isParticipant()) return;
        game.stateCommands().runEventModifiers("ON_EVERY_ADVANCEMENT", player);
        if (winConditionEngine.hasReachAdvancement(player)) {
            game.finishLater(Role.SPEEDRUNNER);
        }
    }

    private void handleDisconnect(Player player, Role role) {
        String roleKey = role == Role.SPEEDRUNNER ? "speedrunner" : "hunter";
        int maxStrikes = config.getInt("disconnect-handling." + roleKey + ".max-strikes", 3);
        int graceSeconds = Math.max(0, config.getInt("disconnect-handling." + roleKey + ".reconnect-grace-seconds", 60));
        SpeedrunnerDisconnectTracker.Decision decision =
                disconnects.registerDisconnect(player.getUniqueId(), game.matchId(), maxStrikes);
        cancelDisconnectTask(player.getUniqueId());
        if (decision.forfeit()) {
            eliminateDisconnectedPlayer(player.getUniqueId(), game.matchId(), role);
            return;
        }
        messages.broadcast("game." + roleKey + "-disconnect-warning", Map.of("seconds", Integer.toString(graceSeconds)));
        long currentMatchId = game.matchId();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> eliminateDisconnectedPlayer(player.getUniqueId(), currentMatchId, role), graceSeconds * 20L);
        disconnectTasks.put(player.getUniqueId(), task);
    }

    private void eliminateDisconnectedPlayer(UUID playerId, long matchId, Role role) {
        if (!game.isActive() || game.matchId() != matchId || !playerStates.isMatchParticipant(playerId)
                || playerStates.role(playerId) != role) {
            return;
        }
        cancelDisconnectTask(playerId);
        disconnects.clear(playerId);

        if (role == Role.SPEEDRUNNER) {
            playerStates.setSpeedrunnerAlive(playerId, false);
        }
        playerStates.setRole(playerId, Role.NONE);
        playerStates.removeMatchParticipant(playerId);

        Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer != null && config.getBoolean("settings.set-none-gamemode-spectator.enabled", true)) {
            onlinePlayer.setGameMode(GameMode.SPECTATOR);
        }

        String roleKey = role == Role.SPEEDRUNNER ? "speedrunner" : "hunter";
        messages.broadcast("game." + roleKey + "-disconnect-removed");

        if (role == Role.SPEEDRUNNER) {
            int playerCount = playerStates.getActiveSpeedrunnerCount();
            if (playerCount == 0) {
                messages.broadcast("game.last-speedrunner-death");
                game.finishLater(Role.HUNTER);
            }
        } else {
            int hunterCount = (int) Bukkit.getOnlinePlayers().stream()
                    .filter(p -> playerStates.role(p) == Role.HUNTER)
                    .filter(p -> playerStates.isMatchParticipant(p.getUniqueId()))
                    .count();
            if (hunterCount == 0) {
                messages.broadcast("game.last-hunter-removed");
                game.finishLater(Role.SPEEDRUNNER);
            }
        }

        String soundKey = role == Role.SPEEDRUNNER ? "game.speedrunner-death" : "game.hunter-death";
        sounds.playGlobalSound(soundKey);
    }

    private void handleRejoin(Player player) {
        UUID playerId = player.getUniqueId();
        if (!disconnectTasks.containsKey(playerId)) {
            return;
        }
        cancelDisconnectTask(playerId);
        // Strikes are intentionally NOT cleared here so that repeated
        // disconnect/reconnect cycles accumulate toward the max-strikes limit.
        Role role = playerStates.role(player);
        String roleKey = role == Role.SPEEDRUNNER ? "speedrunner" : "hunter";
        messages.broadcast("game." + roleKey + "-disconnect-cancelled");
    }

    private void cancelDisconnectTask(UUID playerId) {
        BukkitTask task = disconnectTasks.remove(playerId);
        if (task != null) task.cancel();
    }

    private void cancelAllRespawnTasks() {
        for (BukkitTask task : respawnTasks.values()) {
            task.cancel();
        }
        respawnTasks.clear();
    }
}
