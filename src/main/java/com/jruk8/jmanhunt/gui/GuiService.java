package com.jruk8.jmanhunt.gui;

import com.jruk8.jmanhunt.message.SoundService;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

/**
 * Opens, renders, and click-routes chest menus.
 *
 * <p>Menus carry their own state in the inventory holder, so no registry,
 * title matching, or scheduler cleanup is needed. Every in-window click on a
 * menu is cancelled for dupe safety; only plain single clicks in the player
 * inventory and clicks outside the window pass through untouched.
 *
 * <p>Every executed action also plays the compass click unless the button
 * is silent; silent buttons play their own commit sounds inside the action.
 * A click guard drops too-fast repeats and double-clicks never reach an
 * action, so rapid clicking cannot toggle twice.
 */
public final class GuiService {

    /** Central compass click for every executed non-silent action. */
    static final String CLICK_SOUND_KEY = "compass.left-click";

    private final MenuButton filler = MenuButton.filler();
    private final SoundService sounds;
    private final ClickGuard guard = new ClickGuard(System::currentTimeMillis);

    /** Silent service for tests that never route live clicks. */
    public GuiService() {
        this(null);
    }

    /**
     * @param sounds central click player, null only in unit tests that
     *        never route live clicks
     */
    public GuiService(SoundService sounds) {
        this.sounds = sounds;
    }

    /**
     * Opens a fresh rendering of the menu for the player. Buttons rebuild
     * first so navigation re-renders current state (glow, counts) instead
     * of the buttons cached when the menu was built.
     */
    public void open(Player player, Menu menu) {
        menu.refresh();
        MenuHolder holder = new MenuHolder(menu);
        Inventory inventory = Bukkit.createInventory(holder, menu.layout().size(), menu.title());
        holder.setInventory(inventory);
        render(menu, inventory);
        player.openInventory(inventory);
    }

    /** Opens another menu; Bukkit closes the current one automatically. */
    public void navigate(Player player, Menu menu) {
        open(player, menu);
    }

    /**
     * Returns to the parent menu, or closes the inventory at a root. Never
     * throws: a missing menu or parent just closes the inventory.
     */
    public void back(Player player, Menu menu) {
        Menu parent = menu == null || menu.parent() == null ? null : menu.parent().get();
        if (parent == null) {
            player.closeInventory();
            return;
        }
        navigate(player, parent);
    }

    /**
     * Rebuilds and re-renders the menu when it is still the player's open
     * top inventory. Navigation actions open a new inventory, so the guard
     * skips re-rendering menus the player already left.
     */
    public void refresh(Player player, Menu menu) {
        InventoryView open = player.getOpenInventory();
        if (!(open.getTopInventory().getHolder() instanceof MenuHolder holder)
                || holder.menu() != menu) {
            return;
        }
        menu.refresh();
        render(menu, open.getTopInventory());
    }

    /** Routes a click: cancel for safety, run at most one button action. */
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        Menu menu = holder.menu();
        boolean topSlot = event.getRawSlot() < event.getInventory().getSize();
        ClickType click = event.getClick();
        if (!topSlot && !click.isShiftClick() && click != ClickType.DOUBLE_CLICK) {
            return;
        }
        event.setCancelled(true);
        if (topSlot && event.getWhoClicked() instanceof Player player) {
            MenuButton button = menu.buttonAt(event.getRawSlot());
            Consumer<Player> action = clickAction(button, click);
            if (action != null && guard.accept(player.getUniqueId())) {
                action.accept(player);
                playClick(player, button);
                refresh(player, menu);
            }
        }
        if (event.getWhoClicked() instanceof Player player) {
            player.updateInventory();
        }
    }

    private void playClick(Player player, MenuButton button) {
        if (sounds == null || button.soundPolicy() != MenuButton.SoundPolicy.CLICK) {
            return;
        }
        sounds.playSound(player, CLICK_SOUND_KEY);
    }

    /** Cancels drags that touch menu slots. */
    public void handleDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        int topSize = event.getInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.updateInventory();
                }
                return;
            }
        }
    }

    /**
     * Action for the click type: right clicks prefer the right action and
     * fall back to the main action, every other click runs the main action.
     * Double-clicks map to nothing: the two single clicks already ran and
     * the double is only their echo.
     */
    static Consumer<Player> clickAction(MenuButton button, ClickType click) {
        if (button == null || click == ClickType.DOUBLE_CLICK) {
            return null;
        }
        if (click.isRightClick() && button.rightAction() != null) {
            return button.rightAction();
        }
        return button.action();
    }

    private void render(Menu menu, Inventory inventory) {
        inventory.clear();
        int size = menu.layout().size();
        for (int slot = 0; slot < size; slot++) {
            MenuButton button = menu.buttonAt(slot);
            if (button != null) {
                inventory.setItem(slot, button.buildItem());
            } else if (menu.layout().contentIndex(slot) < 0) {
                inventory.setItem(slot, filler.buildItem());
            }
            // Content slots without entries stay empty.
        }
    }
}
