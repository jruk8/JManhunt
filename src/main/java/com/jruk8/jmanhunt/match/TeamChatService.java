package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.config.ServerSettings;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.message.ChatMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Team chat: prefixed lines go to same-team match members instead of
 * the public broadcast. Prefix matching and recipient filtering are
 * pure so they stay unit testable; delivery runs on the main thread.
 */
public final class TeamChatService {
    /** States, fakes, lobbies, and team chat settings. */
    public record TeamReads(PlayerStateStore playerStates, FakeSpectatorService fakes,
            LobbyService lobbies, ServerSettings.TeamChat teamChat) {
    }

    /** Message bus, chat texts, and sounds. */
    public record TeamTexts(MessageService messages, ChatMessages chat, SoundService sounds) {
    }

    private final GameManager game;
    private final TeamReads reads;
    private final TeamTexts texts;

    public TeamChatService(GameManager game, TeamReads reads, TeamTexts texts) {
        this.game = game;
        this.reads = reads;
        this.texts = texts;
    }

    /** Membership row for the pure recipient filter. */
    public record Member(Role role, boolean fakeSpectator) {
    }

    /** Lobby queuer row for the pure lobby recipient filter. */
    public record LobbyMember(int lobbyId, Role role, boolean fakeSpectator, boolean inMatch) {
    }

    /**
     * Message body after the first matching prefix, or empty when no
     * alias hits. Prefixes match at the message start (case-blind,
     * leading whitespace tolerated) and must end at a word boundary:
     * end of text or whitespace. The body is stripped; a present but
     * empty body is a usage error, not a team message.
     */
    public static Optional<String> parsePrefix(String text, List<String> prefixes) {
        String stripped = text.stripLeading();
        for (String prefix : prefixes) {
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            String alias = prefix.strip();
            if (alias.length() > stripped.length()
                    || !stripped.regionMatches(true, 0, alias, 0, alias.length())) {
                continue;
            }
            if (alias.length() < stripped.length()
                    && !Character.isWhitespace(stripped.charAt(alias.length()))) {
                continue;
            }
            return Optional.of(stripped.substring(alias.length()).strip());
        }
        return Optional.empty();
    }

    /**
     * True for members who receive team chat: the sender's role, plus
     * fake spectators when spectators-see is on. Lobby idlers (wrong
     * role, not in fake-spectator mode) never match.
     */
    static boolean isRecipient(Role role, boolean fakeSpectator,
            Role senderRole, boolean spectatorsSee) {
        return role == senderRole || (spectatorsSee && fakeSpectator);
    }

    /**
     * Pure recipient filter over one instance's online members. The
     * caller passes only members of the sender's match.
     */
    static List<Member> filterRecipients(List<Member> members, Role senderRole,
            boolean spectatorsSee) {
        List<Member> recipients = new ArrayList<>();
        for (Member member : members) {
            if (isRecipient(member.role(), member.fakeSpectator(), senderRole, spectatorsSee)) {
                recipients.add(member);
            }
        }
        return recipients;
    }

    public boolean enabled() {
        return reads.teamChat().isEnabled();
    }

    public List<String> prefixes() {
        List<String> prefixes = reads.teamChat().getPrefixes();
        return prefixes.isEmpty() ? List.of("@team", "@t") : prefixes;
    }

    public boolean spectatorsSee() {
        return reads.teamChat().isSpectatorsSee();
    }

    /**
     * True for queuers who receive lobby team chat: same lobby, no
     * running match, the sender's role, plus fake spectators when
     * spectators-see is on. Match players never match.
     */
    static boolean isLobbyRecipient(LobbyMember member, int senderLobbyId,
            Role senderRole, boolean spectatorsSee) {
        return !member.inMatch()
                && member.lobbyId() == senderLobbyId
                && (member.role() == senderRole || (spectatorsSee && member.fakeSpectator()));
    }

    /**
     * Pure recipient filter over lobby queuers. Match members are
     * excluded by the predicate, so lobby lines never leak into a
     * running match, including hold and sublobby setups.
     */
    static List<LobbyMember> filterLobbyRecipients(List<LobbyMember> members, int senderLobbyId,
            Role senderRole, boolean spectatorsSee) {
        List<LobbyMember> recipients = new ArrayList<>();
        for (LobbyMember member : members) {
            if (isLobbyRecipient(member, senderLobbyId, senderRole, spectatorsSee)) {
                recipients.add(member);
            }
        }
        return recipients;
    }

    /** Eligible senders: participants inside a match, or queuers inside a lobby. */
    public boolean isEligible(Player sender) {
        if (!reads.playerStates().role(sender).isParticipant()) {
            return false;
        }
        return game.instanceOf(sender.getUniqueId()).isPresent()
                || reads.lobbies().lobbyOf(sender.getUniqueId()).isPresent();
    }

    /** Same-team online members plus watching fake spectators. */
    public List<Player> recipients(Player sender) {
        Optional<GameInstance> match = game.instanceOf(sender.getUniqueId());
        if (match.isEmpty()) {
            return lobbyRecipients(sender);
        }
        long matchId = match.get().matchId();
        Role senderRole = reads.playerStates().role(sender);
        boolean spectatorsSee = spectatorsSee();
        List<Player> recipients = new ArrayList<>();
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            Optional<GameInstance> other = game.instanceOf(candidate.getUniqueId());
            if (other.isEmpty() || other.get().matchId() != matchId) {
                continue;
            }
            if (isRecipient(reads.playerStates().role(candidate), reads.fakes().isFakeSpectator(candidate),
                    senderRole, spectatorsSee)) {
                recipients.add(candidate);
            }
        }
        return recipients;
    }

    /** Same-lobby, same-role queuers outside any match, plus watching fake spectators. */
    private List<Player> lobbyRecipients(Player sender) {
        Optional<Lobby> lobby = reads.lobbies().lobbyOf(sender.getUniqueId());
        if (lobby.isEmpty()) {
            return List.of();
        }
        int lobbyId = lobby.get().id();
        Role senderRole = reads.playerStates().role(sender);
        boolean spectatorsSee = spectatorsSee();
        List<Player> recipients = new ArrayList<>();
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            Optional<Lobby> other = reads.lobbies().lobbyOf(candidate.getUniqueId());
            if (other.isEmpty() || other.get().id() != lobbyId) {
                continue;
            }
            LobbyMember member = new LobbyMember(lobbyId, reads.playerStates().role(candidate),
                    reads.fakes().isFakeSpectator(candidate),
                    game.instanceOf(candidate.getUniqueId()).isPresent());
            if (isLobbyRecipient(member, lobbyId, senderRole, spectatorsSee)) {
                recipients.add(candidate);
            }
        }
        return recipients;
    }

    /** Renders once, then sends to recipients plus the console with a bump sound. */
    public void deliver(Player sender, String body) {
        Role role = reads.playerStates().role(sender);
        Component rendered = texts.messages().componentRaw(texts.chat().getTeamChatFormat(), Map.of(
                "role", texts.messages().roleName(role),
                "rolecolor", texts.messages().roleColor(role),
                "player", sender.getName(),
                "message", MiniMessage.miniMessage().escapeTags(body)));
        for (Player recipient : recipients(sender)) {
            recipient.sendMessage(rendered);
            texts.sounds().playSound(recipient, "chat.team-chat");
        }
        Bukkit.getConsoleSender().sendMessage(rendered);
    }

    /** Usage hint for a bare prefix. The raw line stays cancelled. */
    public void usage(Player sender) {
        texts.messages().messageRaw(sender, texts.chat().getTeamChatUsage());
        texts.sounds().playAngrySound(sender);
    }
}
