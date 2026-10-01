package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared command environment: message sends, MiniMessage rendering, the
 * neutral click sound, run-twice confirmations, and completion option
 * providers. Units take this plus their message subconfig instead of the
 * whole plugin or messaging services.
 */
public final class CommandSupport {
    private final MessageService messages;
    private final SoundService sounds;
    private final PendingConfirmations confirms;

    public CommandSupport(MessageService messages, SoundService sounds,
            PendingConfirmations confirms) {
        this.messages = messages;
        this.sounds = sounds;
        this.confirms = confirms;
    }

    /** Sends a raw template with no placeholders. Always true (handled). */
    public boolean message(CommandSender sender, String raw) {
        messages.messageRaw(sender, raw, Map.of());
        return true;
    }

    /** Sends a raw template with placeholders. */
    public void message(CommandSender sender, String raw, Map<String, String> values) {
        messages.messageRaw(sender, raw, values);
    }

    /** Plays the neutral click for players; console hears nothing. */
    public void neutralSound(CommandSender sender) {
        if (sender instanceof Player player) {
            sounds.playNeutralSound(player);
        }
    }

    public Component renderLiteral(String raw, Map<String, String> values) {
        return messages.renderLiteral(raw, values);
    }

    public Component miniMessage(String raw) {
        return messages.miniMessage(raw);
    }

    public Component componentRaw(String raw, Map<String, String> values) {
        return messages.componentRaw(raw, values);
    }

    public String prefix() {
        return messages.prefix();
    }

    public String roleName(Role role) {
        return messages.roleName(role);
    }

    public PendingConfirmations confirms() {
        return confirms;
    }

    /** Case-insensitive prefix filter for completion options. */
    public static List<String> partial(String value, List<String> options) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    /** Lobby ids for completion, sorted, with 0 fallback when empty. */
    public static List<String> lobbyIdOptions(LobbyService lobbies) {
        List<String> ids = new ArrayList<>(lobbies.lobbyIds().stream().sorted().map(String::valueOf).toList());
        if (ids.isEmpty()) {
            ids.add("0");
        }
        return ids;
    }

    /** Live match ids for completion, oldest first. */
    public static List<String> instanceIdOptions(GameManager game) {
        return game.liveInstances().stream().map(instance -> String.valueOf(instance.matchId())).toList();
    }

    /** Stable confirm key for a sender: player uuid, or "console". */
    public static String senderKey(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getUniqueId().toString();
        }
        return "console";
    }

    /**
     * Selects online players by vanilla selector. Null when the
     * selector itself is broken; empty when it matches nothing.
     */
    public static List<Player> selectPlayers(CommandSender sender, String selector) {
        List<Player> targets = new ArrayList<>();
        try {
            for (Entity entity : Bukkit.selectEntities(sender, selector)) {
                if (entity instanceof Player player) {
                    targets.add(player);
                }
            }
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return targets;
    }

    /** Selector completion: vanilla selectors plus online player names. */
    public static List<String> selectorOptions() {
        List<String> selectors = new ArrayList<>(List.of("@a", "@r", "@s", "@p"));
        Bukkit.getOnlinePlayers().forEach(player -> selectors.add(player.getName()));
        return selectors;
    }
}
