package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.jruk8.jmanhunt.match.GameInstance;

/** Match and lobby message fan-out. */
public final class MatchMessaging {
    private final MessageService messages;
    private final SoundService sounds;
    private final ConfigService configService;
    private final MatchStore store;
    private final LobbyService lobbies;

    public MatchMessaging(MessageService messages, SoundService sounds, ConfigService configService,
            MatchStore store, LobbyService lobbies) {
        this.messages = messages;
        this.sounds = sounds;
        this.configService = configService;
        this.store = store;
        this.lobbies = lobbies;
    }

    /** Sends a message to a match plus the console, never other matches. */
    public void sendToInstance(GameInstance instance, String key, Map<String, String> values) {
        if (messages.isDisabled(key)) {
            return;
        }
        Component rendered = messages.component(key, values);
        for (Player recipient : store.onlineAssignedPlayers(instance)) {
            recipient.sendMessage(rendered);
        }
        Bukkit.getConsoleSender().sendMessage(rendered);
    }

    /** Plays a match sound for a match's online players. */
    public void playInstanceSound(GameInstance instance, String key) {
        for (Player recipient : store.onlineAssignedPlayers(instance)) {
            sounds.playSound(recipient, key);
        }
    }

    /** Plays the neutral click for a match's online players. */
    public void playInstanceNeutral(GameInstance instance) {
        for (Player recipient : store.onlineAssignedPlayers(instance)) {
            sounds.playNeutralSound(recipient);
        }
    }

    /** Sends a pre-rendered message to a match plus the console, never other matches. */
    public void sendToInstanceComponent(GameInstance instance, Component rendered) {
        for (Player recipient : store.onlineAssignedPlayers(instance)) {
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
        if (!configService.getBoolean("settings.announce-role-changes", false)) {
            return;
        }
        if (from.isParticipant() == to.isParticipant()) {
            return;
        }
        Role active = to.isParticipant() ? to : from;
        String key = to.isParticipant() ? "manhunt.role-is-now" : "manhunt.role-no-longer";
        Map<String, String> values = Map.of("player", player.getName(),
                "active-role", messages.roleName(active));
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
            messages.message(recipient, key, values);
        }
    }

    /** Online lobby members for scoped autostart messages and sounds. */
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

    public void sendToLobby(int lobbyId, String key, Map<String, String> values) {
        messages.sendTo(lobbyRecipients(lobbyId), key, values);
        // Console keeps seeing every lobby, as with the old broadcasts.
        if (!messages.isDisabled(key)) {
            Bukkit.getConsoleSender().sendMessage(messages.component(key, values));
        }
    }

    public void playLobbySound(int lobbyId, String key) {
        for (Player recipient : lobbyRecipients(lobbyId)) {
            sounds.playSound(recipient, key);
        }
    }
}
