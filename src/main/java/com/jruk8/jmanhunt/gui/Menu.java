package com.jruk8.jmanhunt.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;

/**
 * One menu page: layout, static buttons, and a scrollable content factory.
 *
 * <p>Content buttons are rebuilt from the factory on every refresh so toggles
 * and preset renames show immediately. A null parent supplier means this menu
 * is a root and back closes the inventory.
 */
public final class Menu {

    private final Component title;
    private final MenuLayout layout;
    private final Map<Integer, MenuButton> staticButtons;
    private final Supplier<List<MenuButton>> contentFactory;
    private final Supplier<Menu> parent;
    private final ContentWindow<MenuButton> window;

    /**
     * @param title inventory title
     * @param layout slot structure
     * @param staticButtons buttons pinned to raw slots, null means none
     * @param contentFactory rebuilds the scrollable content on every refresh
     * @param parent supplier of the menu back returns to, null for a root
     */
    public Menu(Component title, MenuLayout layout, Map<Integer, MenuButton> staticButtons,
            Supplier<List<MenuButton>> contentFactory, Supplier<Menu> parent) {
        this.title = title;
        this.layout = layout;
        this.staticButtons = staticButtons == null
                ? Map.of() : Map.copyOf(new HashMap<>(staticButtons));
        this.contentFactory = contentFactory;
        this.parent = parent;
        this.window = new ContentWindow<>(
                Math.max(1, layout.contentColumns()), Math.max(1, layout.contentRows()));
        refresh();
    }

    /** Rebuilds content from the factory and clamps the scroll offset. */
    public void refresh() {
        window.setEntries(contentFactory.get());
    }

    /**
     * Button shown at a raw top-inventory slot, or null for filler, empty
     * content cells, and slots outside the layout.
     */
    public MenuButton buttonAt(int slot) {
        MenuButton fixed = staticButtons.get(slot);
        if (fixed != null) {
            return fixed;
        }
        int contentIndex = layout.contentIndex(slot);
        if (contentIndex < 0) {
            return null;
        }
        List<MenuButton> visible = window.visibleEntries();
        if (contentIndex >= visible.size()) {
            return null;
        }
        return visible.get(contentIndex);
    }

    public Component title() {
        return title;
    }

    public MenuLayout layout() {
        return layout;
    }

    public ContentWindow<MenuButton> window() {
        return window;
    }

    public Supplier<Menu> parent() {
        return parent;
    }
}
