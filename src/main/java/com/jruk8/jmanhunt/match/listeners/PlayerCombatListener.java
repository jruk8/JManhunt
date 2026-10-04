package com.jruk8.jmanhunt.match.listeners;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.PlayerSettings;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpawnCampService;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import com.jruk8.jmanhunt.stats.Stats;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.WorldEngineService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.match.WinConditionEngine;

/** Deaths, damage, kills, and item/advancement win triggers. */
public final class PlayerCombatListener implements Listener {
    /** Mocking friendly fire lines the broadcast picks between. */
    static final int FRIENDLY_FIRE_LINES = 3;

    /** Role, visibility, player settings, and death text. */
    public record CombatReads(PlayerStateStore states, FakeSpectatorService fakes,
            PlayerSettings players, GameMessages gameTexts) {
    }

    /** Match, stats, win checks, and disconnect tracking. */
    public record CombatMatch(GameManager game, StatsManager stats,
            WinConditionEngine winConditionEngine, SpeedrunnerDisconnectTracker disconnects,
            Map<UUID, BukkitTask> disconnectTasks) {
    }

    /** Compass, lobbies, engine, and respawn services. */
    public record CombatWorld(CompassManager compass, LobbyService lobbies,
            WorldEngineService worldEngine, PlayerRespawnListener respawn) {
    }

    /** Spawn camp, role teams, logger, and lobby store. */
    public record CombatEdge(SpawnCampService spawnCamp, RoleTeamService roleTeams,
            JManhuntLogger log, LobbyConfig lobbyConfig) {
    }

    private final CombatReads reads;
    private final CombatMatch match;
    private final CombatWorld world;
    private final CombatEdge edge;
    private final TaskScheduler tasks;
    private long lastVoidRescueWarning;

    public PlayerCombatListener(CombatReads reads, CombatMatch match, CombatWorld world,
            CombatEdge edge, TaskScheduler tasks) {
        this.reads = reads;
        this.match = match;
        this.world = world;
        this.edge = edge;
        this.tasks = tasks;
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        reads.states().recordLastSeen(player, player.getLocation());

        Optional<GameInstance> match = this.match.game().instanceOf(player.getUniqueId());
        if (match.isEmpty() || !match.get().begun()) {
            return;
        }
        // Begun matches speak through plugin lines only: vanilla death
        // messages would double every announcement.
        event.setDeathMessage(null);
        GameInstance instance = match.get();
        Role role = reads.states().role(player);
        if (role.isParticipant()) {
            this.match.stats().recordDeath(player.getUniqueId());
            Stats deathSlice = this.match.stats().getOrCreate(instance.matchId(), player.getUniqueId());
            deathSlice.deaths++;
            if (deathSlice.role == Role.NONE) {
                deathSlice.role = role;
            }
        }
        // Spawncamp punishment kills re-enter here synchronously: their state
        // changes run intact, but the punishment broadcast already said it.
        boolean quiet = edge.spawnCamp().isQuietPunishment(player.getUniqueId());
        if (role == Role.SPEEDRUNNER) {
            handleSpeedrunnerDeath(player, instance, quiet);
        } else if (role == Role.HUNTER) {
            handleHunterDeath(player, instance, quiet);
        }
        if (role.isParticipant()) {
            fireDeathTrigger(player, instance);
        }
        world.compass().clearLocksOnTargetDeath(player.getUniqueId());
        if (!quiet) {
            broadcastFriendlyFireKill(instance, player);
        }
        // Next tick: state is final, and compass items are safe to touch
        // outside the death event. Unlocked picks re-resolve at once.
        tasks.run(() -> world.compass().refreshInstance(instance));
    }

    /** Fires ON_DEATH for the dead player: dead name plus killer name or "null". */
    private void fireDeathTrigger(Player player, GameInstance instance) {
        Player killer = player.getKiller();
        String killerName = killer == null ? "null" : killer.getName();
        this.match.game().stateCommands().runEventModifiers("ON_DEATH", player,
                instance.matchId(), List.of(player.getName(), killerName));
    }

