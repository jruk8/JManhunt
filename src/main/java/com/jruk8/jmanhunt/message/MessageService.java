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
    private static final java.util.regex.Pattern LEGACY_CODE =
            java.util.regex.Pattern.compile("(?i)&([0-9a-fk-or])");
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset"));

    private FileConfiguration messages;

    public void reload(FileConfiguration configuration) {
        messages = configuration;
    }

    public Component component(String key) {
        return component(key, Map.of());
    }

    public Component component(String key, Map<String, String> values) {
        return renderLiteral(messages.getString(key, key), values);
    }

    /**
     * Renders pre-composed text that still carries placeholders: substitutes
     * the prefixes and custom values, then parses as MiniMessage (legacy
     * &amp; codes convert first). For messages assembled from multiple keys
     * (like the win announcement) that can never pass through
     * {@link #component(String, Map)}.
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

    /**
     * Parses raw text as MiniMessage. Legacy &amp; color/format codes are
     * converted first, so users can still write codes like &amp;7 or &amp;6;
     * any other &amp; use survives untouched.
     */
    public Component parse(String raw) {
        return MiniMessage.miniMessage().deserialize(legacyToMiniMessage(raw));
    }

    /**
     * Parses hard-coded text as pure MiniMessage, for messages that embed
     * MiniMessage tags and never carry legacy codes.
     */
    public Component miniMessage(String raw) {
        return MiniMessage.miniMessage().deserialize(raw);
    }

    /**
     * Converts legacy &amp; codes to MiniMessage tags. Only a code
     * character after the &amp; converts; anything else (like Tom &amp;
     * Jerry) survives. Pure for tests.
     */
    static String legacyToMiniMessage(String raw) {
        java.util.regex.Matcher matcher = LEGACY_CODE.matcher(raw);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            matcher.appendReplacement(result, "<" + LEGACY_TAGS.get(code) + ">");
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public String formatPlaceholder(String raw) {
        return LegacyComponentSerializer.legacySection().serialize(parse(raw));
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
        if (!isDisabled(key)) {
            Bukkit.broadcast(component(key));
        }
    }
    public void broadcast(String key, Map<String, String> values) {
        if (!isDisabled(key)) {
            Bukkit.broadcast(component(key, values));
        }
    }

    /** Sends a message to exactly the given recipients (lobby or instance members). */
    public void sendTo(Collection<? extends Player> recipients, String key) {
        sendTo(recipients, key, Map.of());
    }

    /** Sends a message to exactly the given recipients (lobby or instance members). */
    public void sendTo(Collection<? extends Player> recipients, String key, Map<String, String> values) {
        if (isDisabled(key)) {
            return;
        }
        Component rendered = component(key, values);
        for (Player recipient : recipients) {
            recipient.sendMessage(rendered);
        }
    }

    public void message(CommandSender sender, String key) {
        if (!isDisabled(key)) {
            sender.sendMessage(component(key));
        }
    }
    public void message(CommandSender sender, String key, Map<String, String> values) {
        if (!isDisabled(key)) {
            sender.sendMessage(component(key, values));
        }
    }
}
