package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.player.PlayerResetService;
import org.bukkit.entity.Player;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Player wipes: deferred match-end wipes for offline players plus the
 * role-less post-crash cleanup roster. Repository failures log and
 * continue; gameplay never blocks on persistence.
 */
public final class PlayerWipeService {
    private final JManhuntPlugin plugin;
    private final PlayerResetService resets;
    private final Set<UUID> pendingEndWipes = new HashSet<>();
    /** In-match UUIDs still owed a post-crash wipe; mirrored in crash_cleanup. */
    private final Set<UUID> pendingCrashWipes = new HashSet<>();

    public PlayerWipeService(JManhuntPlugin plugin, PlayerResetService resets) {
        this.plugin = plugin;
        this.resets = resets;
    }

    /** Full match-end style wipe for one player. */
    public void resetPlayer(Player player) {
        resets.resetPlayer(player);
    }

    /** Vitals-only reset for one player. */
    public void resetVitals(Player player) {
        resets.resetVitals(player);
    }

    /** True when the end phase wipes participant inventories for the lobby. */
    public boolean endWipeEnabled(int lobbyId) {
        return resets.endWipeEnabled(lobbyId);
    }

    /**
     * Defers the match-end wipe for players who are offline at
     * teardown. Memory only: matches never survive a restart anyway.
     */
    public void markPendingEndWipe(Collection<UUID> playerIds) {
        pendingEndWipes.addAll(playerIds);
    }

    /**
     * Runs a deferred match-end wipe for a rejoiner. Returns true
     * when a wipe was pending and ran.
     */
    public boolean applyPendingEndWipe(Player player, Consumer<Player> wipe) {
        if (!pendingEndWipes.remove(player.getUniqueId())) {
            return false;
        }
        wipe.accept(player);
        return true;
    }

    /**
     * Tracks match-state entry for post-crash cleanup: every
     * speedrunner, hunter, and spectator activation. The memory set
     * answers joins; the crash_cleanup rows survive the crash itself.
     * Failures log and continue, never blocking the match.
     */
    public void trackMatchEntry(Collection<UUID> playerIds) {
        pendingCrashWipes.addAll(playerIds);
        EngineStateRepository repository = plugin.engineStates();
        if (repository == null) {
            return;
        }
        for (UUID playerId : playerIds) {
            try {
                repository.markCrashCleanup(playerId);
            } catch (SQLException failed) {
                plugin.logger().warning("Could not track crash cleanup for " + playerId + ": "
                        + failed.getMessage());
            }
        }
    }

    /**
     * Drops clean match-state exits from post-crash cleanup: voluntary
     * leave, auto-leave, and teardown back to normal play. Transferred
     * spectators stay tracked under their new match.
     */
    public void untrackMatchExit(Collection<UUID> playerIds) {
        pendingCrashWipes.removeAll(playerIds);
        EngineStateRepository repository = plugin.engineStates();
        if (repository == null) {
            return;
        }
        for (UUID playerId : playerIds) {
            try {
                repository.clearCrashCleanup(playerId);
            } catch (SQLException failed) {
                plugin.logger().warning("Could not clear crash cleanup for " + playerId + ": "
                        + failed.getMessage());
            }
        }
    }

    /**
     * Loads surviving crash_cleanup rows after enable. Rows are honored
     * regardless of the crash flag so a player who misses the first
     * post-crash restart is still wiped on their next join; each row
     * deletes only when its player is actually wiped.
     */
    public void loadCrashCleanup() {
        EngineStateRepository repository = plugin.engineStates();
        if (repository == null) {
            return;
        }
        try {
            pendingCrashWipes.addAll(repository.crashCleanupIds());
        } catch (SQLException failed) {
            plugin.logger().warning("Could not load crash cleanup rows: " + failed.getMessage());
        }
    }

    /**
     * Runs the post-crash wipe for a rejoiner. Returns true when a wipe
     * was pending and ran. Match stats are memory-only and died with
     * the crash, career stats were never saved for the crashed match,
     * and roles are memory-only too, so the full wipe plus the
     * join-time toolbar strip is the complete cleanup.
     */
    public boolean applyPendingCrashWipe(Player player, Consumer<Player> wipe) {
        UUID playerId = player.getUniqueId();
        if (!pendingCrashWipes.remove(playerId)) {
            return false;
        }
        wipe.accept(player);
        EngineStateRepository repository = plugin.engineStates();
        if (repository == null) {
            return true;
        }
        try {
            repository.clearCrashCleanup(playerId);
        } catch (SQLException failed) {
            plugin.logger().warning("Could not clear crash cleanup for " + playerId + ": "
                    + failed.getMessage());
        }
        return true;
    }
}
