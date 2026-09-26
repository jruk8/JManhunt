package com.jruk8.jmanhunt.gui.dialog;

import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.gui.Menu;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Value dialogs for scalar settings. The Paper implementation lives in
 * {@link SettingDialogs}; menus depend on this port so headless tests
 * never link the client dialog classes.
 */
public interface SettingDialog {

    /**
     * Opens the value dialog for one scalar setting.
     *
     * @param player clicking player
     * @param descriptor INT, FLOAT, or STRING setting
     * @param title dialog title, supplied pre-rendered by the caller
     * @param reopen rebuilds the menu Submit and Cancel return to
     */
    void openSetting(Player player, SettingDescriptor descriptor,
            Component title, Supplier<Menu> reopen);

    /**
     * Opens the text dialog editing one list entry.
     *
     * @param player clicking player
     * @param listPath full list path
     * @param index live entry index
     * @param title dialog title, supplied pre-rendered by the caller
     * @param reopen rebuilds the menu Submit and Cancel return to
     */
    void openListEntry(Player player, String listPath, int index,
            Component title, Supplier<Menu> reopen);

    /**
     * Opens the text dialog appending one list entry.
     *
     * @param player clicking player
     * @param listPath full list path
     * @param title dialog title, supplied pre-rendered by the caller
     * @param reopen rebuilds the menu Submit and Cancel return to
     */
    void openListAppend(Player player, String listPath,
            Component title, Supplier<Menu> reopen);

    /**
     * Single free-text prompt with body lines. Submit and cancel run one
     * tick later so callers may navigate safely.
     */
    void prompt(Player player, String titleText, List<String> body,
            Consumer<String> onSubmit, Runnable onCancel);

    /** Same, with a prefilled value for editing existing text. */
    void prompt(Player player, String titleText, String initial, List<String> body,
            Consumer<String> onSubmit, Runnable onCancel);
}
