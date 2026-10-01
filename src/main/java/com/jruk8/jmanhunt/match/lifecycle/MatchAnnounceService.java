package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.lobby.config.PlayersSettingsFacade;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.StatusRosterService;
import com.jruk8.jmanhunt.match.listeners.PlayerRespawnListener;
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
    /** Announcement chat, sounds, and text. */
    public record AnnounceTexts(MessageService messages, SoundService sounds,
            ManhuntMessages manhunt) {
    }

    /** Roster state plus respawn reads. */
    public record AnnounceState(PlayerStateStore playerStates, MatchStore store,
            StatusRosterService roster, PlayerRespawnListener respawnListener) {
    }

    private final PlayersSettingsFacade playerSettings;
    private final AnnounceTexts texts;
    private final AnnounceState state;

    public MatchAnnounceService(PlayersSettingsFacade playerSettings, AnnounceTexts texts,
            AnnounceState state) {
        this.playerSettings = playerSettings;
        this.texts = texts;
        this.state = state;
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
            Role playerRole = state.playerStates().role(player);
            if (!playerRole.isParticipant()) {
                continue;
            }
            Map<String, String> values = Map.of("role", texts.messages().roleName(playerRole));
            if (chat) {
                texts.messages().messageRaw(player, texts.manhunt().getRoleAnnounceChat(), values);
            }
            if (title) {
                String subtitle = playerRole == Role.HUNTER
                        ? texts.manhunt().getRoleAnnounceSubtitleHunter()
                        : texts.manhunt().getRoleAnnounceSubtitleSpeedrunner();
                player.showTitle(Title.title(
                        texts.messages().componentRaw(texts.manhunt().getRoleAnnounceTitle(), values),
                        texts.messages().componentRaw(subtitle), times));
            }
            if (soundsEnabled) {
                texts.sounds().playSound(player,
                        playerRole == Role.HUNTER ? "announce.hunter" : "announce.speedrunner");
            }
        }
        for (Player spectator : spectators) {
            announceSpectator(spectator, chat, title, times, soundsEnabled);
        }
    }

    private void announceSpectator(Player spectator, boolean chat, boolean title, Title.Times times,
            boolean soundsEnabled) {
        if (state.playerStates().role(spectator) != Role.SPECTATOR) {
            return;
        }
        Map<String, String> values = Map.of("role", texts.messages().roleName(Role.SPECTATOR));
        if (chat) {
            texts.messages().messageRaw(spectator, texts.manhunt().getRoleAnnounceChat(), values);
        }
        if (title) {
            spectator.showTitle(Title.title(
                    texts.messages().componentRaw(texts.manhunt().getRoleAnnounceTitle(), values),
                    texts.messages().componentRaw(texts.manhunt().getRoleAnnounceSubtitleSpectator()), times));
        }
        if (soundsEnabled) {
            texts.sounds().playSound(spectator, "announce.spectator");
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
        Predicate<UUID> respawning = StatusRosterService.respawning(state.respawnListener());
        for (Player recipient : state.store().onlineAssignedPlayers(instance)) {
            texts.messages().messageRaw(recipient, texts.manhunt().getStatusHeader(), Map.of("status", "ACTIVE"));
            state.roster().sendRoleSection(recipient, players, Role.SPEEDRUNNER,
                    texts.manhunt().getSpeedrunnersHeader(), instance.deadPlayers(), respawning);
            state.roster().sendRoleSection(recipient, players, Role.HUNTER, texts.manhunt().getHuntersHeader(),
                    instance.deadPlayers(), respawning);
            state.roster().sendRoleSection(recipient, players, Role.AFK, texts.manhunt().getAfkHeader(),
                    instance.deadPlayers(), respawning);
            state.roster().sendRoleSection(recipient, players, Role.NONE, texts.manhunt().getNoneHeader(),
                    instance.deadPlayers(), respawning);
            state.roster().sendSpectatorLine(recipient, players);
        }
    }
}
