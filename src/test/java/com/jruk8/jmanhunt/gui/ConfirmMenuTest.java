package com.jruk8.jmanhunt.gui;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Confirm panel layout: cancel at 2, the described icon at 4, confirm
 * at 6, filler everywhere else, both actions wired.
 */
class ConfirmMenuTest {

    private static Menu panel(AtomicReference<String> fired) {
        return ConfirmMenu.create(Component.text("Reset?"), Material.PAPER,
                Component.text("Value"), List.of(Component.text("1 to 2")),
                Component.text("Cancel"), player -> fired.set("cancel"),
                Component.text("Confirm"), player -> fired.set("confirm"),
                null);
    }

    @Test
    void slotsHoldCancelIconConfirm() {
        Menu menu = panel(new AtomicReference<>());

        assertEquals(9, menu.layout().size());
        assertEquals(Material.RED_STAINED_GLASS_PANE,
                menu.buttonAt(ConfirmMenu.CANCEL_SLOT).material());
        assertEquals(Material.PAPER, menu.buttonAt(ConfirmMenu.ICON_SLOT).material());
        assertEquals(1, menu.buttonAt(ConfirmMenu.ICON_SLOT).lore().size());
        assertEquals(Material.LIME_STAINED_GLASS_PANE,
                menu.buttonAt(ConfirmMenu.CONFIRM_SLOT).material());
        assertNull(menu.buttonAt(0));
        assertNull(menu.buttonAt(8));
    }

    @Test
    void actionsFire() {
        AtomicReference<String> fired = new AtomicReference<>();
        Menu menu = panel(fired);

        menu.buttonAt(ConfirmMenu.CANCEL_SLOT).action().accept(null);
        assertEquals("cancel", fired.get());
        menu.buttonAt(ConfirmMenu.CONFIRM_SLOT).action().accept(null);
        assertEquals("confirm", fired.get());
        assertNull(menu.buttonAt(ConfirmMenu.ICON_SLOT).action());
    }
}
