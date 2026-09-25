package com.jruk8.jmanhunt.gui;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Click-type routing: right clicks prefer the right action, everything
 * else runs the main action, and display-only buttons stay silent.
 */
class GuiClickRoutingTest {

    private static MenuButton button(Consumer<Player> action, Consumer<Player> right) {
        return new MenuButton(Material.PAPER, Component.text("b"), List.of(),
                false, false, action, right);
    }

    @Test
    void rightClickPrefersRightAction() {
        Consumer<Player> action = player -> {};
        Consumer<Player> right = player -> {};
        MenuButton button = button(action, right);

        assertSame(right, GuiService.clickAction(button, ClickType.RIGHT));
        assertSame(right, GuiService.clickAction(button, ClickType.SHIFT_RIGHT));
    }

    @Test
    void rightClickFallsBackToMainAction() {
        Consumer<Player> action = player -> {};
        MenuButton button = button(action, null);

        assertSame(action, GuiService.clickAction(button, ClickType.RIGHT));
    }

    @Test
    void otherClicksRunMainAction() {
        Consumer<Player> action = player -> {};
        Consumer<Player> right = player -> {};
        MenuButton button = button(action, right);

        assertSame(action, GuiService.clickAction(button, ClickType.LEFT));
        assertSame(action, GuiService.clickAction(button, ClickType.SHIFT_LEFT));
        assertSame(action, GuiService.clickAction(button, ClickType.NUMBER_KEY));
    }

    @Test
    void missingButtonOrActionStaysSilent() {
        assertNull(GuiService.clickAction(null, ClickType.LEFT));
        assertNull(GuiService.clickAction(button(null, null), ClickType.LEFT));
        assertNull(GuiService.clickAction(button(null, null), ClickType.RIGHT));
    }

    @Test
    void doubleClickNeverReachesAnAction() {
        Consumer<Player> action = player -> {};
        Consumer<Player> right = player -> {};

        assertNull(GuiService.clickAction(button(action, right), ClickType.DOUBLE_CLICK));
        assertNull(GuiService.clickAction(button(action, null), ClickType.DOUBLE_CLICK));
    }

    @Test
    void legacyButtonsKeepSingleAction() {
        AtomicReference<String> fired = new AtomicReference<>();
        MenuButton button = new MenuButton(Material.PAPER, null, null,
                false, false, player -> fired.set("main"));

        assertNull(button.rightAction());
        GuiService.clickAction(button, ClickType.RIGHT).accept(null);
        assertEquals("main", fired.get());
    }
}
