package com.jruk8.jmanhunt.command;

import java.util.Optional;

/**
 * Command execution behind {@code <run:command>}: dispatches the
 * evaluated line from the console like a command list entry and
 * yields nothing. Blank lines are silent no-ops; pure null warns
 * and skips like any other list entry.
 */
public final class TagRun {

    private TagRun() {
    }

    static String run(String tag, String args, TagContext context) {
        String line = args == null ? "" : args.strip();
        if (line.isEmpty()) {
            return "";
        }
        Optional<String> dispatchable = TagExpressions.dispatchableLine(line);
        if (dispatchable.isEmpty()) {
            return "";
        }
        if (TagExpressions.isPureNull(dispatchable.get())) {
            context.scope().warn("Skipping command that resolved to pure \"null\" at "
                    + context.provenance().describe() + ".");
            return "";
        }
        context.runCommand(dispatchable.get(), context.provenance().describe());
        return "";
    }
}
