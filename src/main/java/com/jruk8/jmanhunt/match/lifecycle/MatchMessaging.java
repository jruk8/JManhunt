package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.config.ServerSettings;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.jruk8.jmanhunt.match.GameInstance;

/** Match and lobby message fan-out. */
public final class MatchMessaging {
    /** Message bus, manhunt texts, and sounds. */
    public record MessagingTexts(MessageService messages, ManhuntMessages manhunt,
            SoundService sounds) {
    }

    private final MessagingTexts texts;
    private final ServerSettings server;
    private final MatchStore store;
    private final LobbyService lobbies;

    public MatchMessaging(MessagingTexts texts, ServerSettings server, MatchStore store,
            LobbyService lobbies) {
        this.texts = texts;
        this.server = server;
        this.store = store;
        this.lobbies = lobbies;
    }

    /** Sends a message to a match plus the console, never other matches. */
    public void sendToInstance(GameInstance instance, String template, Map<String, String> values) {
        sendToInstanceExcept(instance, template, values, Set.of());
    }

    /** Sends to a match except the given ids, plus the console. */
    public void sendToInstanceExcept(GameInstance instance, String template,
            Map<String, String> values, Set<UUID> excluded) {
        if (texts.messages().blank(template)) {
            return;
        }
        Component rendered = texts.messages().componentRaw(template, values);
        for (Player recipient : store.onlineMatchAudience(instance)) {
            if (!excluded.contains(recipient.getUniqueId())) {
                recipient.sendMessage(rendered);
            }
        }
        Bukkit.getConsoleSender().sendMessage(rendered);
    }

    /** Plays a match sound for a match's online players. */
    public void playInstanceSound(GameInstance instance, String key) {
        for (Player recipient : store.onlineMatchAudience(instance)) {
            texts.sounds().playSound(recipient, key);
        }
    }

    /** Plays the neutral click for a match's online players. */
    public void playInstanceNeutral(GameInstance instance) {
        for (Player recipient : store.onlineMatchAudience(instance)) {
            texts.sounds().playNeutralSound(recipient);
        }
    }

    /** Sends a pre-rendered message to a match plus the console, never other matches. */
    public void sendToInstanceComponent(GameInstance instance, Component rendered) {
        for (Player recipient : store.onlineMatchAudience(instance)) {
            recipient.sendMessage(rendered);
        }
        Bukkit.getConsoleSender().sendMessage(rendered);
    }

    /**
     * Announces a passive&lt;-&gt;active role change to the player's lobby
     * mates, excluding the player and anyone in a live match. Same-class
     * changes stay silent. Only the active side of the change is named.
     */
    public void announceRoleChange(Player player, Role from, Role to) {
        if (!server.isAnnounceRoleChanges()) {
            return;
        }
        if (from.isParticipant() == to.isParticipant()) {
            return;
        }
        Role active = to.isParticipant() ? to : from;
        String template = to.isParticipant() ? texts.manhunt().getRoleIsNow() : texts.manhunt().getRoleNoLonger();
        Map<String, String> values = Map.of("player", player.getName(),
                "active-role", texts.messages().roleName(active));
        Optional<Lobby> lobby = lobbies.lobbyOf(player.getUniqueId());
        if (lobby.isEmpty()) {
            return;
        }
        for (Player recipient : lobbyRecipients(lobby.get().id())) {
            if (recipient.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (store.instanceOf(recipient.getUniqueId()).isPresent()) {
                continue;
            }
            texts.messages().messageRaw(recipient, template, values);
        }
    }

    /** Online lobby members for scoped autostart messages and texts.sounds(). */
    public List<Player> lobbyRecipients(int lobbyId) {
        Optional<Lobby> lobby = lobbies.get(lobbyId);
        if (lobby.isEmpty()) {
            return List.of();
        }
        Lobby resolved = lobby.get();
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> resolved.contains(player.getUniqueId()))
                .map(player -> (Player) player).toList();
    }

    public void sendToLobby(int lobbyId, String template, Map<String, String> values) {
        texts.messages().sendToRaw(lobbyRecipients(lobbyId), template, values);
        // Console keeps seeing every lobby, as with the old broadcasts.
        if (!texts.messages().blank(template)) {
            Bukkit.getConsoleSender().sendMessage(texts.messages().componentRaw(template, values));
        }
    }

    public void playLobbySound(int lobbyId, String key) {
        for (Player recipient : lobbyRecipients(lobbyId)) {
            texts.sounds().playSound(recipient, key);
        }
    }
}
