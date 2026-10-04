package com.jruk8.jmanhunt.lobby.bounds;

import com.jruk8.jmanhunt.config.LobbiesConfig;
import com.jruk8.jmanhunt.core.DebugService;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.RolePadService;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;

/**
 * Lobby boundary boxes in the lobby world: a player who walks into a
 * configured box joins that lobby with role none, like
 * /manhunt lobby join with role none (minus the teleport, since they
 * are already there). Overlapping boxes resolve to the one whose
 * midpoint is nearest. Guards exit cheapest-first like the role pads.
 */
public final class LobbyBoundsService implements Listener {

    /** World name plus pending-corner views. */
    public record BoundsSuppliers(Supplier<String> lobbyWorldName,
            Supplier<Map<UUID, Location>> boundPos1, Supplier<Map<UUID, Location>> boundPos2,
            Supplier<Map<UUID, Location>> devPos1, Supplier<Map<UUID, Location>> devPos2) {
    }

    /** Lobby, bounds, player, match, and debug context. */
    public record BoundsContext(LobbyService lobbies, LobbiesConfig.Bounds boundsSettings,
            PlayerStateStore playerStates, GameManager game, DebugService debug) {
    }

    /** Scheduler, role teams, and lobby store edge. */
    public record BoundsEdge(TaskScheduler tasks, RoleTeamService roleTeams,
            LobbyConfig lobbyConfig) {
    }

    private final BoundsSuppliers suppliers;
    private final BoundsContext context;
    private final BoundsEdge edge;
    /** Last checked block position per player, packed for one-lookup exits. */
    private final Map<UUID, Long> lastChecked = new HashMap<>();

