package com.jruk8.jmanhunt.command;

import org.bukkit.command.CommandSender;
import java.util.List;
import java.util.Set;

/**
 * One /manhunt subcommand family: execution plus tab completion.
 * The router dispatches by lowercased verb; units own their handler,
 * helpers, arg parsing, and completion fragment.
 */
public interface SubcommandUnit {
    /** Canonical verb, lowercased (qs maps to the quickstart unit). */
    String primaryName();

    /** Extra verbs routed here, lowercased. Empty when there are none. */
    Set<String> aliases();

    /** Executes with the full args array (args[0] is the verb). */
    boolean execute(CommandSender sender, String[] args);

    /** Tab completion for this unit's verbs. Null when inapplicable. */
    List<String> complete(CommandSender sender, String[] args);
}
