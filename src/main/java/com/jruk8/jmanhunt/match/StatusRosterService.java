package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.match.listeners.PlayerRespawnListener;
import com.jruk8.jmanhunt.message.ListFormatter;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Shared /mh status roster rendering for the status command and the
 * match-start roll call: one Oxford-joined, truncated alive line per
 * role plus a trailing dark-gray line for the role's permanent
 * deaths. Awaiting-revive players carry an inline skull on the alive
 * line. Limits, words, separators, and templates all read from
 * messages.yml with the documented defaults.
 */
public final class StatusRosterService {
    private final MessageService messages;
    private final PlayerStateStore playerStates;

    public StatusRosterService(MessageService messages, PlayerStateStore playerStates) {
        this.messages = messages;
        this.playerStates = playerStates;
    }

    /** Null-safe pending-revive predicate for roster skulls. */
    public static Predicate<UUID> respawning(PlayerRespawnListener respawns) {
        return respawns == null ? id -> false : respawns::hasPendingRespawn;
    }

    /**
     * Lobby queue line detail: both halves when the lobby holds queued
     * and match members, else whichever half is populated. Pure.
     */
    public static String lobbyDetail(long inLobby, int inMatch) {
        if (inLobby > 0 && inMatch > 0) {
            return inLobby + " in lobby, " + inMatch + " in match";
        }
        if (inMatch > 0) {
            return inMatch + " in match";
        }
        return inLobby + " in lobby";
    }

    /**
     * Sends one role block: header, the truncated alive line, and the
     * role's dead line newest-first. Silent when the role holds nobody
     * and recorded no deaths. The dead list usually comes from the
     * match; lobbies pass an empty list.
     */
    public void sendRoleSection(CommandSender recipient, List<Player> players, Role role,
            String headerKey, List<GameInstance.DeadPlayer> dead, Predicate<UUID> respawning) {
        List<String> names = players.stream()
                .filter(player -> playerStates.role(player) == role)
                .sorted(Comparator.comparing(Player::getName))
                .map(player -> displayName(player, respawning))
                .toList();
        List<String> ghosts = new ArrayList<>();
        for (GameInstance.DeadPlayer entry : dead) {
            if (entry.formerRole() == role) {
                ghosts.add(skull() + entry.name());
            }
        }
        Collections.reverse(ghosts);
        if (names.isEmpty() && ghosts.isEmpty()) {
            return;
        }
        messages.message(recipient, headerKey, Map.of());
        if (!names.isEmpty()) {
            messages.message(recipient, "manhunt.status-player",
                    Map.of("player", aliveLine(names, role)));
        }
        if (!ghosts.isEmpty()) {
            messages.message(recipient, "manhunt.status-dead-line",
                    Map.of("dead_players", deadLine(ghosts)));
        }
    }

    /**
     * Sends the spectator roll call, truncated like every role line.
     * Silent when nobody watches.
     */
    public void sendSpectatorLine(CommandSender recipient, List<Player> players) {
        List<String> names = players.stream()
                .filter(player -> playerStates.role(player) == Role.SPECTATOR)
                .map(Player::getName)
                .sorted()
                .toList();
        if (names.isEmpty()) {
            return;
        }
        messages.message(recipient, "manhunt.spectators-line",
                Map.of("value", aliveLine(names, Role.SPECTATOR)));
    }

    private String displayName(Player player, Predicate<UUID> respawning) {
        if (respawning.test(player.getUniqueId())) {
            return skull() + player.getName();
        }
        return player.getName();
    }

    private String aliveLine(List<String> names, Role role) {
        return ListFormatter.joinOxfordTruncated(names, limitFor(role), comma(), and(), more(),
                more());
    }

    private String deadLine(List<String> ghosts) {
        return ListFormatter.joinOxfordTruncated(ghosts, limit("manhunt.status-limit-dead", 3),
                comma(), and(), more(), deadWord());
    }

    private int limitFor(Role role) {
        return switch (role) {
            case SPEEDRUNNER -> limit("manhunt.status-limit-speedrunners", 5);
            case HUNTER -> limit("manhunt.status-limit-hunters", 10);
            case AFK -> limit("manhunt.status-limit-afk", 2);
            case NONE -> limit("manhunt.status-limit-none", 20);
            case SPECTATOR -> limit("manhunt.status-limit-spectators", 5);
        };
    }

    private int limit(String key, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(messages.string(key, String.valueOf(fallback)).trim()));
        } catch (NumberFormatException bad) {
            return fallback;
        }
    }

    private String comma() {
        return messages.string("manhunt.status-comma", "<gray>,</gray>");
    }

    private String and() {
        return messages.string("manhunt.status-and", "<gray>and</gray>");
    }

    private String more() {
        return messages.string("manhunt.status-more", "more");
    }

    private String deadWord() {
        return messages.string("manhunt.status-dead-word", "dead");
    }

    private String skull() {
        return messages.string("manhunt.status-skull", "💀");
    }
}
