package com.jruk8.jmanhunt.message;

import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

public final class MessageService {
    private FileConfiguration messages;
    private String format = "minimessage";

    public void reload(FileConfiguration configuration, String textFormat) {
        messages = configuration;
        format = textFormat;
    }

    public Component component(String key) {
        return component(key, Map.of());
    }

    public Component component(String key, Map<String, String> values) {
        return renderLiteral(messages.getString(key, key), values);
    }

    /**
     * Renders pre-composed text that still carries placeholders: substitutes
     * the prefixes and custom values, then parses per the text format. For
     * messages assembled from multiple keys (like the win announcement) that
     * can never pass through {@link #component(String, Map)}.
     */
    public Component renderLiteral(String raw, Map<String, String> values) {
        String rendered = raw.replace("{prefix}", messages.getString("prefix", ""))
                .replace("{debug-prefix}", messages.getString("debug.prefix", ""));
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        // Role colors resolve after values so composed text (win-condition
        // fragments passed as {conditions}) picks them up too.
        for (Role role : Role.values()) {
            rendered = rendered.replace("{role-color-" + role.name().toLowerCase(Locale.ROOT) + "}",
                    string("role-colors." + role.name().toLowerCase(Locale.ROOT), defaultRoleColor(role)));
        }
        return parse(rendered);
    }

    /** Role display name prefixed with its configured color tag, without a reset. */
    public String roleName(Role role) {
        return string("role-colors." + role.name().toLowerCase(Locale.ROOT), defaultRoleColor(role))
                + role.displayName();
    }

    private static String defaultRoleColor(Role role) {
        return switch (role) {
            case SPEEDRUNNER -> "<#74de66>";
            case HUNTER -> "<#de666e>";
            case SPECTATOR -> "<#6e728a>";
            case AFK -> "<#a18e68>";
            case NONE -> "<#7d7d7d>";
        };
    }

    /**
     * True when a message key is explicitly set to the empty string, which
     * disables that message everywhere it would be sent.
     */
    public boolean isDisabled(String key) {
        String raw = messages.getString(key, null);
        return raw != null && raw.isEmpty();
    }

    public Component parse(String raw) {
        return "legacy".equalsIgnoreCase(format)
                ? LegacyComponentSerializer.legacyAmpersand().deserialize(raw)
                : MiniMessage.miniMessage().deserialize(raw);
    }

    /**
     * Parses raw text as MiniMessage regardless of the configured text-format,
     * for hard-coded messages that embed MiniMessage tags.
     */
    public Component miniMessage(String raw) {
        return MiniMessage.miniMessage().deserialize(raw);
    }

    public String formatPlaceholder(String raw) {
        if ("legacy".equalsIgnoreCase(format)) {
            return LegacyComponentSerializer.legacySection().serialize(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
        }
        return LegacyComponentSerializer.legacySection().serialize(
                MiniMessage.miniMessage().deserialize(raw));
    }

    public String addSeparators(String text) {
        String separator = string("game.separator", "");
        return separator + "\n" + text + "\n" + separator;
    }

    public Component nonItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    public String string(String path, String fallback) {
        return messages.getString(path, fallback);
    }

    public java.util.List<String> strings(String path) {
        return messages.getStringList(path);
    }

    public void broadcast(String key) {
        if (!isDisabled(key)) Bukkit.broadcast(component(key));
    }
    public void broadcast(String key, Map<String, String> values) {
        if (!isDisabled(key)) Bukkit.broadcast(component(key, values));
    }

    /** Sends a message to exactly the given recipients (lobby or instance members). */
    public void sendTo(Collection<? extends Player> recipients, String key) {
        sendTo(recipients, key, Map.of());
    }

    /** Sends a message to exactly the given recipients (lobby or instance members). */
    public void sendTo(Collection<? extends Player> recipients, String key, Map<String, String> values) {
        if (isDisabled(key)) return;
        Component rendered = component(key, values);
        for (Player recipient : recipients) {
            recipient.sendMessage(rendered);
        }
    }

    public void message(CommandSender sender, String key) {
        if (!isDisabled(key)) sender.sendMessage(component(key));
    }
    public void message(CommandSender sender, String key, Map<String, String> values) {
        if (!isDisabled(key)) sender.sendMessage(component(key, values));
    }
}
