package com.jruk8.jmanhunt.match.listeners;

import com.jruk8.jmanhunt.player.DimensionEnterTracker;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.world.WorldEngineService;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.util.EnumSet;
import java.util.Optional;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.WinCondition;
import com.jruk8.jmanhunt.match.WinConditionEngine;

/** Teleports, world changes, movement sightings, and dimension triggers. */
public final class PlayerMovementListener implements Listener {
    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final WinConditionEngine winConditionEngine;
    private final WorldEngineService worldEngine;
    private final DimensionEnterTracker dimensionEnterTracker = new DimensionEnterTracker();

    public PlayerMovementListener(PlayerStateStore playerStates, GameManager game,
            WinConditionEngine winConditionEngine, WorldEngineService worldEngine) {
        this.playerStates = playerStates;
        this.game = game;
        this.winConditionEngine = winConditionEngine;
        this.worldEngine = worldEngine;
        // Dimension-enter tracking is keyed by match, so each finished
        // match is dropped the same way.
        game.addGameEndListener(instance -> dimensionEnterTracker.dropMatch(instance.matchId()));
    }

    @EventHandler public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (event.getTo() != null && worldEngine.isLobbyWorld(event.getTo().getWorld())) {
            worldEngine.careFor(player);
        }
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isPresent() && playerStates.role(player).isParticipant()
                && player.getGameMode() != GameMode.SPECTATOR) {
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
                || !playerStates.role(player).isParticipant()) {
            return;
        }
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
}
