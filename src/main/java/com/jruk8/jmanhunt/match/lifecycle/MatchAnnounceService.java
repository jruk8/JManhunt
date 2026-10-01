package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.config.PlayersSettingsFacade;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.StatusRosterService;
import com.jruk8.jmanhunt.message.ManhuntMessages;
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
    private final PlayersSettingsFacade playerSettings;
    private final MessageService messages;
    private final SoundService sounds;
    private final PlayerStateStore playerStates;
    private final MatchStore store;
    private final StatusRosterService roster;
    private final ManhuntMessages manhunt;

    public MatchAnnounceService(JManhuntPlugin plugin, PlayersSettingsFacade playerSettings,
            MessageService messages, SoundService sounds,
            PlayerStateStore playerStates, MatchStore store, StatusRosterService roster,
            ManhuntMessages manhunt) {
        this.plugin = plugin;
        this.playerSettings = playerSettings;
        this.messages = messages;
        this.sounds = sounds;
        this.playerStates = playerStates;
        this.store = store;
        this.roster = roster;
        this.manhunt = manhunt;
    }

    /**
     * Tells each participant their own role when a match starts. This runs
     * inside the start flow after the match status is shown, but still
     * before the pre-start window opens, so it always plays before any damage
     * can occur. Non-participants are skipped. Sounds toggle separately:
     * when both chat and title are disabled, nothing plays at all.
     */
    public void announceRoles(int lobbyId, List<Player> players, List<Player> spectators) {
        boolean chat = playerSettings.announceRolesChat(lobbyId);
        boolean title = playerSettings.announceRolesTitle(lobbyId);
        if (!chat && !title) {
            return;
        }
        boolean soundsEnabled = playerSettings.announceRolesSounds(lobbyId);
        long fadeIn = toMillis(playerSettings.announceTitleFadeInSeconds(lobbyId));
        long stay = toMillis(playerSettings.announceTitleStaySeconds(lobbyId));
        long fadeOut = toMillis(playerSettings.announceTitleFadeOutSeconds(lobbyId));
        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn), Duration.ofMillis(stay), Duration.ofMillis(fadeOut));
        for (Player player : players) {
            Role playerRole = playerStates.role(player);
            if (!playerRole.isParticipant()) {
                continue;
            }
            Map<String, String> values = Map.of("role", messages.roleName(playerRole));
            if (chat) {
                messages.messageRaw(player, manhunt.getRoleAnnounceChat(), values);
            }
            if (title) {
                String subtitle = playerRole == Role.HUNTER
                        ? manhunt.getRoleAnnounceSubtitleHunter()
                        : manhunt.getRoleAnnounceSubtitleSpeedrunner();
                player.showTitle(Title.title(
                        messages.componentRaw(manhunt.getRoleAnnounceTitle(), values),
                        messages.componentRaw(subtitle), times));
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
            messages.messageRaw(spectator, manhunt.getRoleAnnounceChat(), values);
        }
        if (title) {
            spectator.showTitle(Title.title(
                    messages.componentRaw(manhunt.getRoleAnnounceTitle(), values),
                    messages.componentRaw(manhunt.getRoleAnnounceSubtitleSpectator()), times));
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
            messages.messageRaw(recipient, manhunt.getStatusHeader(), Map.of("status", "ACTIVE"));
            roster.sendRoleSection(recipient, players, Role.SPEEDRUNNER,
                    manhunt.getSpeedrunnersHeader(), instance.deadPlayers(), respawning);
            roster.sendRoleSection(recipient, players, Role.HUNTER, manhunt.getHuntersHeader(),
                    instance.deadPlayers(), respawning);
            roster.sendRoleSection(recipient, players, Role.AFK, manhunt.getAfkHeader(),
                    instance.deadPlayers(), respawning);
            roster.sendRoleSection(recipient, players, Role.NONE, manhunt.getNoneHeader(),
                    instance.deadPlayers(), respawning);
            roster.sendSpectatorLine(recipient, players);
        }
    }
}
