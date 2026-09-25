package com.jruk8.jmanhunt.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MenuDispatchTest {

    private static MenuButton button(String name, List<String> clicked) {
        return new MenuButton(Material.STONE, Component.text(name), null,
                false, false, player -> clicked.add(name));
    }

    @Test
    void routesStaticContentAndFillerSlots() {
        List<String> clicked = new ArrayList<>();
        MenuButton main = button("main", clicked);
        MenuButton presets = button("presets", clicked);
        Map<Integer, MenuButton> fixed = new HashMap<>();
        fixed.put(0, main);
        fixed.put(8, presets);
        List<MenuButton> content = new ArrayList<>();
        for (int index = 0; index < 14; index++) {
            content.add(button("c" + index, clicked));
        }
        Menu menu = new Menu(Component.text("Modifiers"),
                MenuLayout.parse("m#######p", "#xxxxxx##", "b###d###u"),
                () -> fixed, () -> new ArrayList<>(content), null);

        assertSame(main, menu.buttonAt(0));
        assertSame(presets, menu.buttonAt(8));
        assertSame(content.get(0), menu.buttonAt(10));
        assertSame(content.get(5), menu.buttonAt(15));
        assertNull(menu.buttonAt(1));
        assertNull(menu.buttonAt(16));
        assertNull(menu.buttonAt(26));
        assertNull(menu.buttonAt(99));

        menu.window().scrollLine(1);
        assertSame(content.get(6), menu.buttonAt(10));
        assertSame(content.get(11), menu.buttonAt(15));

        menu.buttonAt(10).action().accept(null);
        menu.buttonAt(0).action().accept(null);
        assertEquals(List.of("c6", "main"), clicked);
    }

    @Test
    void backWithMissingMenuOrRootClosesInventory() {
        GuiService gui = new GuiService();
        Player player = mock(Player.class);
        Menu root = new Menu(Component.text("Root"),
                MenuLayout.parse("#########"), null, List::of, null);

        gui.back(player, null);
        gui.back(player, root);

        verify(player, times(2)).closeInventory();
    }

    @Test
    void fillerIsAGrayTooltipLessPaneWithoutAction() {
        MenuButton filler = MenuButton.filler();

        assertEquals(Material.GRAY_STAINED_GLASS_PANE, filler.material());
        assertTrue(filler.hideTooltip());
        assertNull(filler.action());
    }

    @Test
    void confirmCommitsSilentlyAndCancelClicks() {
        Menu confirm = ConfirmMenu.create(Component.text("Delete?"), Material.PAPER, null, null,
                Component.text("Cancel"), player -> {}, Component.text("Confirm"), player -> {},
                null);

        assertEquals(MenuButton.SoundPolicy.CLICK,
                confirm.buttonAt(ConfirmMenu.CANCEL_SLOT).soundPolicy());
        assertEquals(MenuButton.SoundPolicy.SILENT,
                confirm.buttonAt(ConfirmMenu.CONFIRM_SLOT).soundPolicy());
    }

    @Test
    void buttonsClickByDefaultAndSilentCopiesKeepTheirFields() {
        MenuButton button = button("main", new ArrayList<>());

        assertEquals(MenuButton.SoundPolicy.CLICK, button.soundPolicy());

        MenuButton silent = button.silent();

        assertEquals(MenuButton.SoundPolicy.SILENT, silent.soundPolicy());
        assertEquals(button.material(), silent.material());
        assertEquals(button.name(), silent.name());
        assertEquals(button.glow(), silent.glow());
        assertSame(button.action(), silent.action());
        assertSame(silent, silent.silent());
    }

    @Test
    void refreshRebuildsContentAndClampsOffset() {
        List<MenuButton> content = new ArrayList<>();
        for (int index = 0; index < 14; index++) {
            content.add(button("c" + index, new ArrayList<>()));
        }
        AtomicInteger builds = new AtomicInteger();
        Menu menu = new Menu(Component.text("Modifiers"),
                MenuLayout.parse("#########", "#xxxxxx##", "#########"), null,
                () -> {
                    builds.incrementAndGet();
                    return new ArrayList<>(content);
                }, null);

        assertEquals(1, builds.get());
        menu.window().scrollLine(2);
        assertEquals(2, menu.window().lineOffset());

        content.clear();
        for (int index = 0; index < 6; index++) {
            content.add(button("n" + index, new ArrayList<>()));
        }
        menu.refresh();

        assertEquals(2, builds.get());
        assertEquals(0, menu.window().lineOffset());
        assertSame(content.get(0), menu.buttonAt(10));
    }

    @Test
    void refreshRebuildsStaticButtons() {
        AtomicInteger builds = new AtomicInteger();
        Menu menu = new Menu(Component.text("Modifiers"),
                MenuLayout.parse("#########", "#########", "#########"),
                () -> Map.of(0,
                        button("toggle" + builds.incrementAndGet(), new ArrayList<>())),
                List::of, null);

        assertEquals(1, builds.get());
        assertEquals(Component.text("toggle1"), menu.buttonAt(0).name());

        menu.refresh();

        assertEquals(2, builds.get());
        assertEquals(Component.text("toggle2"), menu.buttonAt(0).name());
    }
}
