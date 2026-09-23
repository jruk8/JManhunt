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
import org.junit.jupiter.api.Test;

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
                fixed, () -> new ArrayList<>(content), null);

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
    void fillerIsAGrayTooltipLessPaneWithoutAction() {
        MenuButton filler = MenuButton.filler();

        assertEquals(Material.GRAY_STAINED_GLASS_PANE, filler.material());
        assertTrue(filler.hideTooltip());
        assertNull(filler.action());
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
}
