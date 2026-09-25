package com.jruk8.jmanhunt.gui;

import com.jruk8.jmanhunt.message.MessageService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/**
 * Fixed six-row scroll list in the modifiers-menu pattern.
 *
 * <p>Content fills columns 0 to 7 across all six rows; column 8 holds
 * scroll-up at the top, the back arrow in the middle, and scroll-down at
 * the bottom. Pure-keys lists reuse this instead of inventing their own
 * scrolling chrome.
 */
public final class ScrollList {

    private ScrollList() {
    }

    /**
     * @param title inventory title
     * @param content rebuilds the scrollable buttons on every refresh
     * @param parent supplier of the menu back returns to, null for a root
     * @param gui navigation for the back arrow
     * @param messages button labels
     * @return the scroll list menu
     */
    public static Menu menu(Component title, Supplier<List<MenuButton>> content,
            Supplier<Menu> parent, GuiService gui, MessageService messages) {
        MenuLayout layout = MenuLayout.parse(
                "xxxxxxxxu", "xxxxxxxx#", "xxxxxxxxb",
                "xxxxxxxx#", "xxxxxxxx#", "xxxxxxxxd");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(title, layout,
                () -> scrollStatic(self, messages, gui),
                content::get, parent);
        return self[0];
    }

    private static Map<Integer, MenuButton> scrollStatic(Menu[] self,
            MessageService messages, GuiService gui) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        fixed.put(8, scrollButton(messages, self,
                "manhunt-gui.scroll-up", "Scroll up", -1));
        fixed.put(26, new MenuButton(Material.PAPER,
                GuiTexts.name(messages,
                        messages.string("manhunt-gui.back", "Back"), "Back"),
                null, false, false,
                player -> gui.back(player, self[0])));
        fixed.put(53, scrollButton(messages, self,
                "manhunt-gui.scroll-down", "Scroll down", 1));
        return fixed;
    }

    private static MenuButton scrollButton(MessageService messages,
            Menu[] self, String labelKey, String fallback, int delta) {
        return new MenuButton(Material.ARROW,
                GuiTexts.name(messages, messages.string(labelKey, fallback), fallback),
                null, false, false,
                player -> self[0].window().scrollLine(delta));
    }
}
