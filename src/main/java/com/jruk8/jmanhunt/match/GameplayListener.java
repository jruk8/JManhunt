package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.core.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.DimensionEnterTracker;
import com.jruk8.jmanhunt.player.DisconnectDecision;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.WorldEngineService;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
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
    private final LobbyService lobbies;
    private final WorldEngineService worldEngine;
    private long lastVoidRescueWarning;
    private final SpeedrunnerDisconnectTracker disconnects = new SpeedrunnerDisconnectTracker();
    private final Map<UUID, BukkitTask> disconnectTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();
    private final DimensionEnterTracker dimensionEnterTracker = new DimensionEnterTracker();

    public GameplayListener(JManhuntPlugin plugin, PlayerStateStore playerStates, GameManager game,
                            MessageService messages, ConfigService config, SoundService sounds, CompassManager compass,
                            StatsManager stats, LobbyTeleporter lobbyTeleporter,
                            WinConditionEngine winConditionEngine, LobbyService lobbyService,
                            WorldEngineService worldEngine) {
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
        this.lobbies = lobbyService;
        this.worldEngine = worldEngine;
        // Cancel any pending respawn tasks when a match ends so players
        // are not revived during the end sequence or after the match.
        // Dimension-enter tracking is keyed by match, so each finished
        // match is dropped the same way.
        game.addGameEndListener(instance -> {
            cancelAllRespawnTasks();
            dimensionEnterTracker.dropMatch(instance.matchId());
        });
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        playerStates.resetRolesIfAbsent(player);
        // Repair scoreboard teams in case roles and teams drifted apart.
        plugin.roleTeams().sync(player);
        lobbies.assignDefault(player.getUniqueId());

        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isPresent()) {
            // Rejoining a match in progress: active participants keep playing
            // where they left off and are never teleported to the lobby.
            if (playerStates.role(player) == Role.SPEEDRUNNER
                    || playerStates.role(player) == Role.HUNTER) {
                handleRejoin(player);
            }
            return;
        }
        int lobbyId = lobbyIdFor(player.getUniqueId());
        if (lobbyId >= 0 && game.instanceForLobby(lobbyId).isPresent()) {
            // Their lobby has a running match they are not part of (a
            // newcomer or an eliminated player): wait in the lobby as a
            // spectator. Preserve the AFK role; only reset the rest to NONE.
            // With nowhere to wait (engine off or no lobby set), they join
            // the newest running match as a spectator instead.
            if (playerStates.role(player) != Role.AFK && !game.hasLobbyLocation(lobbyId)) {
                if (!game.joinLeastTimeMatch(player)) {
                    playerStates.setRole(player.getUniqueId(), Role.NONE);
                    plugin.roleTeams().sync(player);
                }
            } else {
                if (playerStates.role(player) != Role.AFK) {
                    playerStates.setRole(player.getUniqueId(), Role.NONE);
                    plugin.roleTeams().sync(player);
                }
                // Joining NONEs take spectator gamemode only with the toggle;
                // AFK players keep their role and their gamemode.
                if (playerStates.role(player) == Role.NONE
                        && config.getBoolean("settings.roles.turn-nones-spectator.enabled", false)) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            }
        }
        // Everyone else keeps their queued role and is sent to their lobby.
        if (lobbyId >= 0) {
            lobbyTeleporter.teleportToLobby(List.of(player), lobbyId);
            lobbyTeleporter.setSpawnToLobby(List.of(player), lobbyId);
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.roleTeams().remove(player);
        int lobbyId = lobbyIdFor(player.getUniqueId());
        lobbies.remove(player.getUniqueId());
        if (config.getBoolean("settings.roles.reset-on-leave.enabled", true)
                && playerStates.role(player) != Role.AFK
                && (lobbyId < 0 || game.instanceForLobby(lobbyId).isEmpty())) {
            playerStates.setRole(player.getUniqueId(), Role.NONE);
        }
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isPresent()) {
            Role role = playerStates.role(player);
            if (role == Role.SPEEDRUNNER && playerStates.isActiveSpeedrunner(player.getUniqueId())) {
                handleDisconnect(player, Role.SPEEDRUNNER, match.get().matchId());
            } else if (role == Role.HUNTER) {
                handleDisconnect(player, Role.HUNTER, match.get().matchId());
            }
        }
        game.updateAutostartState();
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isPresent() && playerStates.role(player).isParticipant()) {
            Bukkit.getScheduler().runTask(plugin, () -> compass.giveCompass(player));
        }
        if (match.isEmpty() || !match.get().begun() || !playerStates.role(player).isParticipant()) return;
        long matchId = match.get().matchId();
        game.stateCommands().runEventModifiers("ON_RESPAWN", player, matchId);
        Role role = playerStates.role(player);
        if (role == Role.HUNTER) {
            game.stateCommands().runEventModifiers("ON_HUNTER_RESPAWN", player, matchId);
        } else if (role == Role.SPEEDRUNNER) {
            game.stateCommands().runEventModifiers("ON_SPEEDRUNNER_RESPAWN", player, matchId);
        }
    }
    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        playerStates.recordLastSeen(player, player.getLocation());

        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isEmpty() || !match.get().begun()) return;
        // Begun matches speak through plugin lines only: vanilla death
        // messages would double every announcement.
        event.setDeathMessage(null);
        GameInstance instance = match.get();
        stats.recordDeath(player.getUniqueId());
        Role role = playerStates.role(player);
        // Spawncamp punishment kills re-enter here synchronously: their state
        // changes run intact, but the punishment broadcast already said it.
        boolean quiet = plugin.spawnCamp().isQuietPunishment(player.getUniqueId());
        if (role == Role.SPEEDRUNNER) {
            handleSpeedrunnerDeath(player, instance, quiet);
        } else if (role == Role.HUNTER) {
            handleHunterDeath(player, instance, quiet);
        }
    }

    private void handleSpeedrunnerDeath(Player player, GameInstance instance, boolean quiet) {
        cancelDisconnectTask(player.getUniqueId());
        disconnects.clear(player.getUniqueId());
        playerStates.setSpeedrunnerAlive(player.getUniqueId(), false);
        long matchId = instance.matchId();
        int lives = playerStates.getLives(player.getUniqueId());
        if (lives != -1) {
            playerStates.decrementLives(player.getUniqueId());
            if (playerStates.getLives(player.getUniqueId()) <= 0) {
                // Out of lives: eliminate permanently.
                instance.deactivate(player.getUniqueId());
                if (player.getKiller() != null) {
                    stats.getOrCreate(matchId, player.getKiller().getUniqueId()).finalKills++;
                }
                Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
                if (!quiet) {
                    game.sendToInstance(instance, "game.speedrunner-out-of-lives", Map.of());
                }
                // When nobody remains the win line follows, so no last-died
                // line is sent: the win is the announcement.
                int playerCount = game.activeRunnerCount(instance);
                if (playerCount > 0) {
                    if (!quiet) {
                        game.sendToInstance(instance, "game.speedrunner-death", Map.of("value", Integer.toString(playerCount)));
                    }
                } else {
                    game.finishLater(instance, Role.HUNTER);
                }
                game.playInstanceSound(instance, "game.speedrunner-death");
                return;
            }
            // Lives remain: keep the speedrunner in the game.
            if (!quiet) {
                game.sendToInstance(instance, "game.speedrunner-death", Map.of("value", Integer.toString(game.activeRunnerCount(instance))));
            }
            game.playInstanceSound(instance, "game.speedrunner-death");
            respawnParticipant(player, 0, matchId);
            return;
        }
        // Unlimited lives (-1): never eliminated permanently by lives. The
        // unlimited line fires once per side per match; quiet deaths neither
        // send nor consume it.
        if (!quiet && !instance.runnerUnlimitedAnnounced()) {
            instance.setRunnerUnlimitedAnnounced(true);
            game.sendToInstance(instance, "game.speedrunners-unlimited-lives", Map.of());
        }
        if (!quiet) {
            game.sendToInstance(instance, "game.speedrunner-death", Map.of("value", Integer.toString(game.activeRunnerCount(instance))));
        }
        game.playInstanceSound(instance, "game.speedrunner-death");
        respawnParticipant(player, 0, matchId);
    }

    private void handleHunterDeath(Player player, GameInstance instance, boolean quiet) {
        cancelDisconnectTask(player.getUniqueId());
        disconnects.clear(player.getUniqueId());
        long matchId = instance.matchId();
        int lives = playerStates.getLives(player.getUniqueId());
        boolean delayed = plugin.getConfig().getBoolean("settings.hunter-respawn.enabled", false);
        int delaySeconds = plugin.getConfig().getInt("settings.hunter-respawn.delay-seconds", 60);
        if (lives != -1) {
            playerStates.decrementLives(player.getUniqueId());
            if (playerStates.getLives(player.getUniqueId()) <= 0) {
                // Out of lives: eliminate permanently.
                if (!quiet) {
                    game.sendToInstance(instance, "game.hunter-out-of-lives", Map.of());
                }
                playerStates.setRole(player.getUniqueId(), Role.NONE);
                plugin.roleTeams().sync(player);
                instance.deactivate(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.setGameMode(GameMode.SPECTATOR);
                    compass.removeCompasses(player);
                });
                checkHuntersRemaining(instance);
                game.playInstanceSound(instance, "game.hunter-death");
                return;
            }
        }
        if (!quiet) {
            game.sendToInstance(instance, "game.hunter-death", Map.of());
        }
        game.playInstanceSound(instance, "game.hunter-death");
        if (lives == -1 && !quiet && !instance.hunterUnlimitedAnnounced()) {
            instance.setHunterUnlimitedAnnounced(true);
            game.sendToInstance(instance, "game.hunters-unlimited-lives", Map.of());
        }
        if (delayed && delaySeconds > 0) {
            if (!quiet) {
                game.sendToInstance(instance, "game.hunter-respawn-scheduled",
                        Map.of("player", player.getName(), "seconds", Integer.toString(delaySeconds)));
            }
            respawnParticipant(player, delaySeconds, matchId);
        }
    }

    /**
     * Puts the player in spectator mode, then revives them after the given
     * delay (in seconds). A delay of 0 or less revives them immediately.
     */
    private void respawnParticipant(Player player, int delaySeconds, long matchId) {
        UUID playerId = player.getUniqueId();
        BukkitTask existing = respawnTasks.remove(playerId);
        if (existing != null) existing.cancel();
        Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
        if (delaySeconds <= 0) {
            Bukkit.getScheduler().runTask(plugin, () -> revivePlayer(player, matchId));
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            respawnTasks.remove(playerId);
            if (!game.isActiveInInstance(matchId, playerId)) return;
            revivePlayer(player, matchId);
        }, delaySeconds * 20L);
        respawnTasks.put(playerId, task);
    }

    private void revivePlayer(Player player, long matchId) {
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty() || !match.get().isActive(player.getUniqueId())) return;
        GameInstance instance = match.get();
        if (playerStates.role(player) == Role.SPEEDRUNNER) {
            playerStates.setSpeedrunnerAlive(player.getUniqueId(), true);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        Location respawn = player.getBedSpawnLocation();
        if (respawn != null) player.teleport(respawn);
        else player.teleport(player.getWorld().getSpawnLocation());
        if (playerStates.role(player).isParticipant()) {
            compass.giveCompass(player);
            compass.refreshCompass(player);
            if (playerStates.role(player) == Role.HUNTER) {
                game.sendToInstance(instance, "game.hunter-respawn-imminent", Map.of("player", player.getName()));
            }
        }
        if (!instance.begun() || !playerStates.role(player).isParticipant()) return;
        game.stateCommands().runEventModifiers("ON_RESPAWN", player, matchId);
        Role role = playerStates.role(player);
        if (role == Role.HUNTER) {
            game.stateCommands().runEventModifiers("ON_HUNTER_RESPAWN", player, matchId);
        } else if (role == Role.SPEEDRUNNER) {
            game.stateCommands().runEventModifiers("ON_SPEEDRUNNER_RESPAWN", player, matchId);
        }
    }

    private void checkHuntersRemaining(GameInstance instance) {
        // No last-removed line: the win that follows is the announcement.
        if (game.activeHunterCount(instance) == 0) {
            game.finishLater(instance, Role.SPEEDRUNNER);
        }
    }
    @EventHandler public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isPresent() && playerStates.role(player).isParticipant() && player.getGameMode() != GameMode.SPECTATOR) {
            playerStates.recordLastSeen(player, event.getTo());
        }
        boolean exitWin = winConditionEngine.enabled(Role.SPEEDRUNNER, WinCondition.EXIT_END)
                && match.isPresent() && match.get().begun() && playerStates.role(player) == Role.SPEEDRUNNER
                && playerStates.isActiveSpeedrunner(player.getUniqueId())
                && event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL
                && event.getFrom().getWorld() != null
                && event.getFrom().getWorld().getEnvironment() == World.Environment.THE_END
                && event.getTo() != null && event.getTo().getWorld() != null
                && event.getTo().getWorld().getEnvironment() == World.Environment.NORMAL;
        if (exitWin) {
            // The winner lands outside the cell: skip auto-leave for this hop.
            game.finishLater(match.get(), Role.SPEEDRUNNER);
        } else if (event.getTo() != null) {
            game.autoLeaveIfOutside(player, event.getTo());
        }
    }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        boolean exitWin = winConditionEngine.enabled(Role.SPEEDRUNNER, WinCondition.EXIT_END)
                && match.isPresent() && match.get().begun() && playerStates.role(player) == Role.SPEEDRUNNER
                && playerStates.isActiveSpeedrunner(player.getUniqueId())
                && event.getFrom().getEnvironment() == World.Environment.THE_END
                && player.getWorld().getEnvironment() == World.Environment.NORMAL;
        if (exitWin) {
            // The winner lands outside the cell: skip auto-leave for this hop.
            game.finishLater(match.get(), Role.SPEEDRUNNER);
        } else {
            game.autoLeaveIfOutside(player, player.getLocation());
        }
        if (match.isEmpty() || !match.get().begun() || match.get().ending()
                || !playerStates.role(player).isParticipant()) return;
        long matchId = match.get().matchId();
        World.Environment to = player.getWorld().getEnvironment();
        if (to == World.Environment.NETHER) {
            EnumSet<DimensionEnterTracker.Fire> fire = dimensionEnterTracker.onEnter(
                    DimensionEnterTracker.Dimension.NETHER, player.getUniqueId(), matchId);
            if (!fire.isEmpty()) {
                game.stateCommands().runEventModifiers("ON_NETHER_ENTER", player, matchId);
                if (fire.contains(DimensionEnterTracker.Fire.GLOBAL_FIRST)) {
                    game.stateCommands().runEventModifiers("ON_FIRST_NETHER_ENTER", player, matchId);
                }
            }
        } else if (to == World.Environment.THE_END) {
            EnumSet<DimensionEnterTracker.Fire> fire = dimensionEnterTracker.onEnter(
                    DimensionEnterTracker.Dimension.END, player.getUniqueId(), matchId);
            if (!fire.isEmpty()) {
                game.stateCommands().runEventModifiers("ON_END_ENTER", player, matchId);
                if (fire.contains(DimensionEnterTracker.Fire.GLOBAL_FIRST)) {
                    game.stateCommands().runEventModifiers("ON_FIRST_END_ENTER", player, matchId);
                }
            }
        }
    }
    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (game.instanceOf(event.getPlayer().getUniqueId()).isPresent()
                && playerStates.role(event.getPlayer()).isParticipant()
                && event.getPlayer().getGameMode() != GameMode.SPECTATOR) {
            playerStates.recordLastSeen(event.getPlayer(), event.getTo());
        }
        if (event.getTo() != null && event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        if (event.getTo() != null) {
            game.autoLeaveIfOutside(event.getPlayer(), event.getTo());
        }
    }
    @EventHandler public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        // Void rescue in the lobby world: falling off the platform returns
        // the player to their lobby instead of killing them. Never applies
        // in the game world.
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID
                && plugin.getConfig().getBoolean("world-engine.lobby-world-void-rescue", true)
                && worldEngine.rescuesVoidIn(victim.getWorld())) {
            OptionalInt memberLobby = lobbies.lobbyOf(victim.getUniqueId())
                    .map(lobby -> OptionalInt.of(lobby.id())).orElseGet(OptionalInt::empty);
            Optional<Location> rescue = worldEngine.lobbyRescueLocation(memberLobby);
            if (rescue.isPresent()) {
                event.setCancelled(true);
                victim.teleport(rescue.get());
            } else {
                warnVoidRescueUnset(victim.getName());
            }
            return;
        }

        // NONE, AFK, and spectator players are always invulnerable if
        // configured. Command kills (/kill) still go through: only the
        // pre-start window below blocks those, to avoid glitches.
        if (!playerStates.role(victim).isParticipant()
                && event.getCause() != EntityDamageEvent.DamageCause.SUICIDE
                && config.getBoolean("settings.invulnerability.none-players.enabled", true)) {
            event.setCancelled(true);
            return;
        }

        if (event.getFinalDamage() <= 0) return;
        Optional<GameInstance> victimMatch = game.instanceOf(victim.getUniqueId());
        if (victimMatch.isPresent() && !victimMatch.get().begun()) {
            // A speedrunner hitting a hunter starts the game. The starting
            // hit is felt but never wounds: it begins the match and deals
            // no damage.
            boolean startsGame = event instanceof EntityDamageByEntityEvent byEntity
                    && byEntity.getDamager() instanceof Player attacker
                    && sameMatch(victimMatch.get(), attacker)
                    && playerStates.role(attacker) == Role.SPEEDRUNNER
                    && playerStates.role(victim) == Role.HUNTER;
            // During the pre-start window, all participants are protected from
            // damage (including fall damage from wacky world-engine spawns).
            // Survivable hits still land so knockback registers, then heal
            // back one tick later; lethal hits cancel to prevent pre-start
            // deaths.
            if (playerStates.role(victim).isParticipant()) {
                double finalDamage = event.getFinalDamage();
                if (finalDamage < victim.getHealth()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (victim.isOnline() && victim.getHealth() > 0) {
                            victim.setHealth(Math.min(victim.getMaxHealth(),
                                    victim.getHealth() + finalDamage));
                        }
                    }, 1L);
                } else {
                    event.setCancelled(true);
                }
            }
            if (startsGame) {
                game.beginGame(victimMatch.get());
            }
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity)
                || !(byEntity.getDamager() instanceof Player attacker)) return;
        if (victimMatch.isEmpty() || !victimMatch.get().begun() || !sameMatch(victimMatch.get(), attacker)
                || !playerStates.role(attacker).isParticipant()
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
        stats.getOrCreate(victimMatch.get().matchId(), attacker.getUniqueId()).damage += event.getFinalDamage();
    }
    @EventHandler public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player killer)
                || !playerStates.role(killer).isParticipant()) return;
        Optional<GameInstance> match = game.instanceOf(killer.getUniqueId());
        if (match.isEmpty() || !match.get().begun()) return;
        if (!(event.getEntity() instanceof Player)) {
            // Mob kills only matter for the kill-mob win conditions.
            Role killerRole = playerStates.role(killer);
            if (killerRole == Role.SPEEDRUNNER
                    && winConditionEngine.mobMatches(event.getEntity().getType(), Role.SPEEDRUNNER)) {
                game.finishLater(match.get(), Role.SPEEDRUNNER);
            } else if (killerRole == Role.HUNTER
                    && winConditionEngine.mobMatches(event.getEntity().getType(), Role.HUNTER)) {
                game.finishLater(match.get(), Role.HUNTER);
            }
            return;
        }
        boolean victimIsPlayer = event.getEntity() instanceof Player;
        if (!victimIsPlayer || !playerStates.role(event.getEntity().getUniqueId()).isParticipant()) {
            return;
        }
        if (!sameMatch(match.get(), event.getEntity().getUniqueId())) {
            return;
        }
        long matchId = match.get().matchId();
        stats.getOrCreate(matchId, killer.getUniqueId()).kills++;
        game.stateCommands().runEventModifiers("ON_EVERY_KILL", killer, matchId);
        plugin.spawnCamp().handleKill(matchId, killer, (Player) event.getEntity());
        if (victimIsPlayer) {
            game.stateCommands().runEventModifiers("ON_PLAYER_KILL", killer, matchId);
            Role victimRole = playerStates.role(event.getEntity().getUniqueId());
            if (victimRole == Role.HUNTER) {
                game.stateCommands().runEventModifiers("ON_HUNTER_KILL", killer, matchId);
            } else if (victimRole == Role.SPEEDRUNNER) {
                game.stateCommands().runEventModifiers("ON_SPEEDRUNNER_KILL", killer, matchId);
            }
        }
    }

    @EventHandler public void onPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isEmpty() || !match.get().begun() || !playerStates.role(player).isParticipant()) return;
        Role role = playerStates.role(player);
        // This deprecated event fires before the stack lands in the
        // inventory, so check the picked stack itself for an instant win
        // and keep the inventory scan as a fallback for stacks already
        // held or picked up by other means.
        if (winConditionEngine.materialWins(event.getItem().getItemStack().getType(), role)
                || winConditionEngine.hasItem(player, role)) {
            game.finishLater(match.get(), role);
        }
    }

    @EventHandler public void onAdvancement(org.bukkit.event.player.PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isEmpty() || !match.get().begun() || !playerStates.role(player).isParticipant()) return;
        // Recipe book unlocks are advancement events too, but they are not
        // real advancements: neither triggers nor the advancement win
        // condition should react to them.
        if (event.getAdvancement().getKey().getKey().startsWith("recipes/")) return;
        long matchId = match.get().matchId();
        game.stateCommands().runEventModifiers("ON_EVERY_ADVANCEMENT", player, matchId);
        if (playerStates.role(player) == Role.SPEEDRUNNER
                && winConditionEngine.hasReachAdvancement(player, Role.SPEEDRUNNER)) {
            game.finishLater(match.get(), Role.SPEEDRUNNER);
        } else if (playerStates.role(player) == Role.HUNTER
                && winConditionEngine.hasReachAdvancement(player, Role.HUNTER)) {
            game.finishLater(match.get(), Role.HUNTER);
        }
    }

    private void handleDisconnect(Player player, Role role, long matchId) {
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty()) {
            return;
        }
        String roleKey = role == Role.SPEEDRUNNER ? "speedrunner" : "hunter";
        int maxStrikes = config.getInt("match.disconnect-handling." + roleKey + ".max-strikes", 3);
        int graceSeconds = Math.max(0, config.getInt("match.disconnect-handling." + roleKey + ".reconnect-grace-seconds", 60));
        DisconnectDecision decision =
                disconnects.registerDisconnect(player.getUniqueId(), matchId, maxStrikes);
        cancelDisconnectTask(player.getUniqueId());
        if (decision.forfeit()) {
            eliminateDisconnectedPlayer(player.getUniqueId(), matchId, role);
            return;
        }
        game.sendToInstance(match.get(), "game." + roleKey + "-disconnect-warning", Map.of("seconds", Integer.toString(graceSeconds)));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> eliminateDisconnectedPlayer(player.getUniqueId(), matchId, role), graceSeconds * 20L);
        disconnectTasks.put(player.getUniqueId(), task);
    }

    private void eliminateDisconnectedPlayer(UUID playerId, long matchId, Role role) {
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty() || !match.get().active() || !match.get().isActive(playerId)
                || playerStates.role(playerId) != role) {
            return;
        }
        GameInstance instance = match.get();
        cancelDisconnectTask(playerId);
        disconnects.clear(playerId);

        if (role == Role.SPEEDRUNNER) {
            playerStates.setSpeedrunnerAlive(playerId, false);
        }
        playerStates.setRole(playerId, Role.NONE);
        instance.deactivate(playerId);

        // Disconnect removal always lands on NONE; the toggle decides the
        // gamemode. AFK players are never tracked, so they keep theirs.
        Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer != null && config.getBoolean("settings.roles.turn-nones-spectator.enabled", false)) {
            onlinePlayer.setGameMode(GameMode.SPECTATOR);
        }
        if (onlinePlayer != null) {
            plugin.roleTeams().sync(onlinePlayer);
        }

        String roleKey = role == Role.SPEEDRUNNER ? "speedrunner" : "hunter";
        game.sendToInstance(instance, "game." + roleKey + "-disconnect-removed", Map.of());

        // No last-died lines: the win that follows is the announcement.
        if (role == Role.SPEEDRUNNER) {
            if (game.activeRunnerCount(instance) == 0) {
                game.finishLater(instance, Role.HUNTER);
            }
        } else if (game.activeHunterCount(instance) == 0) {
            game.finishLater(instance, Role.SPEEDRUNNER);
        }

        String soundKey = role == Role.SPEEDRUNNER ? "game.speedrunner-death" : "game.hunter-death";
        game.playInstanceSound(instance, soundKey);
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
        Optional<GameInstance> match = game.instanceOf(playerId);
        if (match.isPresent()) {
            game.sendToInstance(match.get(), "game." + roleKey + "-disconnect-cancelled", Map.of());
        } else {
            messages.broadcast("game." + roleKey + "-disconnect-cancelled");
        }
    }

    /** True when the player actively participates in the given match. */
    private boolean sameMatch(GameInstance instance, Player player) {
        return sameMatch(instance, player.getUniqueId());
    }

    /** True when the player id actively participates in the given match. */
    private boolean sameMatch(GameInstance instance, UUID playerId) {
        return game.instanceOf(playerId).map(match -> match.matchId() == instance.matchId()).orElse(false);
    }

    /**
     * Lobby used for join/quit placement: the player's lobby, else the
     * default lobby (forced to 0 with the world engine off). Negative when
     * the player is lobby-less by configuration.
     */
    private int lobbyIdFor(UUID playerId) {
        return lobbies.lobbyOf(playerId).map(Lobby::id)
                .orElseGet(() -> lobbies.multiLobbyAllowed() ? lobbies.defaultLobbyId() : 0);
    }

    private void cancelDisconnectTask(UUID playerId) {
        BukkitTask task = disconnectTasks.remove(playerId);
        if (task != null) task.cancel();
    }

    /** Warns about a missing rescue destination, at most once every 30 seconds. */
    private void warnVoidRescueUnset(String playerName) {
        long now = System.currentTimeMillis();
        if (now - lastVoidRescueWarning < 30_000L) {
            return;
        }
        lastVoidRescueWarning = now;
        plugin.logger().warning(playerName + " fell into the void in the lobby world, but no lobby location "
                + "is set to rescue them to. Set one with /manhunt worldengine setlobbytp 0.");
    }

    private void cancelAllRespawnTasks() {
        for (BukkitTask task : respawnTasks.values()) {
            task.cancel();
        }
        respawnTasks.clear();
    }
}
