package com.jruk8.jmanhunt.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Shared panel chrome.
 *
 * <p>The stock back button is a lore-less paper that returns to the parent
 * menu and clicks centrally like any other navigation.
 */
public final class Panels {

    private Panels() {
    }

    /**
     * @param gui navigation service, only touched inside the click action
     * @param backName back button name
     * @param self the menu cell back returns from, read at click time
     *        because factories run before the cell is assigned
     * @return the stock back button
     */
    public static MenuButton backButton(GuiService gui, Component backName, Menu[] self) {
        return new MenuButton(Material.PAPER, backName, null, false, false,
                (Player player) -> gui.back(player, self[0]));
    }
}
