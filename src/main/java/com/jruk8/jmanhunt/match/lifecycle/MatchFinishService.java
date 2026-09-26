package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.api.events.JMatchCancelEvent;
import com.jruk8.jmanhunt.api.events.JMatchEndEvent;
import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.match.LeaveDestination;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.border.BorderMode;
import com.jruk8.jmanhunt.world.cell.CellBounds;
import com.jruk8.jmanhunt.world.teleport.MatchTeleportService;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.world.WorldEngineService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.match.autostart.AutostartService;
import com.jruk8.jmanhunt.match.prestart.PrestartService;

/**
 * Ends matches and shrinks them: finishes, cancels, teardown, mid-match
 * leaves, and the concurrent-match border guard. GameManager keeps thin
 * delegates.
 */
public final class MatchFinishService {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final PlayerStateStore playerStates;
    private final CompassManager compass;
    private final StatsManager stats;
    private final GameStateCommandManager stateCommands;
    private final ConfigService configService;
    private final WorldEngineService worldEngine;
    private final MatchStore store;
    private final MatchMessaging messaging;
    private final TimeLimitService timeLimits;
    private final PrestartService prestart;
    private final AutostartService autostart;
    private final FlagStore flagStore;
    private final List<Consumer<GameInstance>> gameEndListeners = new ArrayList<>();

    public MatchFinishService(JManhuntPlugin plugin, MessageService messages, PlayerStateStore playerStates,
            CompassManager compass, StatsManager stats, GameStateCommandManager stateCommands,
            ConfigService configService, WorldEngineService worldEngine, MatchStore store,
            MatchMessaging messaging, TimeLimitService timeLimits, PrestartService prestart,
            AutostartService autostart, FlagStore flagStore) {
        this.plugin = plugin;
        this.messages = messages;
        this.playerStates = playerStates;
        this.compass = compass;
        this.stats = stats;
        this.stateCommands = stateCommands;
        this.configService = configService;
        this.worldEngine = worldEngine;
        this.store = store;
        this.messaging = messaging;
        this.timeLimits = timeLimits;
        this.prestart = prestart;
        this.autostart = autostart;
        this.flagStore = flagStore;
        // Pseudo-border guard for concurrent matches; idle unless at least two
        // matches run with the engine border on.
        Bukkit.getScheduler().runTaskTimer(plugin, this::enforcePseudoBorders, 20L, 20L);
    }

    public void addGameEndListener(Consumer<GameInstance> listener) {
        gameEndListeners.add(listener);
    }

    /**
     * Ends a begun match whose hunter or speedrunner bucket hit zero through
     * a role change. Deaths end matches on their own paths; this covers
     * setplayer, joins, leaves, and removals.
     */
    public void finishIfBucketEmpty(GameInstance instance) {
        if (!instance.begun() || instance.ending()) {
            return;
        }
        bucketWinner(store.activeHunterCount(instance), store.activeRunnerCount(instance))
                .ifPresent(winner -> finishLater(instance, winner));
    }

    /**
     * Cancels an unbegun match that lost a whole side through a leave.
     * Pre-start matches need at least one hunter and one speedrunner to
     * progress; the autostart minimums do not apply here.
     */
    public void cancelIfPreStartUnviable(GameInstance instance) {
        if (instance.begun() || instance.ending()) {
            return;
        }
        if (!canProgress(store.activeHunterCount(instance), store.activeRunnerCount(instance))) {
            cancel(instance);
        }
    }

    /** True when a pre-start match can still progress: both sides fielded. Pure for tests. */
    public static boolean canProgress(int hunterCount, int runnerCount) {
        return hunterCount > 0 && runnerCount > 0;
    }

    /** Winner when a bucket is empty; empty when both sides stand. Pure for tests. */
    public static Optional<Role> bucketWinner(int hunterCount, int runnerCount) {
        if (hunterCount == 0 && runnerCount == 0) {
            return Optional.empty();
        }
        if (hunterCount == 0) {
            return Optional.of(Role.SPEEDRUNNER);
        }
        if (runnerCount == 0) {
            return Optional.of(Role.HUNTER);
        }
        return Optional.empty();
    }

