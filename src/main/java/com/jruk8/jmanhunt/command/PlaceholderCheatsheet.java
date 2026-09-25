package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bulleted placeholder lines for command dialog bodies. Tags come from
 * the engine table ({@link CommandSyntax#knownTags}), so every tag the
 * engine resolves appears exactly once; signatures and notes mirror
 * the phrasing in docs/configuration/modifiers.md. The double arrow
 * reuses the codebase marker from help and list lines.
 */
public final class PlaceholderCheatsheet {

    private static final Map<String, String[]> ENTRIES = Map.of(
            "p", new String[]{"<p>", "participating player's name"},
            "random-mob", new String[]{"<random-mob>", "random living entity type"},
            "random-item", new String[]{"<random-item>", "random item material"},
            "random-num", new String[]{"<random-num:min,max>", "random whole number"},
            "random-pick", new String[]{"<random-pick:a,b>", "random entry from the list"},
            "random-player", new String[]{"<random-player>", "random participating player"},
            "all-players", new String[]{"<all-players>",
                    "once per participating player (:ROLE filters)"});

    private PlaceholderCheatsheet() {
    }

    /** One MiniMessage line per engine tag, in tag table order. */
    public static List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (String tag : CommandSyntax.knownTags()) {
            String[] entry = ENTRIES.get(tag);
            String signature = entry == null ? "<" + tag + ">" : entry[0];
            String note = entry == null ? "engine tag" : entry[1];
            lines.add("<green>»</green> <white>" + signature + "</white> <gray>" + note + "</gray>");
        }
        return lines;
    }
}
