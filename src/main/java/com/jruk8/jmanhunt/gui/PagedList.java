package com.jruk8.jmanhunt.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/**
 * Five-row scrollable list in the modifiers-menu pattern: six content
 * columns with scroll arrows plus back on the left and optional
 * caller chrome on the right. Modifiers, presets, and spectator
 * browsing share this instead of inventing their own scrolling
 * chrome. Chrome labels stay caller-owned so each menu keeps its
 * own message keys.
 */
public final class PagedList {

    private PagedList() {
    }

    /** Optional right-column chrome: top (8), middle (26), bottom (44). */
    public record Chrome(MenuButton top, MenuButton middle, MenuButton bottom) {
        /** No side chrome; those slots stay empty. */
        public static Chrome none() {
            return new Chrome(null, null, null);
        }
    }

    /**
     * @param title menu title
     * @param content content factory, given the six content columns
     * @param parent supplier of the menu back returns to, null for a root
     * @param gui service backing back navigation
     * @param scrollUpName scroll-up arrow label
     * @param scrollDownName scroll-down arrow label
     * @param backName back button label
     * @param chrome side chrome factory, given the menu for navigation
     */
    public static Menu menu(Component title, Function<Integer, List<MenuButton>> content,
            Supplier<Menu> parent, GuiService gui, Component scrollUpName,
            Component scrollDownName, Component backName, Function<Menu[], Chrome> chrome) {
        MenuLayout layout = MenuLayout.parse(
                "##xxxxxx#", "u#xxxxxx#", "b#xxxxxxt", "d#xxxxxx#", "##xxxxxx#");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(title, layout,
                () -> chromeStatic(self, gui, scrollUpName, scrollDownName, backName, chrome),
                () -> content.apply(layout.contentColumns()), parent);
        return self[0];
    }

    private static Map<Integer, MenuButton> chromeStatic(Menu[] self, GuiService gui,
            Component scrollUpName, Component scrollDownName, Component backName,
            Function<Menu[], Chrome> chrome) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        Chrome side = chrome.apply(self);
        if (side.top() != null) {
            fixed.put(8, side.top());
        }
        fixed.put(9, arrow(scrollUpName, self, -1));
        fixed.put(18, new MenuButton(new MenuButton.Spec(Material.PAPER, backName, null, false, false,
                player -> gui.back(player, self[0]), null, null, MenuButton.SoundPolicy.CLICK, null)));
        if (side.middle() != null) {
            fixed.put(26, side.middle());
        }
        fixed.put(27, arrow(scrollDownName, self, 1));
        if (side.bottom() != null) {
            fixed.put(44, side.bottom());
        }
        return fixed;
    }

    private static MenuButton arrow(Component name, Menu[] self, int delta) {
        return new MenuButton(new MenuButton.Spec(Material.ARROW, name, null, false, false,
                player -> self[0].window().scrollLine(delta), null, null, MenuButton.SoundPolicy.CLICK, null));
    }
}