    /** Configured leave destination, SPECTATOR by default. */
    public LeaveDestination leaveDestination(Integer lobby) {
        return LeaveDestination.parse(plugin.overrides()
                .getString(lobby, "settings.match.game-leave.destination", "SPECTATOR"));
    }

    /**
     * Removes players from a match: deactivates them, clears their match
     * state, and moves them to the configured destination. Begun-match
     * participants drop their gear when {@code dropGear} is true (voluntary
     * leave) or are wiped match-end style when false (auto-leave); pre-start
     * leavers keep everything. Hunter and speedrunner departures are
     * announced to the lobby with the remaining role count. Returns the
     * number removed. A last leaver ends the match for the other side.
     */
    public int leaveMatch(GameInstance instance, List<Player> leavers, boolean dropGear) {
        if (!instance.active()) {
            return 0;
        }
        LeaveDestination destination = leaveDestination(instance.originLobbyId());
        int removed = 0;
        List<Role> leftRoles = new ArrayList<>();
        List<String> leftNames = new ArrayList<>();
        for (Player player : leavers) {
            Role before = leavePlayer(instance, player, dropGear, destination);
            if (before == null) {
                continue;
            }
            leftRoles.add(before);
            leftNames.add(player.getName());
            removed++;
        }
        announceLeaves(instance, leftRoles, leftNames);
        if (removed > 0) {
            finishIfBucketEmpty(instance);
            cancelIfPreStartUnviable(instance);
        }
        return removed;
    }

    /** Removes one player from the instance. Returns the prior role, or null when not active. */
    private Role leavePlayer(GameInstance instance, Player player, boolean dropGear,
            LeaveDestination destination) {
        UUID playerId = player.getUniqueId();
        if (!instance.isActive(playerId)) {
            return null;
        }
        Role before = playerStates.role(player);
        if (instance.begun() && before.isParticipant()) {
            if (dropGear) {
                dropAllGear(player);
                stateCommands.resetVitals(player);
            } else {
                stateCommands.resetPlayer(player);
            }
        }
        playerStates.setSpeedrunnerAlive(playerId, false);
        instance.deactivate(playerId);
        playerStates.clearMatchFor(List.of(playerId));
        flagStore.removePlayer(instance.matchId(), player.getName());
        compass.removeCompasses(player);
        applyLeaveDestination(instance, player, dropGear, destination);
        plugin.roleTeams().sync(player);
        messages.message(player, "game.leave-success", Map.of());
        return before;
    }

    /** Moves a leaver to the lobby or the spectator box. */
    private void applyLeaveDestination(GameInstance instance, Player player, boolean dropGear,
            LeaveDestination destination) {
        if (destination == LeaveDestination.LOBBY) {
            playerStates.setRole(player, Role.NONE);
            worldEngine.teleportToLobby(List.of(player), instance.originLobbyId());
            worldEngine.setSpawnToLobbyQuiet(List.of(player), instance.originLobbyId());
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        } else {
            playerStates.setRole(player, Role.SPECTATOR);
            player.setGameMode(GameMode.SPECTATOR);
            if (!dropGear && instance.cellIndex().isPresent()) {
                // Auto-leave pulled them out of bounds: put the watcher
                // back in the cell instead of stranding them outside it.
                worldEngine.teleportJoinersToCell(List.of(player), instance.cellIndex().getAsLong());
            }
        }
    }

