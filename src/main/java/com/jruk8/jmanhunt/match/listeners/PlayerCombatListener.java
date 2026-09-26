package com.jruk8.jmanhunt.match.listeners;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.WorldEngineService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.WinConditionEngine;

/** Deaths, damage, kills, and item/advancement win triggers. */
public final class PlayerCombatListener implements Listener {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final ConfigService config;
    private final CompassManager compass;
    private final StatsManager stats;
    private final LobbyService lobbies;
    private final WorldEngineService worldEngine;
    private final WinConditionEngine winConditionEngine;
    private final PlayerRespawnListener respawn;
    private final SpeedrunnerDisconnectTracker disconnects;
    private final Map<UUID, BukkitTask> disconnectTasks;
    private long lastVoidRescueWarning;

    public PlayerCombatListener(JManhuntPlugin plugin, PlayerStateStore playerStates, GameManager game,
            ConfigService config, CompassManager compass, StatsManager stats, LobbyService lobbies,
            WorldEngineService worldEngine, WinConditionEngine winConditionEngine,
            PlayerRespawnListener respawn, SpeedrunnerDisconnectTracker disconnects,
            Map<UUID, BukkitTask> disconnectTasks) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.game = game;
        this.config = config;
        this.compass = compass;
        this.stats = stats;
        this.lobbies = lobbies;
        this.worldEngine = worldEngine;
        this.winConditionEngine = winConditionEngine;
        this.respawn = respawn;
        this.disconnects = disconnects;
        this.disconnectTasks = disconnectTasks;
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        playerStates.recordLastSeen(player, player.getLocation());

        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isEmpty() || !match.get().begun()) {
            return;
        }
        // Begun matches speak through plugin lines only: vanilla death
        // messages would double every announcement.
        event.setDeathMessage(null);
        GameInstance instance = match.get();
        stats.recordDeath(player.getUniqueId());
        stats.getOrCreate(instance.matchId(), player.getUniqueId()).deaths++;
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
        PlayerConnectionListener.cancelDisconnectTask(disconnectTasks, player.getUniqueId());
        disconnects.clear(player.getUniqueId());
        playerStates.setSpeedrunnerAlive(player.getUniqueId(), false);
        long matchId = instance.matchId();
        int lives = playerStates.getLives(player.getUniqueId());
        int delaySeconds = PlayerRespawnListener.effectiveRespawnDelay(
                config.getBoolean("settings.players.respawn.speedrunner.enabled", false),
                config.getInt("settings.players.respawn.speedrunner.delay-seconds", 60));
        if (lives != -1) {
            playerStates.decrementLives(player.getUniqueId());
            if (playerStates.getLives(player.getUniqueId()) <= 0) {
                eliminateSpeedrunner(player, instance, quiet, matchId);
                return;
            }
            // Lives remain: keep the speedrunner in the game.
            surviveRunnerDeath(player, instance, quiet, matchId, delaySeconds);
            return;
        }
        handleUnlimitedRunnerDeath(player, instance, quiet, matchId, delaySeconds);
    }

    /** Eliminates a hunter out of lives, mirroring the speedrunner path. */
    private void eliminateHunterOutOfLives(Player player, GameInstance instance, boolean quiet,
            long matchId) {
        // A speedrunner finishing the hunter earns the final kill,
        // mirroring the speedrunner elimination.
        creditHunterFinalKill(matchId, player);
        if (!quiet) {
            game.sendToInstance(instance, "game.hunter-out-of-lives", Map.of());
        }
        playerStates.setRole(player.getUniqueId(), Role.NONE);
        plugin.roleTeams().sync(player);
        instance.deactivate(player.getUniqueId());
        game.flagStore().removePlayer(matchId, player.getName());
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.setGameMode(GameMode.SPECTATOR);
            compass.removeCompasses(player);
        });
        checkHuntersRemaining(instance);
        game.playInstanceSound(instance, "game.hunter-death");
    }

    /** Eliminates a speedrunner out of lives and finishes when none remain. */
    private void eliminateSpeedrunner(Player player, GameInstance instance, boolean quiet, long matchId) {
        // Out of lives: eliminate permanently. Only an opposite-role
        // killer earns the final kill: same-role finishes never count.
        instance.deactivate(player.getUniqueId());
        game.flagStore().removePlayer(matchId, player.getName());
        Player finalKiller = player.getKiller();
        if (finalKiller != null && playerStates.role(finalKiller.getUniqueId()) == Role.HUNTER) {
            stats.getOrCreate(matchId, finalKiller.getUniqueId()).finalKills++;
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
                game.sendToInstance(instance, "game.speedrunner-death",
                        Map.of("value", Integer.toString(playerCount)));
            }
        } else {
            game.finishLater(instance, Role.HUNTER);
        }
        game.playInstanceSound(instance, "game.speedrunner-death");
    }

    /** Announces a survived speedrunner death and schedules the respawn. */
    private void surviveRunnerDeath(Player player, GameInstance instance, boolean quiet,
            long matchId, int delaySeconds) {
        if (!quiet) {
            // The dying runner is already flagged not-alive but will respawn, so count them.
            int remaining = game.activeRunnerCount(instance) + 1;
            game.sendToInstance(instance, "game.speedrunner-death",
                    Map.of("value", Integer.toString(remaining)));
        }
        game.playInstanceSound(instance, "game.speedrunner-death");
        respawn.scheduleRespawn(player, instance, quiet, delaySeconds,
                "game.speedrunner-respawn-scheduled", matchId);
    }

    /** Handles a speedrunner death with unlimited lives. */
    private void handleUnlimitedRunnerDeath(Player player, GameInstance instance, boolean quiet,
            long matchId, int delaySeconds) {
        // Unlimited lives (-1): never eliminated permanently by lives. The
        // unlimited line fires once per side per match; quiet deaths neither
        // send nor consume it.
        if (!quiet && !instance.runnerUnlimitedAnnounced()) {
            instance.setRunnerUnlimitedAnnounced(true);
            game.sendToInstance(instance, "game.speedrunners-unlimited-lives", Map.of());
        }
        surviveRunnerDeath(player, instance, quiet, matchId, delaySeconds);
    }

    private void handleHunterDeath(Player player, GameInstance instance, boolean quiet) {
        PlayerConnectionListener.cancelDisconnectTask(disconnectTasks, player.getUniqueId());
        disconnects.clear(player.getUniqueId());
        long matchId = instance.matchId();
        int lives = playerStates.getLives(player.getUniqueId());
        int delaySeconds = PlayerRespawnListener.effectiveRespawnDelay(
                config.getBoolean("settings.players.respawn.hunter.enabled", false),
                config.getInt("settings.players.respawn.hunter.delay-seconds", 60));
        if (lives != -1) {
            playerStates.decrementLives(player.getUniqueId());
            if (playerStates.getLives(player.getUniqueId()) <= 0) {
                eliminateHunterOutOfLives(player, instance, quiet, matchId);
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
        // Undelayed hunters respawn through vanilla mechanics; only a
        // positive delay routes them through the spectator revive.
        if (delaySeconds > 0) {
            respawn.scheduleRespawn(player, instance, quiet, delaySeconds,
                    "game.hunter-respawn-scheduled", matchId);
        }
    }

    private void checkHuntersRemaining(GameInstance instance) {
        // No last-removed line: the win that follows is the announcement.
        if (game.activeHunterCount(instance) == 0) {
            game.finishLater(instance, Role.SPEEDRUNNER);
        }
    }

    @EventHandler public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (rescueLobbyVoid(event, victim)) {
            return;
        }

        // NONE, AFK, and spectator players are always invulnerable if
        // configured. Command kills (/kill) still go through: only the
        // pre-start window below blocks those, to avoid glitches.
        if (!playerStates.role(victim).isParticipant()
                && event.getCause() != EntityDamageEvent.DamageCause.SUICIDE
                && config.getBoolean("settings.players.invulnerability.none-players.enabled", true)) {
            event.setCancelled(true);
            return;
        }

        if (event.getFinalDamage() <= 0) {
            return;
        }
        Optional<GameInstance> victimMatch = game.instanceOf(victim.getUniqueId());
        if (victimMatch.isPresent() && !victimMatch.get().begun()) {
            handlePreStartDamage(event, victim, victimMatch.get());
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity)
                || !(byEntity.getDamager() instanceof Player attacker)) {
            return;
        }
        if (victimMatch.isEmpty() || !victimMatch.get().begun() || !sameMatch(victimMatch.get(), attacker)
                || !playerStates.role(attacker).isParticipant()
                || !playerStates.role(victim).isParticipant()) {
            return;
        }
        if (blockFriendlyFire(event, attacker, victim)) {
            return;
        }
        stats.getOrCreate(victimMatch.get().matchId(), attacker.getUniqueId()).damage += event.getFinalDamage();
    }

    /** Credits a speedrunner who finishes a hunter out of lives. */
    private void creditHunterFinalKill(long matchId, Player victim) {
        Player hunterKiller = victim.getKiller();
        if (hunterKiller != null
                && playerStates.role(hunterKiller.getUniqueId()) == Role.SPEEDRUNNER) {
            stats.getOrCreate(matchId, hunterKiller.getUniqueId()).finalKills++;
        }
    }

    /**
     * Lobby void rescue flag, now owned by the lobby config. Defaults to
     * enabled when the store is unavailable, matching the old default.
     */
    private boolean voidRescueEnabled() {
        LobbyConfig lobby = plugin.lobbyConfig();
        return lobby == null || lobby.isVoidRescue();
    }

    /** Rescues void falls in the lobby world. Returns true when the event was handled. */
    private boolean rescueLobbyVoid(EntityDamageEvent event, Player victim) {
        // Void rescue in the lobby world: falling off the platform returns
        // the player to their lobby instead of killing them. Never applies
        // in the game world.
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID
                || !voidRescueEnabled()
                || !worldEngine.rescuesVoidIn(victim.getWorld())) {
            return false;
        }
        OptionalInt memberLobby = lobbies.lobbyOf(victim.getUniqueId())
                .map(lobby -> OptionalInt.of(lobby.id())).orElseGet(OptionalInt::empty);
        Optional<Location> rescue = worldEngine.lobbyRescueLocation(memberLobby);
        if (rescue.isPresent()) {
            event.setCancelled(true);
            victim.teleport(rescue.get());
        } else {
            warnVoidRescueUnset(victim.getName());
        }
        return true;
    }

    /** Handles damage during the pre-start window, including the starting hit. */
    private void handlePreStartDamage(EntityDamageEvent event, Player victim, GameInstance match) {
        // A speedrunner hitting a hunter starts the game. The starting
        // hit is felt but never wounds: it begins the match and deals
        // no damage.
        boolean startsGame = event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player attacker
                && sameMatch(match, attacker)
                && playerStates.role(attacker) == Role.SPEEDRUNNER
                && playerStates.role(victim) == Role.HUNTER;
        // Hunter hits on speedrunners never land before the game
        // begins, while speedrunner hits on hunters still start it.
        if (event instanceof EntityDamageByEntityEvent byEntity
                && hunterHitsRunner(match, byEntity.getDamager(), victim)) {
            event.setCancelled(true);
            return;
        }
        absorbPreStartHit(event, victim);
        if (startsGame) {
            game.beginGame(match);
        }
    }

    /** Heals survivable pre-start hits and cancels lethal ones. */
    private void absorbPreStartHit(EntityDamageEvent event, Player victim) {
        // During the pre-start window, all participants are protected from
        // damage (including fall damage from wacky world-engine spawns).
        // Survivable hits still land so knockback registers, then heal
        // back one tick later; lethal hits cancel to prevent pre-start
        // deaths.
        if (!playerStates.role(victim).isParticipant()) {
            return;
        }
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

    /** Cancels same-team hits unless friendly fire is enabled. Returns true when cancelled. */
    private boolean blockFriendlyFire(EntityDamageEvent event, Player attacker, Player victim) {
        // Friendly fire: participants cannot damage their own team unless
        // enabled for their role. This only applies once the game has begun,
        // so it is never active during the pre-start window.
        if (playerStates.role(attacker) != playerStates.role(victim)) {
            return false;
        }
        boolean friendlyFire = playerStates.role(attacker) == Role.HUNTER
                ? config.getBoolean("settings.players.friendly-fire.hunter", false)
                : config.getBoolean("settings.players.friendly-fire.speedrunner", false);
        if (!friendlyFire) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    @EventHandler public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player killer)
                || !playerStates.role(killer).isParticipant()) {
            return;
        }
        Optional<GameInstance> match = game.instanceOf(killer.getUniqueId());
        if (match.isEmpty() || !match.get().begun()) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            stats.recordMobKill(match.get().matchId(), killer.getUniqueId());
            // Mob kills only matter for the kill-mob win conditions.
            Role killerRole = playerStates.role(killer);
            Integer lobby = match.map(GameInstance::originLobbyId).orElse(null);
            if (killerRole == Role.SPEEDRUNNER
                    && winConditionEngine.mobMatches(lobby, event.getEntity().getType(),
                            Role.SPEEDRUNNER)) {
                game.finishLater(match.get(), Role.SPEEDRUNNER);
            } else if (killerRole == Role.HUNTER
                    && winConditionEngine.mobMatches(lobby, event.getEntity().getType(),
                            Role.HUNTER)) {
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
        stats.recordPlayerKill(matchId, killer.getUniqueId(),
                playerStates.role(killer.getUniqueId()),
                playerStates.role(event.getEntity().getUniqueId()));
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
        if (match.isEmpty() || !match.get().begun() || !playerStates.role(player).isParticipant()) {
            return;
        }
        Role role = playerStates.role(player);
        // This deprecated event fires before the stack lands in the
        // inventory, so check the picked stack itself for an instant win
        // and keep the inventory scan as a fallback for stacks already
        // held or picked up by other means.
        Integer lobby = match.map(GameInstance::originLobbyId).orElse(null);
        if (winConditionEngine.materialWins(lobby, event.getItem().getItemStack().getType(), role)
                || winConditionEngine.hasItem(lobby, player, role)) {
            game.finishLater(match.get(), role);
        }
    }

    @EventHandler public void onAdvancement(org.bukkit.event.player.PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isEmpty() || !match.get().begun() || !playerStates.role(player).isParticipant()) {
            return;
        }
        // Recipe book unlocks are advancement events too, but they are not
        // real advancements: neither triggers nor the advancement win
        // condition should react to them.
        if (event.getAdvancement().getKey().getKey().startsWith("recipes/")) {
            return;
        }
        long matchId = match.get().matchId();
        stats.recordAdvancement(matchId, player.getUniqueId());
        game.stateCommands().runEventModifiers("ON_EVERY_ADVANCEMENT", player, matchId);
        Integer lobby = match.map(GameInstance::originLobbyId).orElse(null);
        if (playerStates.role(player) == Role.SPEEDRUNNER
                && winConditionEngine.hasReachAdvancement(lobby, player, Role.SPEEDRUNNER)) {
            game.finishLater(match.get(), Role.SPEEDRUNNER);
        } else if (playerStates.role(player) == Role.HUNTER
                && winConditionEngine.hasReachAdvancement(lobby, player, Role.HUNTER)) {
            game.finishLater(match.get(), Role.HUNTER);
        }
    }

    /** True when the player actively participates in the given match. */
    private boolean sameMatch(GameInstance instance, Player player) {
        return sameMatch(instance, player.getUniqueId());
    }

    /**
     * True when a hunter of the given match hits a speedrunner: direct
     * hits and projectile shooters both count.
     */
    private boolean hunterHitsRunner(GameInstance instance, Entity damager, Player victim) {
        Player attacker = resolveAttacker(damager);
        return attacker != null && blockPreStartHit(sameMatch(instance, attacker),
                playerStates.role(attacker), playerStates.role(victim));
    }

    /** Attacking player behind a damager: direct hits and shooters. */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    /**
     * True when a pre-start hit never lands: a hunter of the same match
     * hitting a speedrunner. Pure for tests.
     */
    public static boolean blockPreStartHit(boolean sameMatch, Role attacker, Role victim) {
        return sameMatch && attacker == Role.HUNTER && victim == Role.SPEEDRUNNER;
    }

    /** True when the player id actively participates in the given match. */
    private boolean sameMatch(GameInstance instance, UUID playerId) {
        return game.instanceOf(playerId).map(match -> match.matchId() == instance.matchId()).orElse(false);
    }

    /** Warns about a missing rescue destination, at most once every 30 seconds. */
    private void warnVoidRescueUnset(String playerName) {
        long now = System.currentTimeMillis();
        if (now - lastVoidRescueWarning < 30_000L) {
            return;
        }
        lastVoidRescueWarning = now;
        plugin.logger().warning(playerName + " fell into the void in the lobby world, but no lobby location "
                + "is set to rescue them to. Set one with /manhunt worldengine lobbyconfig setlobbytp 0.");
    }
}
