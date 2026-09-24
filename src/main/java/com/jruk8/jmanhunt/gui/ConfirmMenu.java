package com.jruk8.jmanhunt.gui;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Modular single-row confirmation panel.
 *
 * <p>Cancel sits at slot 2, the described icon at slot 4, and Confirm at
 * slot 6; every other slot is filler. Callers supply the labels, the
 * description, and both click actions; the panel only fixes positions.
 */
public final class ConfirmMenu {

    /** Cancel slot. */
    public static final int CANCEL_SLOT = 2;

    /** Described icon slot. */
    public static final int ICON_SLOT = 4;

    /** Confirm slot. */
    public static final int CONFIRM_SLOT = 6;

    private ConfirmMenu() {
    }

    /**
     * @param title inventory title
     * @param icon center icon material
     * @param iconName center icon name, may be null
     * @param description center icon lore, null means none
     * @param cancelLabel cancel button name
     * @param onCancel cancel click action
     * @param confirmLabel confirm button name
     * @param onConfirm confirm click action
     * @param parent supplier of the menu back returns to, null for a root
     * @return the confirmation menu
     */
    public static Menu create(Component title, Material icon, Component iconName,
            List<Component> description, Component cancelLabel, Consumer<Player> onCancel,
            Component confirmLabel, Consumer<Player> onConfirm, Supplier<Menu> parent) {
        MenuLayout layout = MenuLayout.parse("#########");
        Map<Integer, MenuButton> fixed = Map.of(
                CANCEL_SLOT, new MenuButton(Material.RED_STAINED_GLASS_PANE,
                        cancelLabel, null, false, false, onCancel),
                ICON_SLOT, new MenuButton(icon, iconName, description, false, false, null),
                CONFIRM_SLOT, new MenuButton(Material.LIME_STAINED_GLASS_PANE,
                        confirmLabel, null, false, false, onConfirm));
        return new Menu(title, layout, () -> fixed, List::of, parent);
    }
}
