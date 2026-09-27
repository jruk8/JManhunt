package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.RosterValues;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Live roster reads for one match: assignee roles, INTERVAL-eligible
 * names per role, and online locations. Tags call it through
 * {@link RosterValues}; all Bukkit reads stay here.
 */
public final class MatchRosterValues implements RosterValues {
    private final GameManager game;
    private final PlayerStateStore playerStates;
    private final FakeSpectatorService fakeSpectators;
    private final long matchId;

    public MatchRosterValues(GameManager game, PlayerStateStore playerStates,
            FakeSpectatorService fakeSpectators, long matchId) {
        this.game = game;
        this.playerStates = playerStates;
        this.fakeSpectators = fakeSpectators;
        this.matchId = matchId;
    }

    @Override
    public Optional<String> roleOf(String playerName) {
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty()) {
            return Optional.empty();
        }
        for (UUID id : match.get().assignedPlayerIds()) {
            Player online = Bukkit.getPlayer(id);
            String name = online != null ? online.getName()
                    : Bukkit.getOfflinePlayer(id).getName();
            if (name != null && name.equalsIgnoreCase(playerName)) {
                return Optional.of(playerStates.role(id).name());
            }
        }
        return Optional.empty();
    }

    @Override
    public List<String> activePlayers(String role) {
        Role want;
        try {
            want = Role.valueOf(role);
        } catch (IllegalArgumentException unmatched) {
            return List.of();
        }
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (UUID id : match.get().assignedPlayerIds()) {
            if (playerStates.role(id) != want) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            if (IntervalDispatcher.intervalSkipWhy(playerStates.role(player),
                    game.isActiveInInstance(matchId, id),
                    player.isDead(), fakeSpectators.isFakeSpectator(player)).isPresent()) {
                continue;
            }
            names.add(player.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    @Override
    public Optional<Location> locationOf(String playerName) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(playerName)) {
                return Optional.of(player.getLocation());
            }
        }
        return Optional.empty();
    }
}
