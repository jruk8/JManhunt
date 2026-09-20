package com.jruk8.jmanhunt.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Mirrors manhunt roles onto vanilla scoreboard teams so datapacks and
 * custom modifiers can target sides with selectors like
 * {@code @a[distance=..15,team=HUNTER]}. NONE and AFK players sit in no
 * team. Teams carry no color or friendly-fire options: membership only.
 */
public final class RoleTeamService {
    public static final String HUNTER_TEAM = "HUNTER";
    public static final String SPEEDRUNNER_TEAM = "SPEEDRUNNER";
    public static final String SPECTATOR_TEAM = "SPECTATOR";
    private static final List<String> TEAMS = List.of(HUNTER_TEAM, SPEEDRUNNER_TEAM, SPECTATOR_TEAM);

    private final PlayerStateStore playerStates;

    public RoleTeamService(PlayerStateStore playerStates) {
        this.playerStates = playerStates;
    }

    /** Team name for a role, empty for NONE and AFK. Pure for tests. */
    public static Optional<String> teamFor(Role role) {
        if (role == null) {
            return Optional.empty();
        }
        return switch (role) {
            case HUNTER -> Optional.of(HUNTER_TEAM);
            case SPEEDRUNNER -> Optional.of(SPEEDRUNNER_TEAM);
            case SPECTATOR -> Optional.of(SPECTATOR_TEAM);
            case NONE, AFK -> Optional.empty();
        };
    }

    /** Creates missing teams on the main scoreboard. Idempotent. */
    public void ensureTeams() {
        Scoreboard board = mainBoard();
        if (board == null) {
            return;
        }
        for (String name : TEAMS) {
            if (board.getTeam(name) == null) {
                board.registerNewTeam(name);
            }
        }
    }

    /** Repairs one player's team membership from their current role. */
    public void sync(Player player) {
        ensureTeams();
        Scoreboard board = mainBoard();
        if (board == null) {
            return;
        }
        for (String name : TEAMS) {
            Team team = board.getTeam(name);
            if (team != null) {
                team.removeEntry(player.getName());
            }
        }
        teamFor(playerStates.role(player)).ifPresent(name -> {
            Team team = board.getTeam(name);
            if (team != null) {
                team.addEntry(player.getName());
            }
        });
    }

    /** Drops one player from every manhunt team. */
    public void remove(Player player) {
        Scoreboard board = mainBoard();
        if (board == null) {
            return;
        }
        for (String name : TEAMS) {
            Team team = board.getTeam(name);
            if (team != null) {
                team.removeEntry(player.getName());
            }
        }
    }

    /** Drops several players, used when a match tears down. */
    public void removeAll(Collection<? extends Player> players) {
        for (Player player : players) {
            remove(player);
        }
    }

    /** Creates teams and syncs everyone online. Runs on enable. */
    public void syncAll() {
        ensureTeams();
        for (Player player : Bukkit.getOnlinePlayers()) {
            sync(player);
        }
    }

    private Scoreboard mainBoard() {
        if (Bukkit.getScoreboardManager() == null) {
            return null;
        }
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }
}
