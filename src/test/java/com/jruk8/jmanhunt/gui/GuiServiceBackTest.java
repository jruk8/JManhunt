package com.jruk8.jmanhunt.gui;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Back and cancel with nowhere to go close the GUI. */
class GuiServiceBackTest {

    private Menu root() {
        return new Menu(Component.text("Root"),
                MenuLayout.parse("#########", "###mbp###", "#########"),
                Map::of, List::of, null);
    }

    @Test
    void navigateNullCloses() {
        Player player = mock(Player.class);

        new GuiService().navigate(player, null);

        verify(player).closeInventory();
    }

    @Test
    void backAtRootCloses() {
        Player player = mock(Player.class);

        new GuiService().back(player, root());

        verify(player).closeInventory();
    }

    @Test
    void backWithNullMenuCloses() {
        Player player = mock(Player.class);

        new GuiService().back(player, null);

        verify(player).closeInventory();
    }
}
