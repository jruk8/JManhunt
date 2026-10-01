package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.player.CapLimits;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lobby queue-cap checks shared by setplayer and lobby join: live
 * role counts, configured caps, and the force/cap recording rule.
 */
public final class CapSupport {
    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final ConfigService config;

    public CapSupport(PlayerStateStore playerStates, GameManager game, ConfigService config) {
        this.playerStates = playerStates;
        this.game = game;
        this.config = config;
    }

    /** True when the role fits the lobby queue; records capped lobbies. */
    public boolean capAllows(Optional<Lobby> lobby, Role role, boolean force,
            Map<Integer, Set<Role>> cappedIn) {
        if (force || !role.isParticipant() || lobby.isEmpty()) {
            return true;
        }
        if (CapLimits.allows(lobbyRoleCount(lobby.get(), role), capFor(role))) {
            return true;
        }
        cappedIn.computeIfAbsent(lobby.get().id(), key -> new HashSet<>()).add(role);
        return false;
    }

    /** Live in-lobby (not in-match) count for a role. */
    public int lobbyRoleCount(Lobby lobby, Role role) {
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (playerStates.role(online) == role && lobby.contains(online.getUniqueId())
                    && game.instanceOf(online.getUniqueId()).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** Configured queue cap for a participant role, -1 when uncapped. */
    public int capFor(Role role) {
        return config.getInt(
                "advanced.lobbies.queue-caps." + role.name().toLowerCase(Locale.ROOT), -1);
    }
}
