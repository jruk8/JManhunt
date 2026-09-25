package com.jruk8.jmanhunt.gui;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Quad panel geometry: four specs on the middle row, back at the bottom,
 * filler everywhere else, and fail-fast spec counts.
 */
class QuadPanelTest {

    private static MenuButton spec(String name) {
        return new MenuButton(Material.STONE, Component.text(name), null,
                false, false, player -> {});
    }

    private static Menu quad(List<MenuButton> specs, GuiService gui) {
        return QuadPanel.menu(Component.text("Quad"), specs, gui,
                Component.text("Back"), null);
    }

    @Test
    void specsLandOnTheMiddleRowAndBackAtTheBottom() {
        List<MenuButton> specs = List.of(spec("1"), spec("2"), spec("3"), spec("4"));
        Menu menu = quad(specs, mock(GuiService.class));

        assertEquals(27, menu.layout().size());
        assertSame(specs.get(0), menu.buttonAt(10));
        assertSame(specs.get(1), menu.buttonAt(12));
        assertSame(specs.get(2), menu.buttonAt(14));
        assertSame(specs.get(3), menu.buttonAt(16));
        assertEquals(Material.PAPER, menu.buttonAt(22).material());
        assertNull(menu.buttonAt(0));
        assertNull(menu.buttonAt(9));
        assertNull(menu.buttonAt(11));
        assertNull(menu.buttonAt(13));
        assertNull(menu.buttonAt(26));
    }

    @Test
    void backReturnsThroughTheService() {
        GuiService gui = mock(GuiService.class);
        Player player = mock(Player.class);
        Menu menu = quad(List.of(spec("1"), spec("2"), spec("3"), spec("4")), gui);

        menu.buttonAt(QuadPanel.BACK_SLOT).action().accept(player);

        verify(gui).back(player, menu);
    }

    @Test
    void wrongSpecCountThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> quad(List.of(spec("1")), mock(GuiService.class)));
        assertThrows(IllegalArgumentException.class,
                () -> quad(List.of(spec("1"), spec("2"), spec("3"), spec("4"), spec("5")),
                        mock(GuiService.class)));
    }
}
