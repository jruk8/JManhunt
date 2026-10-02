package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpawnCampService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Mid-match elimination outside the death pipeline: the
 * {@code <loseplayer>} sink. Kills quietly, drops the player to
 * spectator, announces the loss, then runs the standard elimination
 * check through the finish hook.
 */
public final class MatchEliminationService {
    /** Role plus fake-spectator state. */
    public record ElimPlayers(PlayerStateStore states, FakeSpectatorService fakes) {
    }

    /** Scheduler, spawn camp, and role teams edge. */
    public record ElimEdge(TaskScheduler tasks, SpawnCampService spawnCamp,
            RoleTeamService roleTeams) {
    }

    /** Match store, flags, and elimination hook. */
    public record ElimMatch(MatchStore store, FlagStore flagStore,
            Consumer<GameInstance> onEliminated) {
    }

    private final ElimPlayers players;
    private final ElimEdge edge;
    private final CompassManager compass;
    private final MatchMessaging messaging;
    private final ElimMatch match;

    public MatchEliminationService(ElimPlayers players, ElimEdge edge, CompassManager compass,
            MatchMessaging messaging, ElimMatch match) {
        this.players = players;
        this.edge = edge;
        this.compass = compass;
        this.messaging = messaging;
        this.match = match;
    }

    /**
     * {@code <loseplayer>} sink target: kills a runner/hunter by name,
     * drops them to spectator, announces the loss, and runs the
     * standard elimination check. Works when already dead (the
     * transition still runs); the quiet kill keeps the death
     * pipeline's own chatter silent. Returns false with no effect
     * when the match is not live or the player is unknown, offline,
     * or not a runner/hunter.
     */
    public boolean losePlayer(long matchId, String playerName, String reason) {
        Optional<GameInstance> match = this.match.store().instance(matchId);
        if (match.isEmpty() || !match.get().begun() || match.get().ending()) {
            return false;
        }
        GameInstance instance = match.get();
        Player player = namedAssignee(instance, playerName);
        if (player == null) {
            return false;
        }
        Role role = players.states().role(player);
        if (role != Role.HUNTER && role != Role.SPEEDRUNNER) {
            return false;
        }
        edge.spawnCamp().quietKill(player);
        instance.recordDeath(player.getUniqueId(), player.getName(), role);
        players.states().setRole(player.getUniqueId(), Role.SPECTATOR);
        edge.roleTeams().sync(player);
        instance.deactivate(player.getUniqueId());
        compass.clearHotspotHistory(player.getUniqueId());
        compass.reconcileTeammateModes(instance);
        this.match.flagStore().removePlayer(matchId, player.getName());
        edge.tasks().run(() -> {
            players.fakes().enable(player);
            compass.removeCompasses(player);
        });
        messaging.sendToInstance(instance, "game.loseplayer",
                Map.of("player", player.getName(), "reason", reason));
        messaging.playInstanceSound(instance,
                role == Role.HUNTER ? "game.hunter-death" : "game.speedrunner-death");
        this.match.onEliminated().accept(instance);
        return true;
    }

    /** Online match assignee by name, case-insensitive; null when none. */
    private Player namedAssignee(GameInstance instance, String playerName) {
        for (UUID id : instance.assignedPlayerIds()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.getName().equalsIgnoreCase(playerName)) {
                return player;
            }
        }
        return null;
    }
}