    public LobbyBoundsService(BoundsSuppliers suppliers, BoundsContext context, BoundsEdge edge) {
        this.suppliers = suppliers;
        this.context = context;
        this.edge = edge;
        edge.tasks().runTimer(this::showBoundsParticles,
                LobbyBoundsPalette.refreshTicks(), LobbyBoundsPalette.refreshTicks());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        check(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        check(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastChecked.remove(event.getPlayer().getUniqueId());
    }

    private void check(Player player) {
        if (!player.getWorld().getName().equals(suppliers.lobbyWorldName().get())) {
            return;
        }
        Location location = player.getLocation();
        long packed = RolePadService.packBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Long previous = lastChecked.put(player.getUniqueId(), packed);
        if (previous != null && previous == packed) {
            return;
        }
        Map<Integer, LobbyBounds.Bound> bounds = boundsByLobby();
        if (bounds.isEmpty()) {
            return;
        }
        OptionalInt match = LobbyBounds.match(bounds,
                location.getX(), location.getY(), location.getZ());
        if (match.isEmpty()) {
            exitBounds(player, bounds);
            return;
        }
        int lobbyId = match.getAsInt();
        Optional<Lobby> current = context.lobbies().lobbyOf(player.getUniqueId());
        if (current.isPresent() && current.get().id() == lobbyId) {
            return;
        }
        if (!context.lobbies().multiLobbyAllowed() && lobbyId != 0) {
            return;
        }
        if (context.game().instanceOf(player.getUniqueId()).isPresent()) {
            return;
        }
        context.lobbies().setLobby(player.getUniqueId(), lobbyId);
        context.playerStates().setRole(player, Role.NONE);
        edge.roleTeams().sync(player);
        context.lobbies().applyLobbyCollisions(player);
        context.lobbies().announceLobbyChange(player, lobbyIdOf(current), OptionalInt.of(lobbyId));
        context.game().updateAutostartState();
    }

    /**
     * Draws lobby edge particles for debug players standing near a box.
     * Boxes are colored in lobby-id order from the contrast palette.
     * Each debug player also sees white draft boxes for their own
     * pending lobby and dev schem corners.
     */
    private void showBoundsParticles() {
        if (context.debug().playerLevels().isEmpty()) {
            return;
        }
        Map<Integer, LobbyBounds.Bound> bounds = boundsByLobby();
        List<Integer> ids = new ArrayList<>(bounds.keySet());
        ids.sort(Integer::compareTo);
        double radiusSquared = LobbyBoundsPalette.CHECK_RADIUS_BLOCKS * LobbyBoundsPalette.CHECK_RADIUS_BLOCKS;
        String lobbyWorld = suppliers.lobbyWorldName().get();
        for (UUID playerId : context.debug().playerLevels().keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            if (player.getWorld().getName().equals(lobbyWorld)) {
                Location at = player.getLocation();
                for (int order = 0; order < ids.size(); order++) {
                    LobbyBounds.Bound bound = bounds.get(ids.get(order));
                    if (bound == null || LobbyBounds.distanceSquaredToBox(
                            bound, at.getX(), at.getY(), at.getZ()) > radiusSquared) {
                        continue;
                    }
                    Particle.DustOptions dust = new Particle.DustOptions(
                            LobbyBoundsPalette.colorForIndex(order), LobbyBoundsPalette.PARTICLE_SIZE);
                    for (double[] point : LobbyBounds.edgePoints(bound, LobbyBoundsPalette.EDGE_STEP_BLOCKS)) {
                        player.spawnParticle(Particle.DUST, point[0], point[1], point[2], 1, dust);
                    }
                }
            }
            drawPending(player, suppliers.boundPos1().get().get(playerId), suppliers.boundPos2().get().get(playerId));
            drawPending(player, suppliers.devPos1().get().get(playerId), suppliers.devPos2().get().get(playerId));
        }
    }

    /**
     * Draws one white draft box from pending corners in the player's
     * world. A lone corner draws its single block.
     */
    private static OptionalInt lobbyIdOf(Optional<Lobby> lobby) {
        return lobby.map(value -> OptionalInt.of(value.id())).orElseGet(OptionalInt::empty);
    }

    /**
     * Handles a lobby member standing outside every box. Under
     * KEEP_IN_LOBBY the exit is ignored and membership stays; under
     * EXIT_LOBBY a destination inside another lobby's box transitions
     * there instead, else the member leaves for the exit destination.
     * Only fires for members whose own lobby has complete bounds, and
     * never for players in a live match.
     */
    private void exitBounds(Player player, Map<Integer, LobbyBounds.Bound> bounds) {
        Optional<Lobby> current = context.lobbies().lobbyOf(player.getUniqueId());
        if (current.isEmpty() || !bounds.containsKey(current.get().id())) {
            return;
        }
        if (context.game().instanceOf(player.getUniqueId()).isPresent()) {
            return;
        }
        int lobbyId = current.get().id();
        if (exitBehavior() == LobbyBoundsExitBehavior.KEEP_IN_LOBBY) {
            return;
        }
        Location at = player.getLocation();
        OptionalInt transition = transitionTarget(bounds, lobbyId,
                at.getX(), at.getY(), at.getZ());
        if (transition.isPresent()) {
            int target = transition.getAsInt();
            context.lobbies().setLobby(player.getUniqueId(), target);
            context.lobbies().applyLobbyCollisions(player);
            context.lobbies().announceLobbyChange(player, OptionalInt.of(lobbyId), OptionalInt.of(target));
            context.game().updateAutostartState();
            return;
        }
        int target = context.boundsSettings().getExitLobbyId();
        int destination = exitDestination(lobbyId, target, context.lobbies().multiLobbyAllowed());
        if (destination == lobbyId) {
            return;
        }
        if (destination < 0) {
            context.lobbies().remove(player.getUniqueId());
            edge.roleTeams().sync(player);
            context.lobbies().announceLobbyChange(player, OptionalInt.of(lobbyId), OptionalInt.empty());
        } else {
            context.lobbies().setLobby(player.getUniqueId(), destination);
            context.lobbies().applyLobbyCollisions(player);
            context.lobbies().announceLobbyChange(player, OptionalInt.of(lobbyId), OptionalInt.of(destination));
        }
        context.game().updateAutostartState();
    }

    private LobbyBoundsExitBehavior exitBehavior() {
        return LobbyBoundsExitBehavior.parse(context.boundsSettings().getExitBehavior());
    }

    /**
     * Different lobby whose box contains the destination, if any. Pure
     * for tests.
     */
    static OptionalInt transitionTarget(Map<Integer, LobbyBounds.Bound> bounds, int currentId,
            double x, double y, double z) {
        OptionalInt match = LobbyBounds.match(bounds, x, y, z);
        if (match.isPresent() && match.getAsInt() != currentId) {
            return match;
        }
        return OptionalInt.empty();
    }

    /**
     * Exit destination under EXIT_LOBBY: the configured target lobby,
     * -1 for lobby-less, or the current lobby when the exit changes
     * nothing. Pure for tests.
     */
    static int exitDestination(int currentId, int target, boolean multiLobbyAllowed) {
        if (target == currentId) {
            return currentId;
        }
        if (target < 0 || (!multiLobbyAllowed && target != 0)) {
            return -1;
        }
        return target;
    }

    private void drawPending(Player player, Location first, Location second) {
        Location from = inPlayerWorld(player, first);
        Location to = inPlayerWorld(player, second);
        if (from == null && to == null) {
            return;
        }
        Location start = from != null ? from : to;
        Location end = to != null ? to : from;
        LobbyBounds.Bound box = new LobbyBounds.Bound(
                start.getBlockX(), start.getBlockY(), start.getBlockZ(),
                end.getBlockX(), end.getBlockY(), end.getBlockZ());
        Particle.DustOptions dust = new Particle.DustOptions(
                LobbyBoundsPalette.PENDING_COLOR, LobbyBoundsPalette.PARTICLE_SIZE);
        for (double[] point : LobbyBounds.edgePoints(box, LobbyBoundsPalette.EDGE_STEP_BLOCKS)) {
            player.spawnParticle(Particle.DUST, point[0], point[1], point[2], 1, dust);
        }
    }

    private static Location inPlayerWorld(Player player, Location pos) {
        if (pos == null || pos.getWorld() == null) {
            return null;
        }
        return pos.getWorld().equals(player.getWorld()) ? pos : null;
    }

    /** Complete bounds boxes keyed by lobby id; partial entries are skipped. */
    private Map<Integer, LobbyBounds.Bound> boundsByLobby() {
        Map<Integer, LobbyBounds.Bound> bounds = new HashMap<>();
        LobbyConfig lobbyConfig = edge.lobbyConfig();
        if (lobbyConfig == null || lobbyConfig.getLobbies() == null) {
            return bounds;
        }
        for (Map.Entry<String, LobbyConfig.LobbyEntry> entry : lobbyConfig.getLobbies().entrySet()) {
            int id;
            try {
                id = Integer.parseInt(entry.getKey().trim());
            } catch (NumberFormatException expected) {
                continue;
            }
            if (id < 0 || entry.getValue() == null || entry.getValue().getBounds() == null) {
                continue;
            }
            LobbyConfig.Position pos1 = entry.getValue().getBounds().getPos1();
            LobbyConfig.Position pos2 = entry.getValue().getBounds().getPos2();
            if (pos1 == null || pos2 == null) {
                continue;
            }
            bounds.put(id, new LobbyBounds.Bound(
                    pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ()));
        }
        return bounds;
    }
}