    /** Mocking lobby broadcast for same-team kills, when enabled. */
    private void broadcastFriendlyFireKill(GameInstance instance, Player victim) {
        if (!(victim.getKiller() instanceof Player killer)) {
            return;
        }
        if (!isFriendlyFireKill(reads.states().role(killer), reads.states().role(victim),
                killer.getUniqueId().equals(victim.getUniqueId()))) {
            return;
        }
        if (!reads.players().getFriendlyFire().isBroadcastKills()) {
            return;
        }
        int roll = ThreadLocalRandom.current().nextInt(FRIENDLY_FIRE_LINES);
        this.match.game().messaging().sendToInstance(instance,
                friendlyFireTemplate(reads.gameTexts(), roll),
                Map.of("dead", victim.getName(), "killer", killer.getName()));
    }

    /** True for a kill of a teammate: same participant role, no suicides. Pure for tests. */
    static boolean isFriendlyFireKill(Role killerRole, Role victimRole, boolean selfKill) {
        return !selfKill && killerRole.isParticipant() && killerRole == victimRole;
    }

    /** Friendly fire line key for a roll in [0, 3). Pure for tests. */
    static String friendlyFireTemplate(GameMessages texts, int roll) {
        return switch (Math.floorMod(roll, FRIENDLY_FIRE_LINES)) {
            case 0 -> texts.getFriendlyFire1();
            case 1 -> texts.getFriendlyFire2();
            default -> texts.getFriendlyFire3();
        };
    }

