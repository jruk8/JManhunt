package com.jruk8.jmanhunt.player;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Mirrors manhunt roles onto vanilla scoreboard teams so datapacks and
 * custom modifiers can target sides with selectors like
 * {@code @a[distance=..15,team=HUNTER]}. NONE and AFK players sit in no
 * team. Team colors recolor names (red hunters, green speedrunners)
 * while the name-colors toggle is on; membership works either way.
 */
public final class RoleTeamService {
    public static final String HUNTER_TEAM = "HUNTER";
    public static final String SPEEDRUNNER_TEAM = "SPEEDRUNNER";
    public static final String SPECTATOR_TEAM = "SPECTATOR";
    private static final List<String> TEAMS = List.of(HUNTER_TEAM, SPEEDRUNNER_TEAM, SPECTATOR_TEAM);

    private final PlayerStateStore playerStates;
    private final Supplier<Boolean> teamColors;

    public RoleTeamService(PlayerStateStore playerStates, Supplier<Boolean> teamColors) {
        this.playerStates = playerStates;
        this.teamColors = teamColors;
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

    /** Name color for a role: red hunters, green speedrunners, default otherwise. Pure for tests. */
    public static NamedTextColor colorFor(Role role) {
        if (role == null) {
            return NamedTextColor.WHITE;
        }
        return switch (role) {
            case HUNTER -> NamedTextColor.RED;
            case SPEEDRUNNER -> NamedTextColor.GREEN;
            case SPECTATOR, NONE, AFK -> NamedTextColor.WHITE;
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
        applyColors();
    }

    /** Recolors existing teams from the toggle; resets to default when off. Safe when a team is missing. */
    public void applyColors() {
        Scoreboard board = mainBoard();
        if (board == null) {
            return;
        }
        boolean colors = teamColors.get();
        paint(board, HUNTER_TEAM, Role.HUNTER, colors);
        paint(board, SPEEDRUNNER_TEAM, Role.SPEEDRUNNER, colors);
        paint(board, SPECTATOR_TEAM, Role.SPECTATOR, colors);
    }

    private void paint(Scoreboard board, String name, Role role, boolean colors) {
        Team team = board.getTeam(name);
        if (team != null) {
            team.color(colors ? colorFor(role) : NamedTextColor.WHITE);
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
