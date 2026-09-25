package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.message.MessageService;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Trailing stick button for scroll lists: the append entry after the
 * last line, prompting before it creates anything. Shared by command
 * lines and the settings list editor.
 */
public final class AddStick {

    private AddStick() {
    }

    /** Silent stick; the prompt it opens plays its own dialog sound. */
    public static MenuButton button(MessageService messages, String name, List<String> lore,
            Consumer<Player> action) {
        return new MenuButton(Material.STICK,
                GuiTexts.name(messages, name, name),
                GuiTexts.lore(messages, lore),
                false, false, action).silent();
    }
}
