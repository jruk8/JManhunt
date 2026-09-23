package com.jruk8.jmanhunt.lobby.bounds;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.core.DebugService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
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

    private final JManhuntPlugin plugin;
    private final LobbyService lobbies;
    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final MessageService messages;
    private final SoundService sounds;
    private final Supplier<String> lobbyWorldName;
    private final DebugService debug;
    /** Last checked block position per player, packed for one-lookup exits. */
    private final Map<UUID, Long> lastChecked = new HashMap<>();

    public LobbyBoundsService(JManhuntPlugin plugin, LobbyService lobbies, PlayerStateStore playerStates,
            GameManager game, MessageService messages, SoundService sounds, Supplier<String> lobbyWorldName,
            DebugService debug) {
        this.plugin = plugin;
        this.lobbies = lobbies;
        this.playerStates = playerStates;
        this.game = game;
        this.messages = messages;
        this.sounds = sounds;
        this.lobbyWorldName = lobbyWorldName;
        this.debug = debug;
        Bukkit.getScheduler().runTaskTimer(plugin, this::showBoundsParticles,
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
        if (!player.getWorld().getName().equals(lobbyWorldName.get())) {
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
            return;
        }
        int lobbyId = match.getAsInt();
        Optional<Lobby> current = lobbies.lobbyOf(player.getUniqueId());
        if (current.isPresent() && current.get().id() == lobbyId) {
            return;
        }
        if (!lobbies.multiLobbyAllowed() && lobbyId != 0) {
            return;
        }
        if (game.instanceOf(player.getUniqueId()).isPresent()) {
            return;
        }
        Role previousRole = playerStates.role(player);
        lobbies.setLobby(player.getUniqueId(), lobbyId);
        playerStates.setRole(player, Role.NONE);
        plugin.roleTeams().sync(player);
        if (previousRole != Role.NONE) {
            messages.message(player, "manhunt.role-assigned",
                    Map.of("role", messages.roleName(Role.NONE)));
            sounds.playNeutralSound(player);
        }
        game.updateAutostartState();
    }

    /**
     * Draws lobby edge particles for debug players standing near a box.
     * Boxes are colored in lobby-id order from the contrast palette.
     */
    private void showBoundsParticles() {
        if (debug.debugPlayerIds().isEmpty()) {
            return;
        }
        Map<Integer, LobbyBounds.Bound> bounds = boundsByLobby();
        if (bounds.isEmpty()) {
            return;
        }
        List<Integer> ids = new ArrayList<>(bounds.keySet());
        ids.sort(Integer::compareTo);
        double radiusSquared = LobbyBoundsPalette.CHECK_RADIUS_BLOCKS * LobbyBoundsPalette.CHECK_RADIUS_BLOCKS;
        String lobbyWorld = lobbyWorldName.get();
        for (UUID playerId : debug.debugPlayerIds()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.getWorld().getName().equals(lobbyWorld)) {
                continue;
            }
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
    }

    /** Complete bounds boxes keyed by lobby id; partial entries are skipped. */
    private Map<Integer, LobbyBounds.Bound> boundsByLobby() {
        Map<Integer, LobbyBounds.Bound> bounds = new HashMap<>();
        LobbyConfig lobbyConfig = plugin.lobbyConfig();
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