    private void handleSpeedrunnerDeath(Player player, GameInstance instance, boolean quiet) {
        PlayerConnectionListener.cancelDisconnectTask(this.match.disconnectTasks(), player.getUniqueId());
        this.match.disconnects().clear(player.getUniqueId());
        reads.states().setSpeedrunnerAlive(player.getUniqueId(), false);
        long matchId = instance.matchId();
        int lives = reads.states().getLives(player.getUniqueId());
        var speedrunnerRespawn = reads.players().getRespawn().getSpeedrunner();
        int delaySeconds = PlayerRespawnListener.effectiveRespawnDelay(
                speedrunnerRespawn.isEnabled(), speedrunnerRespawn.getDelaySeconds());
        if (lives != -1) {
            reads.states().decrementLives(player.getUniqueId());
            if (reads.states().getLives(player.getUniqueId()) <= 0) {
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
            this.match.game().messaging().sendToInstance(instance, reads.gameTexts().getHunterOutOfLives(), Map.of());
        }
        instance.recordDeath(player.getUniqueId(), player.getName(), Role.HUNTER);
        reads.states().setRole(player.getUniqueId(), Role.SPECTATOR);
        edge.roleTeams().sync(player);
        instance.deactivate(player.getUniqueId());
        world.compass().clearHotspotHistory(player.getUniqueId());
        world.compass().reconcileTeammateModes(instance);
        this.match.game().flagStore().removePlayer(matchId, player.getName());
        tasks.run(() -> {
            reads.fakes().enable(player);
            world.compass().removeCompasses(player);
        });
        checkHuntersRemaining(instance);
        this.match.game().messaging().playInstanceSound(instance, "game.hunter-death");
    }

    /** Eliminates a speedrunner out of lives and finishes when none remain. */
    private void eliminateSpeedrunner(Player player, GameInstance instance, boolean quiet, long matchId) {
        // Out of lives: eliminate permanently. Only an opposite-role
        // killer earns the final kill: same-role finishes never count.
        instance.recordDeath(player.getUniqueId(), player.getName(), Role.SPEEDRUNNER);
        reads.states().setRole(player.getUniqueId(), Role.SPECTATOR);
        edge.roleTeams().sync(player);
        instance.deactivate(player.getUniqueId());
        world.compass().clearHotspotHistory(player.getUniqueId());
        world.compass().reconcileTeammateModes(instance);
        this.match.game().flagStore().removePlayer(matchId, player.getName());
        Player finalKiller = player.getKiller();
        if (finalKiller != null && reads.states().role(finalKiller.getUniqueId()) == Role.HUNTER) {
            Stats finalSlice = this.match.stats().getOrCreate(matchId, finalKiller.getUniqueId());
            finalSlice.finalKills++;
            if (finalSlice.role == Role.NONE) {
                finalSlice.role = Role.HUNTER;
            }
        }
        tasks.run(() -> {
            reads.fakes().enable(player);
            world.compass().removeCompasses(player);
        });
        if (!quiet) {
            this.match.game().messaging().sendToInstance(instance,
                    reads.gameTexts().getSpeedrunnerOutOfLives(), Map.of());
        }
        // When nobody remains the win line follows, so no last-died
        // line is sent: the win is the announcement.
        int playerCount = this.match.game().activeRunnerCount(instance);
        if (playerCount > 0) {
            if (!quiet) {
                this.match.game().messaging().sendToInstance(instance, reads.gameTexts().getSpeedrunnerDeath(),
                        Map.of("value", Integer.toString(playerCount)));
            }
        } else {
            this.match.game().finishLater(instance, Role.HUNTER, "All speedrunners eliminated");
        }
        this.match.game().messaging().playInstanceSound(instance, "game.speedrunner-death");
    }

    /** Announces a survived speedrunner death and schedules the world.respawn(). */
    private void surviveRunnerDeath(Player player, GameInstance instance, boolean quiet,
            long matchId, int delaySeconds) {
        if (!quiet) {
            // The dying runner is already flagged not-alive but will respawn, so count them.
            int remaining = this.match.game().activeRunnerCount(instance) + 1;
            this.match.game().messaging().sendToInstance(instance, reads.gameTexts().getSpeedrunnerDeath(),
                    Map.of("value", Integer.toString(remaining)));
        }
        this.match.game().messaging().playInstanceSound(instance, "game.speedrunner-death");
        world.respawn().scheduleRespawn(player, instance, quiet, delaySeconds,
                reads.gameTexts().getSpeedrunnerRespawnScheduled(), matchId);
    }

    /** Handles a speedrunner death with unlimited lives. */
    private void handleUnlimitedRunnerDeath(Player player, GameInstance instance, boolean quiet,
            long matchId, int delaySeconds) {
        // Unlimited lives (-1): never eliminated permanently by lives. The
        // unlimited line fires once per side per match; quiet deaths neither
        // send nor consume it.
        if (!quiet && !instance.runnerUnlimitedAnnounced()) {
            instance.setRunnerUnlimitedAnnounced(true);
            this.match.game().messaging().sendToInstance(instance,
                    reads.gameTexts().getSpeedrunnersUnlimitedLives(), Map.of());
        }
        surviveRunnerDeath(player, instance, quiet, matchId, delaySeconds);
    }

    private void handleHunterDeath(Player player, GameInstance instance, boolean quiet) {
        PlayerConnectionListener.cancelDisconnectTask(this.match.disconnectTasks(), player.getUniqueId());
        this.match.disconnects().clear(player.getUniqueId());
        long matchId = instance.matchId();
        int lives = reads.states().getLives(player.getUniqueId());
        var hunterRespawn = reads.players().getRespawn().getHunter();
        int delaySeconds = PlayerRespawnListener.effectiveRespawnDelay(
                hunterRespawn.isEnabled(), hunterRespawn.getDelaySeconds());
        if (lives != -1) {
            reads.states().decrementLives(player.getUniqueId());
            if (reads.states().getLives(player.getUniqueId()) <= 0) {
                eliminateHunterOutOfLives(player, instance, quiet, matchId);
                return;
            }
        }
        if (!quiet) {
            this.match.game().messaging().sendToInstance(instance, reads.gameTexts().getHunterDeath(), Map.of());
        }
        this.match.game().messaging().playInstanceSound(instance, "game.hunter-death");
        if (lives == -1 && !quiet && !instance.hunterUnlimitedAnnounced()) {
            instance.setHunterUnlimitedAnnounced(true);
            this.match.game().messaging().sendToInstance(instance,
                    reads.gameTexts().getHuntersUnlimitedLives(), Map.of());
        }
        // Undelayed hunters respawn through vanilla mechanics; only a
        // positive delay routes them through the spectator revive.
        if (delaySeconds > 0) {
            world.respawn().scheduleRespawn(player, instance, quiet, delaySeconds,
                    reads.gameTexts().getHunterRespawnScheduled(), matchId);
        }
    }

    private void checkHuntersRemaining(GameInstance instance) {
        // No last-removed line: the win that follows is the announcement.
        if (this.match.game().activeHunterCount(instance) == 0) {
            this.match.game().finishLater(instance, Role.SPEEDRUNNER, "All hunters eliminated");
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
        if (!reads.states().role(victim).isParticipant()
                && event.getCause() != EntityDamageEvent.DamageCause.SUICIDE
                && reads.players().getInvulnerability().getNonePlayers().isEnabled()) {
            event.setCancelled(true);
            return;
        }

        if (event.getFinalDamage() <= 0) {
            return;
        }
        Optional<GameInstance> victimMatch = this.match.game().instanceOf(victim.getUniqueId());
        if (victimMatch.isPresent() && !victimMatch.get().begun()) {
            handlePreStartDamage(event, victim, victimMatch.get());
            return;
        }
        handleBegunMatchDamage(event, victim, victimMatch);
    }

    /**
     * Begun-match damage: friendly-fire cancels first, then the
     * damage-taken event fires for any dealer kind, then same-match
     * player hits accrue damage stats.
     */
    private void handleBegunMatchDamage(EntityDamageEvent event, Player victim,
            Optional<GameInstance> victimMatch) {
        Player attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player damager) {
            attacker = damager;
        }
        boolean playerInvolved = attacker != null && victimMatch.isPresent()
                && victimMatch.get().begun() && sameMatch(victimMatch.get(), attacker)
                && reads.states().role(attacker).isParticipant()
                && reads.states().role(victim).isParticipant();
        if (playerInvolved && blockFriendlyFire(event, attacker, victim)) {
            return;
        }
        if (victimMatch.isPresent() && victimMatch.get().begun()
                && reads.states().role(victim).isParticipant()) {
            fireDamageTaken(event, victim, victimMatch.get(), attacker);
        }
        if (!playerInvolved) {
            return;
        }
        Role attackerRole = reads.states().role(attacker);
        Stats damageSlice = this.match.stats().getOrCreate(victimMatch.get().matchId(), attacker.getUniqueId());
        damageSlice.damage += event.getFinalDamage();
        if (damageSlice.role == Role.NONE) {
            damageSlice.role = attackerRole;
        }
    }

    /**
     * Fires ON_DAMAGE_TAKEN on the victim: their name, the final
     * damage in half hearts, and the dealer name, or "null" for
     * mob and environment damage.
     */
    private void fireDamageTaken(EntityDamageEvent event, Player victim, GameInstance match,
            Player attacker) {
        this.match.game().stateCommands().runEventModifiers("ON_DAMAGE_TAKEN", victim, match.matchId(),
                List.of(victim.getName(), String.valueOf(event.getFinalDamage()),
                        attacker == null ? "null" : attacker.getName()));
    }

    /** Credits a speedrunner who finishes a hunter out of lives. */
    private void creditHunterFinalKill(long matchId, Player victim) {
        Player hunterKiller = victim.getKiller();
        if (hunterKiller != null
                && reads.states().role(hunterKiller.getUniqueId()) == Role.SPEEDRUNNER) {
            Stats finalSlice = this.match.stats().getOrCreate(matchId, hunterKiller.getUniqueId());
            finalSlice.finalKills++;
            if (finalSlice.role == Role.NONE) {
                finalSlice.role = Role.SPEEDRUNNER;
            }
        }
    }

    /**
     * Lobby void rescue flag, now owned by the lobby config. Defaults to
     * enabled when the store is unavailable, matching the old default.
     */
    private boolean voidRescueEnabled() {
        LobbyConfig lobby = edge.lobbyConfig();
        return lobby == null || lobby.isVoidRescue();
    }

    /** Rescues void falls in the lobby world. Returns true when the event was handled. */
    private boolean rescueLobbyVoid(EntityDamageEvent event, Player victim) {
        // Void rescue in the lobby world: falling off the platform returns
        // the player to their lobby instead of killing them. Never applies
        // in the game world.
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID
                || !voidRescueEnabled()
                || !world.worldEngine().rescuesVoidIn(victim.getWorld())) {
            return false;
        }
        OptionalInt memberLobby = world.lobbies().lobbyOf(victim.getUniqueId())
                .map(lobby -> OptionalInt.of(lobby.id())).orElseGet(OptionalInt::empty);
        Optional<Location> rescue = world.worldEngine().lobbyRescueLocation(memberLobby);
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
                && reads.states().role(attacker) == Role.SPEEDRUNNER
                && reads.states().role(victim) == Role.HUNTER;
        // Hunter hits on speedrunners never land before the game
        // begins, while speedrunner hits on hunters still start it.
        if (event instanceof EntityDamageByEntityEvent byEntity
                && hunterHitsRunner(match, byEntity.getDamager(), victim)) {
            event.setCancelled(true);
            return;
        }
        absorbPreStartHit(event, victim);
        if (startsGame) {
            this.match.game().beginGame(match);
        }
    }

