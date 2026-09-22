package com.jruk8.jmanhunt.world;

import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.JManhuntPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps match players inside their own cell across dimensions. Nether
 * portals land in the match's cell slice of the shared nether instead of
 * pairing across matches, end portals lead to the match's dedicated end,
 * and leaving the end returns to the match cell.
 */
public final class PortalRouter implements Listener {
    private final JManhuntPlugin plugin;
    private final GameManager game;
    private final WorldEngineService worldEngine;

    public PortalRouter(JManhuntPlugin plugin, GameManager game, WorldEngineService worldEngine) {
        this.plugin = plugin;
        this.game = game;
        this.worldEngine = worldEngine;
    }

    @EventHandler public void onPortal(PlayerPortalEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isEmpty() || match.get().cellIndex().isEmpty()) {
            return;
        }
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        if (!config.enabled()) {
            return;
        }
        GameInstance instance = match.get();
        long cell = instance.cellIndex().getAsLong();
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            rerouteNether(event, player, instance, config, cell);
        } else if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            rerouteEnd(event, player, instance, cell);
        }
    }

    /** Clamps a nether portal destination into the match cell slice. */
    private void rerouteNether(PlayerPortalEvent event, Player player, GameInstance instance,
                               WorldEngineConfig config, long cell) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        boolean netherTarget;
        if (to.getWorld().getEnvironment() == World.Environment.NETHER) {
            netherTarget = true;
        } else if (to.getWorld().getEnvironment() == World.Environment.NORMAL) {
            netherTarget = false;
        } else {
            return;
        }
        CellBounds bounds = CellBounds.forCell(cell, config.cellSize(),
                config.startBorderDiameter(), !instance.begun());
        double[] inside = bounds.clampInside(to.getX(), to.getZ(), netherTarget, 2.0);
        if (inside == null) {
            return;
        }
        event.setTo(new Location(to.getWorld(), inside[0], to.getY(), inside[1], to.getYaw(), to.getPitch()));
        plugin.logger().debug("debug.portal-reroute",
                Map.of("player", player.getName(), "cell", String.valueOf(cell)));
    }

    /** Sends end entries to the dedicated end and exits back to the cell. */
    private void rerouteEnd(PlayerPortalEvent event, Player player, GameInstance instance, long cell) {
        World fromWorld = event.getFrom().getWorld();
        if (fromWorld == null) {
            return;
        }
        if (fromWorld.getEnvironment() == World.Environment.THE_END) {
            Optional<Location> root = worldEngine.cellRoot(cell);
            if (root.isEmpty()) {
                return;
            }
            event.setTo(root.get());
            plugin.logger().debug("debug.portal-reroute",
                    Map.of("player", player.getName(), "cell", String.valueOf(cell)));
        } else if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
            Optional<World> end = worldEngine.matchEndWorld(instance.matchId());
            if (end.isEmpty()) {
                return;
            }
            Location to = event.getTo();
            if (to == null) {
                to = end.get().getSpawnLocation();
            } else {
                to.setWorld(end.get());
            }
            event.setTo(to);
            plugin.logger().debug("debug.portal-reroute",
                    Map.of("player", player.getName(), "cell", end.get().getName()));
        }
    }
}
