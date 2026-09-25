package com.jruk8.jmanhunt.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;

/**
 * Twin panel: the shared 3-row two-item menu.
 *
 * <p>Geometry is fixed: the left item sits at slot 12, the back button at
 * slot 13, and the right item at slot 14 ({@code ###mbp###}); every other
 * slot renders filler. The modifiers main menu and the modifier Behavior
 * panel both build through here, so no caller does twin slot math.
 */
public final class TwinPanel {

    /** Middle-row left item slot. */
    public static final int LEFT_SLOT = 12;

    /** Middle-row back slot. */
    public static final int BACK_SLOT = 13;

    /** Middle-row right item slot. */
    public static final int RIGHT_SLOT = 14;

    private TwinPanel() {
    }

    /**
     * @param title inventory title
     * @param left left item button
     * @param right right item button
     * @param gui navigation for the back button
     * @param backName back button name, null for no back button
     * @param parent supplier of the menu back returns to, null for a root
     * @return the twin menu
     */
    public static Menu menu(Component title, MenuButton left, MenuButton right,
            GuiService gui, Component backName, Supplier<Menu> parent) {
        MenuLayout layout = MenuLayout.parse("#########", "###mbp###", "#########");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(title, layout,
                () -> twinStatic(left, right, gui, backName, self),
                List::of, parent);
        return self[0];
    }

    private static Map<Integer, MenuButton> twinStatic(MenuButton left, MenuButton right,
            GuiService gui, Component backName, Menu[] self) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        fixed.put(LEFT_SLOT, left);
        fixed.put(RIGHT_SLOT, right);
        if (backName != null) {
            fixed.put(BACK_SLOT, Panels.backButton(gui, backName, self));
        }
        return fixed;
    }
}
