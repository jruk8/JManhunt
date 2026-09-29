package com.jruk8.jmanhunt.gui.dialog;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
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

    /**
     * Opens the Game Rules checkbox dialog: one checkbox per known
     * game-state rule, initialled from the live list.
     *
     * @param player clicking player
     * @param current live rules entries, matched case-insensitively
     * @param onSubmit receives the checked rules set, in known order
     * @param reopen rebuilds the menu Submit and Cancel return to
     */
    void openGameRules(Player player, List<String> current,
            Consumer<Set<String>> onSubmit, Runnable reopen);

    /**
     * Opens the Interfere During checkbox dialog: one checkbox per
     * weather bucket, initialled from the live list.
     *
     * @param player clicking player
     * @param current live weather entries, matched case-insensitively
     * @param onSubmit receives the checked buckets set, in known order
     * @param reopen rebuilds the menu Submit and Cancel return to
     */
    void openInterfereDuring(Player player, List<String> current,
            Consumer<Set<String>> onSubmit, Runnable reopen);

    /**
     * Test-a-Command answers: the remember flag, the upper-case
     * role, and the raw command box texts.
     */
    record TestSubmission(boolean remember, String role, List<String> commands) {
    }

    /**
     * Opens the Test-a-Command dialog: a remember checkbox, a role
     * scroller, and five command boxes over the command editor intro.
     *
     * @param player clicking player
     * @param initial prefilled remember flag, role, and box texts
     * @param onSubmit receives the submitted answers and returns the
     *        follow-up navigation, run one tick later like cancel
     * @param onCancel runs when the dialog is cancelled
     */
    void openTestCommands(Player player, TestSubmission initial,
            Function<TestSubmission, Runnable> onSubmit, Runnable onCancel);
}
