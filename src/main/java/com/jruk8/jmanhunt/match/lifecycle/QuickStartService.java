package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.match.autostart.AutostartService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

/**
 * Quick starts: team assignment over the convertible pool, then the
 * normal start flow through the starter callback.
 */
public final class QuickStartService {
    /** Lobby, roles, and team sync. */
    public record QuickRoster(LobbyService lobbies, PlayerStateStore playerStates,
            RoleTeamService roleTeams) {
    }

    private final QuickRoster roster;
    private final MatchStore store;
    private final AutostartService autostart;
    private final BiFunction<Integer, Location, Boolean> starter;

    public QuickStartService(QuickRoster roster, MatchStore store, AutostartService autostart,
            BiFunction<Integer, Location, Boolean> starter) {
        this.roster = roster;
        this.store = store;
        this.autostart = autostart;
        this.starter = starter;
    }

    /**
     * True when a live lobby match blocks a new start: any live match
     * except under sublobby policies with the world engine on, where the
     * new match becomes the next child sublobby.
     */
    public boolean blocksStart(int lobbyId) {
        return store.instanceForLobby(lobbyId).isPresent()
                && !roster.lobbies().midMatchPolicy().allowsConcurrentStart(roster.lobbies().multiLobbyAllowed());
    }

    /**
     * Quick-starts a match by assigning eligible players of one lobby to
     * teams and immediately starting the game, bypassing the autostart
     * system. Queue caps never apply and NONE players always join the
     * convertible pool.
     *
     * @param speedrunnerPercent the percentage of convertible players that
     *                           should become speedrunners (0-100), or -1 for
     *                           default (keep teams, converting only what is
     *                           missing to start)
     * @param lobbyId the lobby whose members form the convertible pool
     * @return whether the match started
     */
    public QuickStartOutcome quickStart(int speedrunnerPercent, int lobbyId) {
        return quickStart(speedrunnerPercent, lobbyId, null);
    }

    /**
     * Same, but the surround origin gathers participants when the world
     * engine is off: a player executor's location, or null for a random
     * wilderness point (console).
     */
    public QuickStartOutcome quickStart(int speedrunnerPercent, int lobbyId, Location surroundOrigin) {
        if (blocksStart(lobbyId)) {
            return new QuickStartOutcome(false);
        }
        Optional<Lobby> lobby = roster.lobbies().get(lobbyId);
        if (lobby.isEmpty()) {
            return new QuickStartOutcome(false);
        }
        Lobby resolved = lobby.get();
        // Every online non-AFK, non-spectator lobby member is convertible:
        // existing hunters and speedrunners keep their roles unless
        // conversion is needed, and NONEs always join the pool.
        List<Player> pool = Bukkit.getOnlinePlayers().stream()
                .filter(p -> resolved.contains(p.getUniqueId())
                        && roster.playerStates().role(p) != Role.AFK && roster.playerStates().role(p) != Role.SPECTATOR
                        && !store.isInLiveInstance(p.getUniqueId()))
                .map(p -> (Player) p)
                .toList();
        // Cancel any autostart countdown silently
        autostart.cancelAllAutostartCountdowns(false);
        // A match needs at least one hunter and one speedrunner, so with
        // fewer than two convertible players there is nothing to assign.
        if (pool.size() < 2) {
            return new QuickStartOutcome(starter.apply(lobbyId, surroundOrigin));
        }
        if (speedrunnerPercent < 0) {
            ensureMinimumTeams(pool);
        } else {
            assignQuickStartRoles(pool, speedrunnerPercent);
        }
        // Validate after assignment: start() requires at least one hunter and
        // one speedrunner, so e.g. two players online with one AFK will fail.
        return new QuickStartOutcome(starter.apply(lobbyId, surroundOrigin));
    }

    /** Assigns quickstart roles by percentage over the convertible pool. */
    private void assignQuickStartRoles(List<Player> pool, int speedrunnerPercent) {
        // Percentage-based assignment over the whole convertible pool:
        // random selection, players become speedrunners until the count
        // is reached and hunters after that.
        int speedrunnerCount = quickStartSpeedrunnerCount(pool.size(), speedrunnerPercent);
        List<Player> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        for (int index = 0; index < shuffled.size(); index++) {
            Role want = index < speedrunnerCount ? Role.SPEEDRUNNER : Role.HUNTER;
            roster.playerStates().setRole(shuffled.get(index), want);
            roster.roleTeams().sync(shuffled.get(index));
        }
    }

    /**
     * Computes how many players of a convertible pool become speedrunners for
     * a quick-start percentage. Fractional results are rounded to the nearest
     * whole player, with always at least one speedrunner.
     *
     * @param poolSize the number of convertible players (must be positive)
     * @param percent the requested speedrunner percentage (0-100)
     * @return the number of speedrunners to assign
     */
    public static int quickStartSpeedrunnerCount(int poolSize, int percent) {
        return Math.max(1, (int) Math.round(poolSize * percent / 100.0));
    }

    /**
     * Guarantees at least one hunter and one speedrunner by converting random
     * pool members only where a team is missing, so an all-hunter or an
     * all-speedrunner lobby still starts. NONE players become hunters and
     * everyone else keeps their current role.
     */
    private void ensureMinimumTeams(List<Player> pool) {
        boolean hasHunter = pool.stream().anyMatch(p -> roster.playerStates().role(p) == Role.HUNTER);
        boolean hasSpeedrunner = pool.stream().anyMatch(p -> roster.playerStates().role(p) == Role.SPEEDRUNNER);
        Player converted = null;
        if (!hasSpeedrunner) {
            converted = pickConvertible(pool, null);
            if (converted != null) {
                roster.playerStates().setRole(converted, Role.SPEEDRUNNER);
                roster.roleTeams().sync(converted);
            }
        }
        if (!hasHunter) {
            Player hunter = pickConvertible(pool, converted);
            if (hunter != null) {
                roster.playerStates().setRole(hunter, Role.HUNTER);
                roster.roleTeams().sync(hunter);
            }
        }
        for (Player player : pool) {
            if (roster.playerStates().role(player) != Role.NONE) {
                continue;
            }
            roster.playerStates().setRole(player, Role.HUNTER);
            roster.roleTeams().sync(player);
        }
    }

    /**
     * Picks a random convertible pool member, preferring NONE players so
     * queued roles are only disturbed when no unassigned player is left.
     *
     * @return the picked player, or null if the pool holds only the exclusion
     */
    private Player pickConvertible(List<Player> pool, Player exclude) {
        List<Player> candidates = pool.stream().filter(p -> !p.equals(exclude)).toList();
        List<Player> unassigned = candidates.stream()
                .filter(p -> roster.playerStates().role(p) == Role.NONE).toList();
        List<Player> preferred = unassigned.isEmpty() ? candidates : unassigned;
        return preferred.isEmpty()
                ? null : preferred.get(ThreadLocalRandom.current().nextInt(preferred.size()));
    }
}