    /** Announces hunter and speedrunner departures to the origin lobby. */
    private void announceLeaves(GameInstance instance, List<Role> leftRoles, List<String> leftNames) {
        for (int index = 0; index < leftRoles.size(); index++) {
            Role before = leftRoles.get(index);
            if (before == Role.HUNTER) {
                messaging.sendToLobby(instance.originLobbyId(), "game.hunter-left",
                        Map.of("player", leftNames.get(index),
                                "remaining", String.valueOf(store.activeHunterCount(instance))));
            } else if (before == Role.SPEEDRUNNER) {
                messaging.sendToLobby(instance.originLobbyId(), "game.speedrunner-left",
                        Map.of("player", leftNames.get(index),
                                "remaining", String.valueOf(store.activeRunnerCount(instance))));
            }
        }
    }

    /**
     * Removes a begun-match participant standing outside their cell or in
     * the lobby world, with a reason notice. The End is skipped like the
     * border enforcement, and while borders are enabled they confine
     * instead. Returns true when the player was removed.
     */
    public boolean autoLeaveIfOutside(Player player, Location at) {
        Optional<GameInstance> match = store.instanceOf(player.getUniqueId());
        if (match.isEmpty() || !match.get().begun() || match.get().ending()) {
            return false;
        }
        if (!playerStates.role(player).isParticipant()) {
            return false;
        }
        GameInstance instance = match.get();
        if (at.getWorld() != null && at.getWorld().getName().equals(worldEngine.lobbyWorldName())) {
            messages.message(player, "game.auto-left-lobby-world", Map.of());
            leaveMatch(instance, List.of(player), false);
            return true;
        }
        if (at.getWorld() == null) {
            return false;
        }
        World.Environment environment = at.getWorld().getEnvironment();
        if (environment != World.Environment.NORMAL && environment != World.Environment.NETHER) {
            return false;
        }
        WorldEngineConfig config = WorldEngineConfig.fromConfig(configService);
        if (!config.enabled() || instance.cellIndex().isEmpty()) {
            return false;
        }
        if (config.worldBorderEnabled()) {
            // Borders confine instead: the rubber-band brings them back.
            return false;
        }
        boolean nether = environment == World.Environment.NETHER;
        CellBounds bounds = CellBounds.forCell(instance.cellIndex().getAsLong(),
                config.cellSize(), config.startBorderDiameter(), !instance.begun());
        if (bounds.contains(at.getX(), at.getZ(), nether)) {
            return false;
        }
        messages.message(player, "game.auto-left-bounds", Map.of());
        leaveMatch(instance, List.of(player), false);
        return true;
    }

