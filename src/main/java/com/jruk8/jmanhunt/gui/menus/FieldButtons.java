package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.message.MessageService;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Config-field buttons in the shared schema. Silent: the click edits
 * or cycles (playing its own sound) and the right-click resets
 * through a confirm panel.
 */
public final class FieldButtons {

    private FieldButtons() {
    }

    /**
     * @param label button name
     * @param loreLines raw lore lines, usually from {@link FieldLore}
     * @param glow true to force the enchantment glint
     * @param click left-click action
     * @param rightClick right-click reset action
     */
    public static MenuButton field(MessageService messages, Material material,
            String label, List<String> loreLines, boolean glow,
            Consumer<Player> click, Consumer<Player> rightClick) {
        return new MenuButton(material,
                GuiTexts.name(messages, label, label),
                GuiTexts.lore(messages, loreLines),
                glow, false, click, rightClick).silent();
    }
}
