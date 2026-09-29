package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
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
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final CompassManager compass;
    private final MatchStore store;
    private final MatchMessaging messaging;
    private final FlagStore flagStore;
    private final Consumer<GameInstance> onEliminated;

    public MatchEliminationService(JManhuntPlugin plugin, PlayerStateStore playerStates,
            CompassManager compass, MatchStore store, MatchMessaging messaging,
            FlagStore flagStore, Consumer<GameInstance> onEliminated) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.compass = compass;
        this.store = store;
        this.messaging = messaging;
        this.flagStore = flagStore;
        this.onEliminated = onEliminated;
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
        Optional<GameInstance> match = store.instance(matchId);
        if (match.isEmpty() || !match.get().begun() || match.get().ending()) {
            return false;
        }
        GameInstance instance = match.get();
        Player player = namedAssignee(instance, playerName);
        if (player == null) {
            return false;
        }
        Role role = playerStates.role(player);
        if (role != Role.HUNTER && role != Role.SPEEDRUNNER) {
            return false;
        }
        plugin.spawnCamp().quietKill(player);
        instance.recordDeath(player.getUniqueId(), player.getName(), role);
        playerStates.setRole(player.getUniqueId(), Role.SPECTATOR);
        plugin.roleTeams().sync(player);
        instance.deactivate(player.getUniqueId());
        compass.reconcileTeammateModes(instance);
        flagStore.removePlayer(matchId, player.getName());
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.fakeSpectators().enable(player);
            compass.removeCompasses(player);
        });
        messaging.sendToInstance(instance, "game.loseplayer",
                Map.of("player", player.getName(), "reason", reason));
        messaging.playInstanceSound(instance,
                role == Role.HUNTER ? "game.hunter-death" : "game.speedrunner-death");
        onEliminated.accept(instance);
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
