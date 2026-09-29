package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.PlayerSinks;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.function.Consumer;

/**
 * Named-player plumbing shared by modifier and debuff runs: the
 * {@code <pmessage>} and {@code <psound>} sinks, the engine message
 * format, and the online-player lookup. One home so both managers
 * deliver identically.
 */
public final class NamedPlayerSinks {

    private NamedPlayerSinks() {
    }

    /**
     * Sinks delivering engine-formatted output to one online player:
     * false when offline so the tags warn, true once attempted.
     * Unknown sound ids warn through the given logger and skip.
     */
    public static PlayerSinks of(MessageService messages, SoundService sounds,
            Consumer<String> logWarning, String containerId) {
        return new PlayerSinks() {
            @Override
            public boolean message(String playerName, String text) {
                Player target = onlinePlayer(playerName);
                if (target == null) {
                    return false;
                }
                messages.sendText(target, formatEngineMessage(messages, text));
                return true;
            }

            @Override
            public boolean sound(String playerName, String soundId, float pitch, float volume) {
                Player target = onlinePlayer(playerName);
                if (target == null) {
                    return false;
                }
                if (!sounds.isValidSound(soundId)) {
                    logWarning.accept("modifier \"" + containerId
                            + "\" tried playing invalid sound \"" + soundId + "\"");
                    return true;
                }
                sounds.playCustomSound(target, soundId, pitch, volume);
                return true;
            }
        };
    }

    /** Engine message format shared by modifier and debuff runs. */
    public static String formatEngineMessage(MessageService messages, String text) {
        String format = messages.string("modifiers.message-format", "{prefix}{message}");
        return format.replace("{prefix}", messages.string("prefix", ""))
                .replace("{message}", text);
    }

    /** Online player by case-insensitive name, or null. */
    public static Player onlinePlayer(String playerName) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(playerName)) {
                return player;
            }
        }
        return null;
    }
}