    /** Heals survivable pre-start hits and cancels lethal ones. */
    private void absorbPreStartHit(EntityDamageEvent event, Player victim) {
        // During the pre-start window, all participants are protected from
        // damage (including fall damage from wacky world-engine spawns).
        // Survivable hits still land so knockback registers, then heal
        // back one tick later; lethal hits cancel to prevent pre-start
        // deaths.
        if (!reads.states().role(victim).isParticipant()) {
            return;
        }
        double finalDamage = event.getFinalDamage();
        if (finalDamage < victim.getHealth()) {
            tasks.runLater(() -> {
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
        if (reads.states().role(attacker) != reads.states().role(victim)) {
            return false;
        }
        var fire = reads.players().getFriendlyFire();
        boolean friendlyFire = reads.states().role(attacker) == Role.HUNTER
                ? fire.isHunter()
                : fire.isSpeedrunner();
        if (!friendlyFire) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    @EventHandler public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player killer)
                || !reads.states().role(killer).isParticipant()) {
            return;
        }
        Optional<GameInstance> match = this.match.game().instanceOf(killer.getUniqueId());
        if (match.isEmpty() || !match.get().begun()) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            handleMobKill(match.get(), killer, event.getEntity());
            return;
        }
        boolean victimIsPlayer = event.getEntity() instanceof Player;
        if (!victimIsPlayer || !reads.states().role(event.getEntity().getUniqueId()).isParticipant()) {
            return;
        }
        if (!sameMatch(match.get(), event.getEntity().getUniqueId())) {
            return;
        }
        long matchId = match.get().matchId();
        this.match.stats().recordPlayerKill(matchId, killer.getUniqueId(),
                reads.states().role(killer.getUniqueId()),
                reads.states().role(event.getEntity().getUniqueId()));
        edge.spawnCamp().handleKill(matchId, killer, (Player) event.getEntity(),
                reads.states().role(killer.getUniqueId()));
        if (victimIsPlayer) {
            List<String> eventArgs = List.of(killer.getName(), event.getEntity().getName());
            this.match.game().stateCommands().runEventModifiers("ON_PLAYER_KILLS", killer, matchId, eventArgs);
            Role killerRole = reads.states().role(killer.getUniqueId());
            if (killerRole == Role.HUNTER) {
                this.match.game().stateCommands().runEventModifiers("ON_HUNTER_KILLS", killer, matchId, eventArgs);
            } else if (killerRole == Role.SPEEDRUNNER) {
                this.match.game().stateCommands().runEventModifiers("ON_SPEEDRUNNER_KILLS", killer, matchId,
                        eventArgs);
            }
        }
    }

