package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.lobby.config.MatchSettingsFacade;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.match.LeaveDestination;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.PlayerResetService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import com.jruk8.jmanhunt.world.WorldEngineService;
import com.jruk8.jmanhunt.world.cell.CellBounds;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Mid-match leaves: voluntary leave, auto-leave outside cells, and the
 * setplayer leave paths. A last leaver ends the match for the other side
 * through the after-leave callback.
 */
public final class MatchLeaveService {
    /** Match settings, engine settings, fakes, and role teams. */
    public record LeaveReads(MatchSettingsFacade match,
            com.jruk8.jmanhunt.config.WorldEngineConfig engineSettings,
            FakeSpectatorService fakes, RoleTeamService roleTeams) {
    }

    /** States, compass, commands, engine, store, flags, and callback. */
    public record LeaveMatch(PlayerStateStore playerStates, CompassManager compass,
            GameStateCommandManager stateCommands, WorldEngineService worldEngine,
            MatchStore store, FlagStore flagStore, Consumer<GameInstance> afterLeave) {
    }

    private final LeaveReads reads;
    private final LeaveMatch services;
    private final MessageService messages;
    private final GameMessages game;
    private final MatchMessaging messaging;

    public MatchLeaveService(LeaveReads reads, LeaveMatch services, MessageService messages,
            GameMessages game, MatchMessaging messaging) {
        this.reads = reads;
        this.services = services;
        this.messages = messages;
        this.game = game;
        this.messaging = messaging;
    }

    /** Configured leave destination, SPECTATOR by default. */
    public LeaveDestination leaveDestination(Integer lobby) {
        return LeaveDestination.forLobby(reads.match(), lobby);
    }

    /**
     * Removes players from a match: deactivates them, clears their match
     * state, and moves them to the configured destination. Begun-match
     * participants drop their gear when {@code dropGear} is true (voluntary
     * leave) or are wiped match-end style when false (auto-leave); pre-start
     * leavers keep everything. Hunter and speedrunner departures are
     * announced to the match with the remaining role count. Returns the
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
            services.afterLeave().accept(instance);
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
        Role before = services.playerStates().role(player);
        if (instance.begun() && before.isParticipant()) {
            // A held leaver's gear sits in the fake-spectator snapshot
            // with an empty live inventory: restore first so the drop
            // or wipe below runs on the real gear.
            reads.fakes().restoreSnapshot(player);
            if (dropGear) {
                PlayerResetService.dropAllGear(player);
                services.stateCommands().resetVitals(player);
            } else {
                services.stateCommands().resetPlayer(player);
            }
        }
        services.playerStates().setSpeedrunnerAlive(playerId, false);
        instance.deactivate(playerId);
        services.stateCommands().untrackMatchExit(List.of(playerId));
        services.playerStates().clearMatchFor(List.of(playerId));
        services.flagStore().removePlayer(instance.matchId(), player.getName());
        services.compass().removeCompasses(player);
        applyLeaveDestination(instance, player, dropGear, destination);
        reads.roleTeams().sync(player);
        messages.messageRaw(player, game.getLeaveSuccess(), Map.of());
        return before;
    }

    /**
     * Mid-match setplayer to AFK or NONE: a full leave (gear, compass,
     * announcements, bucket checks) plus a lobby return under the given
     * role. The role must be AFK or NONE. Returns the number removed.
     */
    public int leaveMatchToLobby(GameInstance instance, Player player, Role role) {
        int removed = leaveMatch(instance, List.of(player), instance.begun());
        if (removed == 0) {
            return 0;
        }
        services.playerStates().setRole(player, role);
        services.worldEngine().teleportToLobby(List.of(player), instance.originLobbyId());
        services.worldEngine().setSpawnToLobbyQuiet(List.of(player), instance.originLobbyId());
        reads.fakes().disable(player);
        reads.roleTeams().sync(player);
        return removed;
    }

    /** Moves a leaver to the lobby or the spectator box. */
    private void applyLeaveDestination(GameInstance instance, Player player, boolean dropGear,
            LeaveDestination destination) {
        if (destination == LeaveDestination.LOBBY) {
            services.playerStates().setRole(player, Role.NONE);
            services.worldEngine().teleportToLobby(List.of(player), instance.originLobbyId());
            services.worldEngine().setSpawnToLobbyQuiet(List.of(player), instance.originLobbyId());
            if (reads.fakes().isFakeSpectator(player)) {
                reads.fakes().disable(player);
            }
        } else {
            services.playerStates().setRole(player, Role.SPECTATOR);
            reads.fakes().enable(player);
            if (!dropGear && instance.cellIndex().isPresent()) {
                // Auto-leave pulled them out of bounds: put the watcher
                // back in the cell instead of stranding them outside it.
                services.worldEngine().teleportJoinersToCell(instance, List.of(player),
                        instance.cellIndex().getAsLong());
            }
        }
    }

    /** Announces hunter and speedrunner departures to the match compartment only. */
    private void announceLeaves(GameInstance instance, List<Role> leftRoles, List<String> leftNames) {
        for (int index = 0; index < leftRoles.size(); index++) {
            Role before = leftRoles.get(index);
            if (before == Role.HUNTER) {
                messaging.sendToInstance(instance, game.getHunterLeft(),
                        Map.of("player", leftNames.get(index),
                                "remaining", String.valueOf(services.store().activeHunterCount(instance))));
            } else if (before == Role.SPEEDRUNNER) {
                messaging.sendToInstance(instance, game.getSpeedrunnerLeft(),
                        Map.of("player", leftNames.get(index),
                                "remaining", String.valueOf(services.store().activeRunnerCount(instance))));
            }
        }
    }

    /**
     * Removes a begun-match participant standing outside their cell or in
     * the lobby world, with a reason notice. The End is skipped like the
     * border enforcement, and matches with borders on confine by
     * rubber-band instead, so this only confines when borders are off.
     * Returns true when the player was removed.
     */
    public boolean autoLeaveIfOutside(Player player, Location at) {
        Optional<GameInstance> match = services.store().instanceOf(player.getUniqueId());
        if (match.isEmpty() || !match.get().begun() || match.get().ending()) {
            return false;
        }
        if (!services.playerStates().role(player).isParticipant()) {
            return false;
        }
        GameInstance instance = match.get();
        if (at.getWorld() != null && at.getWorld().getName().equals(services.worldEngine().lobbyWorldName())) {
            messages.messageRaw(player, game.getAutoLeftLobbyWorld(), Map.of());
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
        WorldEngineConfig config = WorldEngineConfig.fromSettings(reads.engineSettings());
        if (!config.enabled() || instance.cellIndex().isEmpty()) {
            return false;
        }
        if (config.worldBorderEnabled()) {
            // Pseudo-borders confine by rubber-band instead; auto-leave
            // only confines matches running with borders off.
            return false;
        }
        boolean nether = environment == World.Environment.NETHER;
        CellBounds bounds = CellBounds.forCell(instance.cellIndex().getAsLong(),
                config.cellSize(), config.startBorderDiameter(),
                config.useStartBorder(instance.begun()));
        if (bounds.contains(at.getX(), at.getZ(), nether)) {
            return false;
        }
        messages.messageRaw(player, game.getAutoLeftBounds(), Map.of());
        leaveMatch(instance, List.of(player), false);
        return true;
    }
}
