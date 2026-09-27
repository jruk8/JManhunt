package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Roster tags behind {@code <active-players>} and {@code <prole>}:
 * match membership reads through the context roster. Both need a
 * live match; role filters are case-insensitive.
 */
public final class TagRoster {

    private TagRoster() {
    }

    /**
     * {@code <active-players:ROLE>}: alphabetical eligible names for
     * HUNTER or SPEEDRUNNER as a canonical list, {@code []} when
     * none (empty is data, never a warning).
     */
    static String activePlayers(String tag, String args, TagContext context) {
        if (context.matchId() == TagContext.NO_MATCH) {
            context.scope().warn("Tag <active-players> needs a live match: " + tag);
            return "null";
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 1) {
            context.scope().warn("Tag <active-players> needs one role like "
                    + "<active-players:HUNTER>: " + tag);
            return "null";
        }
        Optional<String> role = CommandPlaceholders.parsePickItem(parts.get(0));
        if (role.isEmpty() || role.get().isBlank()) {
            context.scope().warn("Tag <active-players> needs one role like "
                    + "<active-players:HUNTER>: " + tag);
            return "null";
        }
        String upper = role.get().strip().toUpperCase(Locale.ROOT);
        if (!upper.equals("HUNTER") && !upper.equals("SPEEDRUNNER")) {
            context.scope().warn("Tag <active-players> needs HUNTER or SPEEDRUNNER: " + tag);
            return "null";
        }
        return TagLists.format(context.roster().activePlayers(upper));
    }

    /**
     * {@code <prole:player>}: uppercase HUNTER or SPEEDRUNNER for a
     * match assignee, active or not. Unknown players warn plus
     * {@code "null"}; other roles resolve {@code "null"} silently.
     */
    static String role(String tag, String args, TagContext context) {
        if (context.matchId() == TagContext.NO_MATCH) {
            context.scope().warn("Tag <prole> needs a live match: " + tag);
            return "null";
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 1) {
            context.scope().warn("Tag <prole> needs a player like <prole:Steve>: " + tag);
            return "null";
        }
        Optional<String> name = CommandPlaceholders.parsePickItem(parts.get(0));
        if (name.isEmpty() || name.get().isBlank()) {
            context.scope().warn("Tag <prole> needs a player like <prole:Steve>: " + tag);
            return "null";
        }
        Optional<String> role = context.roster().roleOf(name.get().strip());
        if (role.isEmpty()) {
            context.scope().warn("Tag <prole> found no match player '"
                    + name.get().strip() + "': " + tag);
            return "null";
        }
        String upper = role.get().toUpperCase(Locale.ROOT);
        if (!upper.equals("HUNTER") && !upper.equals("SPEEDRUNNER")) {
            return "null";
        }
        return upper;
    }
}
