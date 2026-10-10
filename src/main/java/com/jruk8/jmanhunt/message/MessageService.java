package com.jruk8.jmanhunt.message;

import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

public final class MessageService {
    /** Brand red behind the {brandcolor} placeholder in templates. */
    public static final String BRAND_COLOR = "<#de7766>";

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

    private MessagesConfig messages = new MessagesConfig();

    public void reload(MessagesConfig configuration) {
        messages = configuration == null ? new MessagesConfig() : configuration;
    }

    /**
     * Typed section reads for wiring only (rule M1 keeps
     * ManhuntCommand's constructor frozen, so it and the plugin plus
     * service wiring read sections here). Every other consumer
     * receives its minimal section through its own constructor.
     */
    public String prefix() {
        return messages.getPrefix();
    }

    public CommandMessages command() {
        return messages.getCommand();
    }

    public ManhuntMessages manhunt() {
        return messages.getManhunt();
    }

    public GameMessages game() {
        return messages.getGame();
    }

    public CompassMessages compass() {
        return messages.getCompass();
    }

    public ChatMessages chat() {
        return messages.getChat();
    }

    public RoleColorsMessages roleColors() {
        return messages.getRoleColors();
    }

    public WinconMessages wincon() {
        return messages.getWincon();
    }

    public DebugMessages debug() {
        return messages.getDebug();
    }

    public DevMessages dev() {
        return messages.getDev();
    }

    public ModifiersMessages modifiers() {
        return messages.getModifiers();
    }

    public ModifiersGuiMessages modifiersGui() {
        return messages.getModifiersGui();
    }

    public ManhuntGuiMessages manhuntGui() {
        return messages.getManhuntGui();
    }

    public SpectatorMessages spectator() {
        return messages.getSpectator();
    }

    /**
     * Renders pre-composed text that still carries placeholders: substitutes
     * the prefixes and custom values, then parses as MiniMessage (legacy
     * &amp; codes convert first). For messages assembled from multiple
     * values (like the win announcement) that can never pass through
     * {@link #componentRaw(String, Map)}.
     */
    public Component renderLiteral(String raw, Map<String, String> values) {
        String rendered = raw.replace("{prefix}", messages.getPrefix())
                .replace("{debug-prefix}", messages.getDebug().getPrefix());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        // Role colors resolve after values so composed text (win-condition
        // fragments passed as {conditions}) picks them up too.
        for (Role role : Role.values()) {
            rendered = rendered.replace("{role-color-" + role.name().toLowerCase(Locale.ROOT) + "}",
                    roleColor(role));
        }
        rendered = rendered.replace("{brandcolor}", BRAND_COLOR);
        return parse(rendered);
    }

    /** True when raw text is null or empty, which disables the message everywhere it would be sent. */
    public boolean blank(String raw) {
        return raw == null || raw.isEmpty();
    }

    /** Renders one typed value (never a key) with placeholders. */
    public Component componentRaw(String raw, Map<String, String> values) {
        return renderLiteral(raw, values);
    }

    /** Renders one typed value without placeholders. */
    public Component componentRaw(String raw) {
        return componentRaw(raw, Map.of());
    }

    /** Sends one typed value to a sender, skipping blank values. */
    public void messageRaw(CommandSender sender, String raw, Map<String, String> values) {
        if (!blank(raw)) {
            sender.sendMessage(componentRaw(raw, values));
        }
    }

    /** Sends one typed value to a sender without placeholders, skipping blank values. */
    public void messageRaw(CommandSender sender, String raw) {
        messageRaw(sender, raw, Map.of());
    }

    /** Broadcasts one typed value, skipping blank values. */
    public void broadcastRaw(String raw, Map<String, String> values) {
        if (!blank(raw)) {
            Bukkit.broadcast(componentRaw(raw, values));
        }
    }

    /** Broadcasts one typed value without placeholders, skipping blank values. */
    public void broadcastRaw(String raw) {
        broadcastRaw(raw, Map.of());
    }

    /** Sends one typed value to exactly the given recipients, skipping blank values. */
    public void sendToRaw(Collection<? extends Player> recipients, String raw, Map<String, String> values) {
        if (blank(raw)) {
            return;
        }
        Component rendered = componentRaw(raw, values);
        for (Player recipient : recipients) {
            recipient.sendMessage(rendered);
        }
    }

