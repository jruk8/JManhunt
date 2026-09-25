package com.jruk8.jmanhunt.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;

/**
 * Quad panel: the shared 3x9 four-item menu.
 *
 * <p>Geometry is fixed: the four items sit on the middle row at slots 10,
 * 12, 14, and 16 ({@code #1#2#3#4#}), the back button at slot 22
 * ({@code ####b####}), and every other slot renders filler. Settings root,
 * both creator roots, and both Meta quads all build through here, so no
 * caller does quad slot math.
 */
public final class QuadPanel {

    /** Middle-row item slots in spec order. */
    public static final List<Integer> ITEM_SLOTS = List.of(10, 12, 14, 16);

    /** Bottom-row back slot. */
    public static final int BACK_SLOT = 22;

    private QuadPanel() {
    }

    /**
     * @param title inventory title
     * @param specs exactly four item buttons in slot order
     * @param gui navigation for the back button
     * @param backName back button name
     * @param parent supplier of the menu back returns to, null for a root
     * @return the quad menu
     * @throws IllegalArgumentException when specs does not hold four buttons
     */
    public static Menu menu(Component title, List<MenuButton> specs, GuiService gui,
            Component backName, Supplier<Menu> parent) {
        if (specs == null || specs.size() != ITEM_SLOTS.size()) {
            throw new IllegalArgumentException("Quad panel needs exactly 4 items.");
        }
        MenuLayout layout = MenuLayout.parse("#########", "#1#2#3#4#", "####b####");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(title, layout, () -> quadStatic(specs, gui, backName, self),
                List::of, parent);
        return self[0];
    }

    private static Map<Integer, MenuButton> quadStatic(List<MenuButton> specs,
            GuiService gui, Component backName, Menu[] self) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        for (int index = 0; index < ITEM_SLOTS.size(); index++) {
            fixed.put(ITEM_SLOTS.get(index), specs.get(index));
        }
        fixed.put(BACK_SLOT, Panels.backButton(gui, backName, self));
        return fixed;
    }
}
