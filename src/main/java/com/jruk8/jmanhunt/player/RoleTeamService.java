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
 * role team. Team colors recolor names (red hunters, green
 * speedrunners) while the name-colors toggle is on; membership works
 * either way. The lobby twins below carry the same colors with
 * collision rule NEVER, so lobby occupants pass through each other
 * (NEVER exempts members from every pairing, not just teammates);
 * the lobby service moves players between the two sets.
 */
public final class RoleTeamService {
    public static final String HUNTER_TEAM = "HUNTER";
    public static final String SPEEDRUNNER_TEAM = "SPEEDRUNNER";
    public static final String SPECTATOR_TEAM = "SPECTATOR";
    private static final List<String> TEAMS = List.of(HUNTER_TEAM, SPEEDRUNNER_TEAM, SPECTATOR_TEAM);
    public static final String LOBBY_HUNTER_TEAM = "jl_hunter";
    public static final String LOBBY_SPEEDRUNNER_TEAM = "jl_speedrunner";
    public static final String LOBBY_SPECTATOR_TEAM = "jl_spectator";
    public static final String LOBBY_NONE_TEAM = "jl_none";
    private static final List<String> LOBBY_TEAMS = List.of(LOBBY_HUNTER_TEAM,
            LOBBY_SPEEDRUNNER_TEAM, LOBBY_SPECTATOR_TEAM, LOBBY_NONE_TEAM);

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

    /**
     * Lobby team for a role: every role holds one, NONE and AFK
     * sharing the colorless twin. Pure for tests.
     */
    public static String lobbyTeamFor(Role role) {
        if (role == null) {
            return LOBBY_NONE_TEAM;
        }
        return switch (role) {
            case HUNTER -> LOBBY_HUNTER_TEAM;
            case SPEEDRUNNER -> LOBBY_SPEEDRUNNER_TEAM;
            case SPECTATOR -> LOBBY_SPECTATOR_TEAM;
            case NONE, AFK -> LOBBY_NONE_TEAM;
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
        paint(board, LOBBY_HUNTER_TEAM, Role.HUNTER, colors);
        paint(board, LOBBY_SPEEDRUNNER_TEAM, Role.SPEEDRUNNER, colors);
        paint(board, LOBBY_SPECTATOR_TEAM, Role.SPECTATOR, colors);
        paint(board, LOBBY_NONE_TEAM, Role.NONE, colors);
    }

    private void paint(Scoreboard board, String name, Role role, boolean colors) {
        Team team = board.getTeam(name);
        if (team != null) {
            team.color(colors ? colorFor(role) : NamedTextColor.WHITE);
        }
    }

    /** Creates missing lobby teams with collision rule NEVER. Idempotent. */
    public void ensureLobbyTeams() {
        Scoreboard board = mainBoard();
        if (board == null) {
            return;
        }
        for (String name : LOBBY_TEAMS) {
            Team team = board.getTeam(name);
            if (team == null) {
                team = board.registerNewTeam(name);
            }
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        applyColors();
    }

    /**
     * Repairs one player's team membership from their current role,
     * leaving every lobby team behind: match-side callers always land
     * back on role teams this way.
     */
    public void sync(Player player) {
        ensureTeams();
        Scoreboard board = mainBoard();
        if (board == null) {
            return;
        }
        strip(board, player.getName());
        teamFor(playerStates.role(player)).ifPresent(name -> {
            Team team = board.getTeam(name);
            if (team != null) {
                team.addEntry(player.getName());
            }
        });
    }

    /**
     * Moves one player onto their role's lobby team, leaving every
     * role team behind. The lobby twin keeps their name color while
     * the NEVER rule drops their collisions.
     */
    public void moveToLobby(Player player) {
        ensureLobbyTeams();
        Scoreboard board = mainBoard();
        if (board == null) {
            return;
        }
        strip(board, player.getName());
        Team team = board.getTeam(lobbyTeamFor(playerStates.role(player)));
        if (team != null) {
            team.addEntry(player.getName());
        }
    }

    /** Drops one entry from every lobby team. */
    public void removeLobbyEntry(String entry) {
        Scoreboard board = mainBoard();
        if (board == null) {
            return;
        }
        removeLobbyEntryOn(board, entry);
    }

    /** Drops one player from every manhunt team, role and lobby alike. */
    public void remove(Player player) {
        Scoreboard board = mainBoard();
        if (board == null) {
            return;
        }
        strip(board, player.getName());
    }

    /** Drops one entry from every manhunt-owned team. */
    private void strip(Scoreboard board, String entry) {
        for (String name : TEAMS) {
            Team team = board.getTeam(name);
            if (team != null) {
                team.removeEntry(entry);
            }
        }
        removeLobbyEntryOn(board, entry);
    }

    /** Drops one entry from every lobby team on the given board. */
    private void removeLobbyEntryOn(Scoreboard board, String entry) {
        for (String name : LOBBY_TEAMS) {
            Team team = board.getTeam(name);
            if (team != null) {
                team.removeEntry(entry);
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
