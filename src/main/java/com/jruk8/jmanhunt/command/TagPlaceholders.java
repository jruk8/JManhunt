package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Optional;

/**
 * The {@code <placeholder>} tag alias: wraps its key in percent
 * signs and routes it through the same resolver as raw
 * {@code %...%} spans, so both spellings always agree. Needs an
 * executor player; unresolvable keys stay verbatim.
 */
public final class TagPlaceholders {

    private TagPlaceholders() {
    }

    /** Resolves {@code <placeholder:key>}. */
    static String resolve(String tag, String args, TagContext context) {
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() != 1) {
            context.scope().warn("Tag <placeholder> needs one key: " + tag);
            return "";
        }
        Optional<String> key = CommandPlaceholders.parsePickItem(parts.get(0));
        if (key.isEmpty() || key.get().isBlank()) {
            context.scope().warn("Tag <placeholder> needs a key: " + tag);
            return "";
        }
        String executor = context.scope().executorName();
        if (executor == null) {
            context.scope().warn("Tag <placeholder> needs an executor player: " + tag);
            return "";
        }
        return context.placeholders().resolve("%" + key.get().strip() + "%", executor);
    }
}
