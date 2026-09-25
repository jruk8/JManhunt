package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.message.MessageService;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Modifier and preset editor buttons in two lore shapes.
 *
 * <p>Value buttons show one {@code Current: <white>{value}} line plus a hint and
 * stay silent: they open a value dialog or flip a toggle, both of which
 * play their own sounds. Action buttons show plain descriptive lore and
 * click centrally: they navigate or run a command. Callers needing a
 * silent action (export, rename) append {@link MenuButton#silent()}.
 */
public final class EditorButtons {

    private EditorButtons() {
    }

    /** Value button without glow. */
    public static MenuButton valueButton(MessageService messages, Material material,
            String label, String value, String hint,
            Consumer<Player> action) {
        return valueButton(messages, material, label, value, hint, false, action);
    }

    /**
     * @param value live display value, shown after the Current prefix
     * @param hint affordance line such as Click to edit
     * @param glow true to force the enchantment glint
     */
    public static MenuButton valueButton(MessageService messages, Material material,
            String label, String value, String hint, boolean glow,
            Consumer<Player> action) {
        return new MenuButton(material,
                GuiTexts.name(messages, label, label),
                GuiTexts.lore(messages, List.of(
                        currentLine(messages, value),
                        hint)),
                glow, false, action).silent();
    }

    /** Action button without glow. */
    public static MenuButton actionButton(MessageService messages, Material material,
            String label, List<String> lines,
            Consumer<Player> action) {
        return actionButton(messages, material, label, lines, false, action);
    }

    /**
     * @param lines plain descriptive lore lines, never prefixed
     * @param glow true to force the enchantment glint
     */
    public static MenuButton actionButton(MessageService messages, Material material,
            String label, List<String> lines, boolean glow,
            Consumer<Player> action) {
        return new MenuButton(material,
                GuiTexts.name(messages, label, label),
                GuiTexts.lore(messages, lines),
                glow, false, action);
    }

    /** Single Current line; the only place the prefix is built. */
    static String currentLine(MessageService messages, String value) {
        return messages.string("modifiers-gui.editor-current", "Current: <white>{value}")
                .replace("{value}", value);
    }
}