    /** Drops a player's full gear at their feet, death style. */
    private static void dropAllGear(Player player) {
        Location at = player.getLocation();
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            dropStack(world, at, item);
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            dropStack(world, at, item);
        }
        dropStack(world, at, player.getInventory().getItemInOffHand());
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
    }

    private static void dropStack(World world, Location at, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            world.dropItemNaturally(at, item);
        }
    }

    /** Ends the match when exactly one is live; a no-op otherwise. */
    public void finish(Role winner) {
        store.singleLiveInstance().ifPresent(instance -> finish(instance, winner));
    }

    /** Ends one match. */
    public void finish(GameInstance instance, Role winner) {
        finish(instance, winner, false);
    }

    /**
     * Ends one match. When {@code immediate} is true the configured
     * {@code match.end-delay} is skipped: statistics post instantly and the
     * final cleanup runs at once instead of after the delay. An immediate end
     * during an in-progress end delay finishes the match at once instead of
     * being blocked.
     */
    public void finish(GameInstance instance, Role winner, boolean immediate) {
        if (instance.ending()) {
            if (immediate) {
                showEndStatsOnce(instance);
                finishEndPhase(instance);
            }
            return;
        }
        instance.setEnding(true);
        gameEndListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(
                new JMatchEndEvent(instance.matchId(), GameManager.roleToPlayerRole(winner)));
        prestart.cancelWaitingTasks(instance);
        prestart.cancelHeadstarts(instance);
        timeLimits.cancelTimeLimit(instance);

        String title = winner == Role.HUNTER ? "game.hunters-title" : "game.speedrunners-title";
        messaging.sendToInstanceComponent(instance, messages.renderLiteral(getWinMessage(winner), Map.of()));
        for (Player player : store.onlineAssignedPlayers(instance)) {
            player.showTitle(Title.title(messages.component(title), Component.empty(),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        playerStates.resetOfflinePlayers(Bukkit.getOnlinePlayers(), instance.assignedPlayerIds());
        messaging.playInstanceSound(instance, winner == Role.HUNTER ? "game.fail-sound" : "game.win-sound");
        stats.completeMatch(instance.matchId(), winner);

        // Make all players invulnerable on game end if configured
        if (plugin.overrides().getBoolean(instance.originLobbyId(),
                "settings.players.invulnerability.on-game-end.enabled", true)) {
            store.onlineAssignedPlayers(instance).forEach(p -> p.setInvulnerable(true));
        }

        // cancel interval modifiers early so they don't fire during the end delay
        stateCommands.cancelIntervalModifiers(instance.matchId());
        // ran before the delay to ensure that any commands that depend on the match being completed can run immediately
        stateCommands.runConsoleCleanup(instance.matchId());
        stateCommands.runPlayerCleanup(instance.matchId(), store.onlineActivePlayers(instance));

        long delay = immediate
                ? 0L
                : Math.max(0L, Math.round(plugin.overrides()
                        .getDouble(instance.originLobbyId(), "match.end-delay", 10.0) * 20.0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> showEndStatsOnce(instance), delay / 2);
        Bukkit.getScheduler().runTaskLater(plugin, () -> finishEndPhase(instance), delay);
    }

    /** Sends end-of-match statistics, exactly once per match. */
    private void showEndStatsOnce(GameInstance instance) {
        if (instance.endStatsShown()) {
            return;
        }
        instance.setEndStatsShown(true);
        stats.showStats(instance.matchId(), store.onlineAssignedPlayers(instance));
    }

    /** Runs end commands and deactivates the match, exactly once per match. */
    private void finishEndPhase(GameInstance instance) {
        if (instance.endPhaseDone()) {
            return;
        }
        instance.setEndPhaseDone(true);
        teardownNow(instance);
    }

    /**
     * Shared immediate teardown tail: end commands, lobby teleport, role
     * reset, and deactivation. Callers run their own announcements, stat
     * handling, and cleanup commands first.
     */
    public void teardownNow(GameInstance instance) {
        long teardownId = instance.matchId();
        List<Player> participants = store.onlineAssignedPlayers(instance).stream()
                .filter(p -> playerStates.role(p).isParticipant()).toList();
        List<Player> spectators = instanceNonePlayers(instance);
        boolean lastMatch = store.instances().size() <= 1;
        stateCommands.runEnd(teardownId, participants, spectators, instance.originLobbyId(), lastMatch);
        scatterEngineOffEnd(instance, participants);
        worldEngine.onMatchEnd(participants, spectators, instance.originLobbyId(), teardownId);
        if (plugin.overrides().getBoolean(instance.originLobbyId(),
                "settings.players.roles.reset-on-game-end.enabled", true)) {
            playerStates.resetRoles(instance.assignedPlayerIds());
        }
        // A finished match fields no sides, even when roles are kept.
        plugin.roleTeams().removeAll(store.onlineAssignedPlayers(instance));
        plugin.spawnCamp().clearMatch(teardownId);
        instance.setActive(false);
        playerStates.clearMatchFor(instance.assignedPlayerIds());
        stats.clearMatch(teardownId);
        flagStore.clearMatch(teardownId);
        store.removeInstance(teardownId);
        plugin.logger().debug("debug.match-end", Map.of("index", GameManager.cellString(instance)));
        worldEngine.prepareNextCell();
        restoreSingleBorder();
        logBorderMode();
        autostart.updateAutostartState();
    }

    /**
     * With the world engine off, gathers participants around the original
     * start center with fresh random offsets. A no-op with the engine on
     * or when no center was recorded.
     */
    private void scatterEngineOffEnd(GameInstance instance, List<Player> participants) {
        Location center = instance.startCenter();
        if (participants.isEmpty() || center == null || center.getWorld() == null
                || configService.getBoolean("world-engine.enabled", false)) {
            return;
        }
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        WorldEngineConfig spawnConfig = WorldEngineConfig.fromConfig(configService);
        List<Location> spawns = MatchTeleportService.spreadSpawnsForConfig(world, centerX, centerZ,
                MatchStartService.SURROUND_RADIUS, participants, spawnConfig);
        for (int index = 0; index < participants.size(); index++) {
            participants.get(index).teleport(spawns.get(index));
        }
    }

    /**
     * Ends every live match at once for server shutdown: tasks cancelled,
     * cleanup commands run, synchronous teardown. No announcements, no
     * scheduler use, and no career statistics are recorded.
     */
    public void shutdownAll() {
        for (GameInstance instance : List.copyOf(store.liveInstances())) {
            shutdown(instance);
        }
    }

    /** Synchronous no-stats teardown of one match for server shutdown. */
    private void shutdown(GameInstance instance) {
        if (!instance.active()) {
            return;
        }
        instance.setEnding(true);
        gameEndListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(new JMatchCancelEvent(instance.matchId()));
        prestart.cancelWaitingTasks(instance);
        prestart.cancelHeadstarts(instance);
        timeLimits.cancelTimeLimit(instance);
        stateCommands.cancelIntervalModifiers(instance.matchId());
        stateCommands.runConsoleCleanup(instance.matchId());
        stateCommands.runPlayerCleanup(instance.matchId(), store.onlineActivePlayers(instance));
        teardownNow(instance);
    }

    /** Cancels the match when exactly one is live; a no-op otherwise. */
    public void cancel() {
        store.singleLiveInstance().ifPresent(this::cancel);
    }

    /** Cancels one match with no winner. */
    public void cancel(GameInstance instance) {
        cancel(instance, false);
    }

    /**
     * Cancels one match with no winner. Career statistics are not
     * saved, but the in-memory match statistics still back the end screen.
     * Runs the normal end delay intermission unless immediate skips
     * straight to teardown. No JMatchEndEvent fires (there is no winner);
     * a JMatchCancelEvent fires instead.
     */
    public void cancel(GameInstance instance, boolean immediate) {
        if (!instance.active()) {
            return;
        }
        if (instance.ending()) {
            if (immediate) {
                showEndStatsOnce(instance);
                finishEndPhase(instance);
            }
            return;
        }
        instance.setEnding(true);
        gameEndListeners.forEach(listener -> listener.accept(instance));
        Bukkit.getPluginManager().callEvent(new JMatchCancelEvent(instance.matchId()));
        prestart.cancelWaitingTasks(instance);
        prestart.cancelHeadstarts(instance);
        timeLimits.cancelTimeLimit(instance);

        messaging.sendToInstance(instance, "game.cancelled", Map.of());
        for (Player player : store.onlineAssignedPlayers(instance)) {
            player.showTitle(Title.title(messages.component("game.cancelled-title"), Component.empty(),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        playerStates.resetOfflinePlayers(Bukkit.getOnlinePlayers(), instance.assignedPlayerIds());
        messaging.playInstanceSound(instance, "game.cancelled-sound");

        // Make all players invulnerable on cancel if configured
        if (plugin.overrides().getBoolean(instance.originLobbyId(),
                "settings.players.invulnerability.on-game-end.enabled", true)) {
            store.onlineAssignedPlayers(instance).forEach(p -> p.setInvulnerable(true));
        }

        // cancel interval modifiers early so they don't fire during the end delay
        stateCommands.cancelIntervalModifiers(instance.matchId());
        // ran before the delay to ensure that any commands that depend on the match being completed can run immediately
        stateCommands.runConsoleCleanup(instance.matchId());
        stateCommands.runPlayerCleanup(instance.matchId(), store.onlineActivePlayers(instance));

        long delay = immediate
                ? 0L
                : Math.max(0L, Math.round(plugin.overrides()
                        .getDouble(instance.originLobbyId(), "match.end-delay", 10.0) * 20.0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> showEndStatsOnce(instance), delay / 2);
        Bukkit.getScheduler().runTaskLater(plugin, () -> finishEndPhase(instance), delay);
    }

    /** Ends the match next tick when exactly one is live; a no-op otherwise. */
    public void finishLater(Role winner) {
        store.singleLiveInstance().ifPresent(instance -> finishLater(instance, winner));
    }

    /** Ends one match on the next tick. */
    public void finishLater(GameInstance instance, Role winner) {
        Bukkit.getScheduler().runTask(plugin, () -> finish(instance, winner));
    }

    private String getWinMessage(Role winner) {
        String text = winner == Role.HUNTER
                ? messages.string("game.hunters-win", "Hunters Win!")
                : messages.string("game.speedrunners-win", "Speedrunners Win!");
        return messages.addSeparators(text);
    }

    /** Online match assignees watching without playing, including eliminated hunters. */
    private List<Player> instanceNonePlayers(GameInstance instance) {
        Set<UUID> assigned = instance.assignedPlayerIds();
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> playerStates.role(p).isWatching() && assigned.contains(p.getUniqueId()))
                .map(p -> (Player) p).toList();
    }

    /** Logs the active border mode; silent when borders cannot apply. */
    public void logBorderMode() {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(configService);
        if (!config.enabled() || !config.worldBorderEnabled()) {
            return;
        }
        plugin.logger().debug("debug.border-mode",
                Map.of("mode", BorderMode.resolve(store.instances().size(), true, true).name()));
    }

    /**
     * Hands the real border to the surviving match when concurrency drops
     * back to one. Honors its start-border phase when it has not begun yet.
     */
    private void restoreSingleBorder() {
        if (store.instances().size() != 1) {
            return;
        }
        GameInstance survivor = store.liveInstances().get(0);
        if (survivor.cellIndex().isPresent()) {
            worldEngine.applyInstanceBorder(survivor.cellIndex().getAsLong(), survivor.begun());
        }
    }

    /**
     * Confines concurrent matches to their cells: players outside their
     * cell are rubber-banded back in and take border damage past the damage
     * buffer. Spectators bypass it like the vanilla border, and the End is
     * left alone until end cells land.
     */
    private void enforcePseudoBorders() {
        if (store.instances().size() < 2) {
            return;
        }
        WorldEngineConfig config = WorldEngineConfig.fromConfig(configService);
        if (!config.enabled() || !config.worldBorderEnabled()) {
            return;
        }
        for (GameInstance instance : store.liveInstances()) {
            if (instance.cellIndex().isEmpty()) {
                continue;
            }
            CellBounds bounds = CellBounds.forCell(instance.cellIndex().getAsLong(),
                    config.cellSize(), config.startBorderDiameter(), !instance.begun());
            for (Player player : store.onlineActivePlayers(instance)) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                World.Environment environment = player.getWorld().getEnvironment();
                if (environment != World.Environment.NETHER && environment != World.Environment.NORMAL) {
                    continue;
                }
                boolean nether = environment == World.Environment.NETHER;
                Location location = player.getLocation();
                double outside = bounds.outsideBy(location.getX(), location.getZ(), nether);
                if (outside <= 0.0) {
                    continue;
                }
                double[] inside = bounds.clampInside(location.getX(), location.getZ(), nether, 2.0);
                if (inside != null) {
                    player.teleport(new Location(location.getWorld(), inside[0], location.getY(), inside[1],
                            location.getYaw(), location.getPitch()));
                }
                if (outside > config.damageBuffer() && config.damageAmount() > 0.0) {
                    player.damage(config.damageAmount());
                }
            }
        }
    }
}
