package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.StatusRosterService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Start-of-match announcements: per-role chat plus titles plus sounds,
 * and the starting roster through the shared status formatter.
 */
public final class MatchAnnounceService {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final SoundService sounds;
    private final PlayerStateStore playerStates;
    private final MatchStore store;
    private final StatusRosterService roster;

    public MatchAnnounceService(JManhuntPlugin plugin, MessageService messages, SoundService sounds,
            PlayerStateStore playerStates, MatchStore store, StatusRosterService roster) {
        this.plugin = plugin;
        this.messages = messages;
        this.sounds = sounds;
        this.playerStates = playerStates;
        this.store = store;
        this.roster = roster;
    }

    /**
     * Tells each participant their own role when a match starts. This runs
     * inside the start flow after the match status is shown, but still
     * before the pre-start window opens, so it always plays before any damage
     * can occur. Non-participants are skipped. Sounds toggle separately:
     * when both chat and title are disabled, nothing plays at all.
     */
    public void announceRoles(int lobbyId, List<Player> players, List<Player> spectators) {
        boolean chat = plugin.overrides()
                .getBoolean(lobbyId, "settings.players.announce-roles.chat.enabled", true);
        boolean title = plugin.overrides()
                .getBoolean(lobbyId, "settings.players.announce-roles.title.enabled", true);
        if (!chat && !title) {
            return;
        }
        boolean soundsEnabled = plugin.overrides()
                .getBoolean(lobbyId, "settings.players.announce-roles.sounds.enabled", true);
        long fadeIn = toMillis(plugin.overrides()
                .getDouble(lobbyId, "settings.players.announce-roles.title.fade-in-seconds", 0.5));
        long stay = toMillis(plugin.overrides()
                .getDouble(lobbyId, "settings.players.announce-roles.title.stay-seconds", 3.0));
        long fadeOut = toMillis(plugin.overrides().getDouble(lobbyId,
                "settings.players.announce-roles.title.fade-out-seconds", 0.5));
        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn), Duration.ofMillis(stay), Duration.ofMillis(fadeOut));
        for (Player player : players) {
            Role playerRole = playerStates.role(player);
            if (!playerRole.isParticipant()) {
                continue;
            }
            Map<String, String> values = Map.of("role", messages.roleName(playerRole));
            if (chat) {
                messages.message(player, "manhunt.role-announce-chat", values);
            }
            if (title) {
                String subtitleKey = playerRole == Role.HUNTER
                        ? "manhunt.role-announce-subtitle-hunter" : "manhunt.role-announce-subtitle-speedrunner";
                player.showTitle(Title.title(
                        messages.component("manhunt.role-announce-title", values),
                        messages.component(subtitleKey), times));
            }
            if (soundsEnabled) {
                sounds.playSound(player,
                        playerRole == Role.HUNTER ? "announce.hunter" : "announce.speedrunner");
            }
        }
        for (Player spectator : spectators) {
            announceSpectator(spectator, chat, title, times, soundsEnabled);
        }
    }

    private void announceSpectator(Player spectator, boolean chat, boolean title, Title.Times times,
            boolean soundsEnabled) {
        if (playerStates.role(spectator) != Role.SPECTATOR) {
            return;
        }
        Map<String, String> values = Map.of("role", messages.roleName(Role.SPECTATOR));
        if (chat) {
            messages.message(spectator, "manhunt.role-announce-chat", values);
        }
        if (title) {
            spectator.showTitle(Title.title(
                    messages.component("manhunt.role-announce-title", values),
                    messages.component("manhunt.role-announce-subtitle-spectator"), times));
        }
        if (soundsEnabled) {
            sounds.playSound(spectator, "announce.spectator");
        }
    }

    private static long toMillis(double seconds) {
        return Math.max(0L, Math.round(seconds * 1000.0));
    }

    /**
     * Shows the starting roster to one match's players through the
     * shared status formatter, so the roll call truncates and joins
     * exactly like status output.
     */
    public void showStatusToInstance(GameInstance instance, List<Player> players) {
        Predicate<UUID> respawning = StatusRosterService.respawning(plugin.respawnListener());
        for (Player recipient : store.onlineAssignedPlayers(instance)) {
            messages.message(recipient, "manhunt.status-header", Map.of("status", "ACTIVE"));
            roster.sendRoleSection(recipient, players, Role.SPEEDRUNNER,
                    "manhunt.speedrunners-header", instance.deadPlayers(), respawning);
            roster.sendRoleSection(recipient, players, Role.HUNTER, "manhunt.hunters-header",
                    instance.deadPlayers(), respawning);
            roster.sendRoleSection(recipient, players, Role.AFK, "manhunt.afk-header",
                    instance.deadPlayers(), respawning);
            roster.sendRoleSection(recipient, players, Role.NONE, "manhunt.none-header",
                    instance.deadPlayers(), respawning);
            roster.sendSpectatorLine(recipient, players);
        }
    }
}