    /** Sends one typed value to exactly the given recipients without placeholders. */
    public void sendToRaw(Collection<? extends Player> recipients, String raw) {
        sendToRaw(recipients, raw, Map.of());
    }

    /** Role display name prefixed with its configured color tag, without a reset. */
    public String roleName(Role role) {
        return roleColor(role) + role.displayName();
    }

    /** Configured color tag for one role, without any reset. */
    public String roleColor(Role role) {
        RoleColorsMessages colors = messages.getRoleColors();
        return switch (role) {
            case SPEEDRUNNER -> colors.getSpeedrunner();
            case HUNTER -> colors.getHunter();
            case SPECTATOR -> colors.getSpectator();
            case AFK -> colors.getAfk();
            case NONE -> colors.getNone();
        };
    }

    /**
     * Raw six-line win announcement for one winner: blank, prefix,
     * separator, role-colored title, reason line, separator. Render it
     * with the wincon and rolecolor slots. The color wrap is
     * unconditional: plain titles take the role color while an explicit
     * tag inside a custom override still dominates it.
     */
    public String winAnnouncement(Role winner) {
        GameMessages game = messages.getGame();
        String title = winner == Role.HUNTER ? game.getHuntersWin() : game.getSpeedrunnersWin();
        return winBlock(game.getSeparator(), roleColor(winner) + title, game.getWinReason());
    }

    /**
     * Six-line win announcement shape: blank, prefix, separator, title,
     * reason, separator. Pure for tests.
     */
    static String winBlock(String separator, String title, String reasonLine) {
        return "\n{prefix}\n" + separator + "\n" + title + "\n" + reasonLine + "\n" + separator;
    }

    /** Role-colored fullscreen win title for one winner. */
    public Component winTitle(Role winner) {
        GameMessages game = messages.getGame();
        String title = winner == Role.HUNTER ? game.getHuntersTitle() : game.getSpeedrunnersTitle();
        return renderLiteral(roleColor(winner) + title, Map.of());
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
     * Jerry) survives. Section codes convert first, so serialized legacy
     * text (including hex colors) never reaches the MiniMessage parser
     * raw. Pure for tests.
     */
    public static String legacyToMiniMessage(String raw) {
        String sectioned = sectionToMiniMessage(raw);
        java.util.regex.Matcher matcher = LEGACY_CODE.matcher(sectioned);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            matcher.appendReplacement(result, "<" + LEGACY_TAGS.get(code) + ">");
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Converts section codes to MiniMessage tags: hex sequences first
     * (a §x followed by six §hex digits becomes a hex tag), then single
     * codes. Unknown sequences survive untouched. Pure for tests.
     */
    static String sectionToMiniMessage(String raw) {
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < raw.length()) {
            char current = raw.charAt(index);
            if (current != '§' || index + 1 >= raw.length()) {
                result.append(current);
                index++;
                continue;
            }
            char code = Character.toLowerCase(raw.charAt(index + 1));
            String hex = readSectionHex(raw, index);
            if (hex != null) {
                result.append(hex);
                index += 14;
                continue;
            }
            String tag = LEGACY_TAGS.get(code);
            if (tag != null) {
                result.append('<').append(tag).append('>');
                index += 2;
                continue;
            }
            result.append(current);
            index++;
        }
        return result.toString();
    }

    /**
     * Hex tag for a §x sequence at the index, or null when the sequence
     * is absent or malformed. Pure for tests.
     */
    private static String readSectionHex(String raw, int index) {
        if (Character.toLowerCase(raw.charAt(index + 1)) != 'x'
                || index + 14 > raw.length()) {
            return null;
        }
        StringBuilder hex = new StringBuilder();
        for (int offset = 2; offset < 14; offset += 2) {
            if (raw.charAt(index + offset) != '§') {
                return null;
            }
            char digit = raw.charAt(index + offset + 1);
            if (Character.digit(digit, 16) == -1) {
                return null;
            }
            hex.append(digit);
        }
        return "<#" + hex + ">";
    }

    public String formatPlaceholder(String raw) {
        return LegacyComponentSerializer.legacySection().serialize(parse(raw));
    }

    public Component nonItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    /** Broadcasts raw text (already formatted) from engine tags. */
    public void broadcastText(String text) {
        Bukkit.broadcast(parse(text));
    }

    /** Sends raw text (already formatted) to one player from engine tags. */
    public void sendText(Player player, String text) {
        player.sendMessage(parse(text));
    }
}
