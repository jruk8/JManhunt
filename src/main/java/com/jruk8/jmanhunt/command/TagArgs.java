package com.jruk8.jmanhunt.command;

/**
 * Event args behind {@code <args:index>}: per-trigger values (kill
 * victim, portal worlds, interval seconds, ...) carried on the
 * {@link TagContext} of the firing dispatch.
 */
public final class TagArgs {

    private TagArgs() {
    }

    /**
     * Resolves one event arg by index. Bare {@code <args>} reads
     * index 0; a non-numeric index warns plus {@code "null"};
     * missing indexes quietly resolve to {@code "null"}.
     */
    static String resolve(String tag, String args, TagContext context) {
        int index = 0;
        if (args != null && !args.isBlank()) {
            try {
                index = Integer.parseInt(args.strip());
            } catch (NumberFormatException unmatched) {
                context.scope().warn("Tag <args> needs a whole index: " + tag);
                return "null";
            }
        }
        if (index < 0 || index >= context.eventArgs().size()) {
            return "null";
        }
        return context.eventArgs().get(index);
    }
}
