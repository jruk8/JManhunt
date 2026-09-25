package com.jruk8.jmanhunt.gui.dialog;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/**
 * Modifier dialogs. The Paper implementation lives in
 * {@link ModifierDialogs}; menus depend on this port so headless tests
 * never link the client dialog classes.
 */
public interface ModifierDialog {

    /**
     * Opens the Runs On checkbox dialog: one checkbox per known
     * trigger, initialled from the live list.
     *
     * @param player clicking player
     * @param current live runs-on entries, matched case-insensitively
     * @param onSubmit receives the checked trigger set, in known order
     * @param reopen rebuilds the menu Submit and Cancel return to
     */
    void openRunsOn(Player player, List<String> current,
            Consumer<Set<String>> onSubmit, Runnable reopen);
}
