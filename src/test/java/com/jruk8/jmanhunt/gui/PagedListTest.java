package com.jruk8.jmanhunt.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** Shared modifiers-pattern list: chrome slots, arrows, and back. */
class PagedListTest {

    private static MenuButton button(Material material) {
        return new MenuButton(material, Component.text("x"), null, false, false, null);
    }

    @Test
    void pinsChromeAndContentSlots() {
        Menu menu = PagedList.menu(Component.text("List"),
                columns -> List.of(button(Material.DIAMOND)), null, mock(GuiService.class),
                Component.text("Up"), Component.text("Down"), Component.text("Back"),
                self -> new PagedList.Chrome(button(Material.WRITABLE_BOOK),
                        button(Material.HOPPER), button(Material.LOOM)));

        assertEquals(45, menu.layout().size());
        assertEquals(6, menu.layout().contentColumns());
        assertEquals(Material.WRITABLE_BOOK, menu.buttonAt(8).material());
        assertEquals(Material.ARROW, menu.buttonAt(9).material());
        assertEquals(Component.text("Up"), menu.buttonAt(9).name());
        assertEquals(Material.PAPER, menu.buttonAt(18).material());
        assertEquals(Component.text("Back"), menu.buttonAt(18).name());
        assertEquals(Material.HOPPER, menu.buttonAt(26).material());
        assertEquals(Material.ARROW, menu.buttonAt(27).material());
        assertEquals(Component.text("Down"), menu.buttonAt(27).name());
        assertEquals(Material.LOOM, menu.buttonAt(44).material());
        assertEquals(Material.DIAMOND, menu.buttonAt(2).material());
        assertNotNull(menu.buttonAt(9).action());
    }

    @Test
    void missingChromeLeavesSideSlotsEmpty() {
        Menu menu = PagedList.menu(Component.text("List"), columns -> List.of(), null,
                mock(GuiService.class), Component.text("Up"), Component.text("Down"),
                Component.text("Back"), self -> PagedList.Chrome.none());

        assertNull(menu.buttonAt(8));
        assertNull(menu.buttonAt(26));
        assertNull(menu.buttonAt(44));
        assertNotNull(menu.buttonAt(9));
        assertNotNull(menu.buttonAt(18));
        assertNotNull(menu.buttonAt(27));
    }
}
