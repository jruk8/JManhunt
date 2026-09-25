package com.jruk8.jmanhunt.gui;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Twin panel geometry: left and right flanking the back slot, filler
 * everywhere else, and no back button when the name is null.
 */
class TwinPanelTest {

    private static MenuButton spec(String name) {
        return new MenuButton(Material.STONE, Component.text(name), null,
                false, false, player -> {});
    }

    @Test
    void itemsFlankTheBackSlot() {
        MenuButton left = spec("left");
        MenuButton right = spec("right");
        GuiService gui = mock(GuiService.class);
        Menu menu = TwinPanel.menu(Component.text("Twin"), left, right, gui,
                Component.text("Back"), null);

        assertEquals(27, menu.layout().size());
        assertSame(left, menu.buttonAt(TwinPanel.LEFT_SLOT));
        assertSame(right, menu.buttonAt(TwinPanel.RIGHT_SLOT));
        assertEquals(Material.PAPER, menu.buttonAt(TwinPanel.BACK_SLOT).material());
        assertNull(menu.buttonAt(0));
        assertNull(menu.buttonAt(9));
        assertNull(menu.buttonAt(26));

        Player player = mock(Player.class);
        menu.buttonAt(TwinPanel.BACK_SLOT).action().accept(player);
        verify(gui).back(player, menu);
    }

    @Test
    void nullBackNameLeavesTheMiddleSlotEmpty() {
        Menu menu = TwinPanel.menu(Component.text("Twin"), spec("left"), spec("right"),
                mock(GuiService.class), null, null);

        assertNull(menu.buttonAt(TwinPanel.BACK_SLOT));
        assertEquals(List.of(), menu.buttonAt(TwinPanel.LEFT_SLOT).lore());
    }
}