    private void handleMobKill(GameInstance match, Player killer, Entity victim) {
        Role killerRole = reads.states().role(killer);
        this.match.stats().recordMobKill(match.matchId(), killer.getUniqueId(), killerRole);
        this.match.game().stateCommands().runEventModifiers("ON_MOB_KILLED", killer, match.matchId(),
                List.of(victim.getType().name()));
        // Mob kills only matter for the kill-mob win conditions.
        Integer lobby = match.originLobbyId();
        if (killerRole == Role.SPEEDRUNNER
                && this.match.winConditionEngine().mobMatches(lobby, victim.getType(), Role.SPEEDRUNNER)) {
            this.match.game().finishLater(match, Role.SPEEDRUNNER,
                    "Slew " + WinConditionEngine.prettyKey(victim.getType().getKey().toString()));
        } else if (killerRole == Role.HUNTER
                && this.match.winConditionEngine().mobMatches(lobby, victim.getType(), Role.HUNTER)) {
            this.match.game().finishLater(match, Role.HUNTER,
                    "Slew " + WinConditionEngine.prettyKey(victim.getType().getKey().toString()));
        }
    }

    @EventHandler public void onPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        Optional<GameInstance> match = this.match.game().instanceOf(player.getUniqueId());
        if (match.isEmpty() || !match.get().begun() || !reads.states().role(player).isParticipant()) {
            return;
        }
        Role role = reads.states().role(player);
        // This deprecated event fires before the stack lands in the
        // inventory, so check the picked stack itself for an instant win
        // and keep the inventory scan as a fallback for stacks already
        // held or picked up by other means.
        Integer lobby = match.map(GameInstance::originLobbyId).orElse(null);
        if (this.match.winConditionEngine().materialWins(lobby, event.getItem().getItemStack().getType(), role)
                || this.match.winConditionEngine().hasItem(lobby, player, role)) {
            this.match.game().finishLater(match.get(), role,
                    "Acquired " + WinConditionEngine.prettyKey(this.match.winConditionEngine().item(lobby, role)));
        }
    }

    @EventHandler public void onAdvancement(org.bukkit.event.player.PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Optional<GameInstance> match = this.match.game().instanceOf(player.getUniqueId());
        if (match.isEmpty() || !match.get().begun() || !reads.states().role(player).isParticipant()) {
            return;
        }
        // Recipe book unlocks are advancement events too, but they are not
        // real advancements: neither triggers nor the advancement win
        // condition should react to them.
        if (event.getAdvancement().getKey().getKey().startsWith("recipes/")) {
            return;
        }
        long matchId = match.get().matchId();
        this.match.stats().recordAdvancement(matchId, player.getUniqueId(), reads.states().role(player));
        this.match.game().stateCommands().runEventModifiers("ON_EVERY_ADVANCEMENT", player, matchId,
                List.of(event.getAdvancement().getKey().toString()));
        Integer lobby = match.map(GameInstance::originLobbyId).orElse(null);
        if (reads.states().role(player) == Role.SPEEDRUNNER
                && this.match.winConditionEngine().hasReachAdvancement(lobby, player, Role.SPEEDRUNNER)) {
            this.match.game().finishLater(match.get(), Role.SPEEDRUNNER, "Reached "
                    + WinConditionEngine.prettyKey(this.match.winConditionEngine()
                            .advancement(lobby, Role.SPEEDRUNNER)));
        } else if (reads.states().role(player) == Role.HUNTER
                && this.match.winConditionEngine().hasReachAdvancement(lobby, player, Role.HUNTER)) {
            this.match.game().finishLater(match.get(), Role.HUNTER, "Reached "
                    + WinConditionEngine.prettyKey(this.match.winConditionEngine().advancement(lobby, Role.HUNTER)));
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
                reads.states().role(attacker), reads.states().role(victim));
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
        return this.match.game().instanceOf(playerId).map(match -> match.matchId() == instance.matchId()).orElse(false);
    }

    /** Warns about a missing rescue destination, at most once every 30 seconds. */
    private void warnVoidRescueUnset(String playerName) {
        long now = System.currentTimeMillis();
        if (now - lastVoidRescueWarning < 30_000L) {
            return;
        }
        lastVoidRescueWarning = now;
        edge.log().warning(playerName + " fell into the void in the lobby world, but no lobby location "
                + "is set to rescue them to. Set one with /manhunt worldengine lobbyconfig setlobbytp 0.");
    }
}
