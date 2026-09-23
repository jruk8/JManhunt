package com.jruk8.jmanhunt.gui;

import com.jruk8.jmanhunt.message.MessageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Menu text built through the message pipeline.
 *
 * <p>Names and lore accept the same mixed Legacy and MiniMessage input as
 * chat messages. A missing color falls back to the per-slot default; broken
 * markup renders literally because the MiniMessage parser degrades unknown
 * and unterminated tags to text instead of throwing.
 */
public final class GuiTexts {

    private GuiTexts() {
    }

    /** Plain instance for tag stripping; rendering goes through the service. */
    private static final MiniMessage STRIPPER = MiniMessage.miniMessage();

    /** Inventory title: default color, never italic, never blank. */
    public static Component title(MessageService messages, String raw) {
        String text = raw == null || raw.isBlank() ? " " : raw;
        return messages.nonItalic(messages.parse(text));
    }

    /** Button name with a fallback used when the raw text is blank. */
    public static Component name(MessageService messages, String raw, String fallback) {
        String text = raw == null || raw.isBlank() ? fallback : raw;
        return messages.nonItalic(messages.parse("<white>" + text));
    }

    /** Lore lines from newline-separated text; blank input yields no lines. */
    public static List<Component> lore(MessageService messages, String raw) {
        return loreWithAuthor(messages, raw, null);
    }

    /** Lore lines plus a trailing author line when the author is not blank. */
    public static List<Component> loreWithAuthor(MessageService messages, String raw, String author) {
        List<Component> lore = new ArrayList<>();
        if (raw != null && !raw.isBlank()) {
            List<String> lines = new ArrayList<>(List.of(raw.split("\\\\n|\n", -1)));
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                lines.remove(lines.size() - 1);
            }
            for (String line : lines) {
                lore.add(messages.nonItalic(messages.parse("<gray>" + line)));
            }
        }
        if (author != null && !author.isBlank()) {
            if (!lore.isEmpty()) {
                lore.add(Component.text(" "));
            }
            lore.add(messages.nonItalic(messages.parse("<gray>by " + author)));
        }
        return lore;
    }

    /** Case-insensitive sort key with all tags stripped. */
    public static String sortKey(String raw) {
        if (raw == null) {
            return "";
        }
        return STRIPPER.stripTags(MessageService.legacyToMiniMessage(raw)).trim()
                .toLowerCase(Locale.ROOT);
    }
}
