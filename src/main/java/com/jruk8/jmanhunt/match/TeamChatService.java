package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.config.ServerSettings;
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
    private final GameManager game;
    private final PlayerStateStore playerStates;
    private final FakeSpectatorService fakes;
    private final ServerSettings.TeamChat teamChat;
    private final MessageService messages;
    private final ChatMessages chat;
    private final SoundService sounds;

    public TeamChatService(GameManager game, PlayerStateStore playerStates,
            FakeSpectatorService fakes, ServerSettings.TeamChat teamChat,
            MessageService messages, ChatMessages chat, SoundService sounds) {
        this.game = game;
        this.playerStates = playerStates;
        this.fakes = fakes;
        this.teamChat = teamChat;
        this.messages = messages;
        this.chat = chat;
        this.sounds = sounds;
    }

    /** Membership row for the pure recipient filter. */
    public record Member(Role role, boolean fakeSpectator) {
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
        return teamChat.isEnabled();
    }

    public List<String> prefixes() {
        List<String> prefixes = teamChat.getPrefixes();
        return prefixes.isEmpty() ? List.of("@team", "@t") : prefixes;
    }

    public boolean spectatorsSee() {
        return teamChat.isSpectatorsSee();
    }

    /** Eligible senders: participants inside a match. Nobody else. */
    public boolean isEligible(Player sender) {
        return playerStates.role(sender).isParticipant()
                && game.instanceOf(sender.getUniqueId()).isPresent();
    }

    /** Same-team online members plus watching fake spectators. */
    public List<Player> recipients(Player sender) {
        Optional<GameInstance> match = game.instanceOf(sender.getUniqueId());
        if (match.isEmpty()) {
            return List.of();
        }
        long matchId = match.get().matchId();
        Role senderRole = playerStates.role(sender);
        boolean spectatorsSee = spectatorsSee();
        List<Player> recipients = new ArrayList<>();
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            Optional<GameInstance> other = game.instanceOf(candidate.getUniqueId());
            if (other.isEmpty() || other.get().matchId() != matchId) {
                continue;
            }
            if (isRecipient(playerStates.role(candidate), fakes.isFakeSpectator(candidate),
                    senderRole, spectatorsSee)) {
                recipients.add(candidate);
            }
        }
        return recipients;
    }

    /** Renders once, then sends to recipients plus the console with a bump sound. */
    public void deliver(Player sender, String body) {
        Role role = playerStates.role(sender);
        Component rendered = messages.componentRaw(chat.getTeamChatFormat(), Map.of(
                "role", messages.roleName(role),
                "rolecolor", messages.roleColor(role),
                "player", sender.getName(),
                "message", MiniMessage.miniMessage().escapeTags(body)));
        for (Player recipient : recipients(sender)) {
            recipient.sendMessage(rendered);
            sounds.playSound(recipient, "chat.team-chat");
        }
        Bukkit.getConsoleSender().sendMessage(rendered);
    }

    /** Usage hint for a bare prefix. The raw line stays cancelled. */
    public void usage(Player sender) {
        messages.messageRaw(sender, chat.getTeamChatUsage());
        sounds.playAngrySound(sender);
    }
}
