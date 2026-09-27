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

    private static final Map<String, String[]> ENTRIES = Map.ofEntries(
            Map.entry("p", new String[]{"<p>", "participating player's name"}),
            Map.entry("random-mob", new String[]{"<random-mob>", "random living entity type"}),
            Map.entry("random-item", new String[]{"<random-item>", "random item material"}),
            Map.entry("random-num", new String[]{"<random-num:min,max>", "random whole number"}),
            Map.entry("random-pick", new String[]{"<random-pick:a,b>", "random entry from the list"}),
            Map.entry("random-player", new String[]{"<random-player>", "random participating player"}),
            Map.entry("all-players", new String[]{"<all-players>",
                    "participant names as a list (:ROLE filters)"}),
            Map.entry("id", new String[]{"<id>", "this modifier id, or debuffs"}),
            Map.entry("min", new String[]{"<min:a,b>", "smaller number"}),
            Map.entry("max", new String[]{"<max:a,b>", "larger number"}),
            Map.entry("clamp", new String[]{"<clamp:x,low,high>", "number clamped to a range"}),
            Map.entry("if", new String[]{"<if:cond,then,else>", "branch on a condition"}),
            Map.entry("gmessage", new String[]{"<gmessage:text>", "broadcast text, returns empty"}),
            Map.entry("pmessage",
                    new String[]{"<pmessage:text>", "message the executor, returns empty"}),
            Map.entry("gsound",
                    new String[]{"<gsound:id,pitch,volume>", "sound for all, returns empty"}),
            Map.entry("psound",
                    new String[]{"<psound:id,pitch,volume>", "sound for executor, returns empty"}),
            Map.entry("pstat",
                    new String[]{"<pstat:player,key>", "health, hunger, mobs-killed, achievements"}),
            Map.entry("gstat", new String[]{"<gstat:key>", "duration seconds, daytime ticks"}),
            Map.entry("gflag", new String[]{"<gflag:name,value>", "match flag, get when no value"}),
            Map.entry("pflag",
                    new String[]{"<pflag:name,value>", "executor flag, get when no value"}),
            Map.entry("lflag",
                    new String[]{"<lflag:name,value>", "run-local flag, get when no value"}),
            Map.entry("placeholder",
                    new String[]{"<placeholder:key>", "placeholder alias, same as %key%"}),
            Map.entry("loseplayer",
                    new String[]{"<loseplayer:player,reason>", "eliminate a player, returns empty"}),
            Map.entry("win",
                    new String[]{"<win:ROLE,reason>", "end the match for a role, returns empty"}),
            Map.entry("args", new String[]{"<args:index>", "trigger event arg, 0 when bare"}));

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

    /**
     * Command dialog body: plain-command guidance plus a curated tag
     * shortlist and a docs pointer. The full tag list stays in
     * {@link #lines()} for other surfaces.
     */
    public static List<String> commandDialogLines() {
        List<String> lines = new ArrayList<>();
        lines.add("<gray>Type standard MC commands like "
                + "<white>give @p cooked_beef 8</white> normally. "
                + "JMHScript is for advanced modifiers.</gray>");
        lines.add("<gray>@p, @a, @s and @r work like vanilla and stay match-scoped; "
                + "JMHS tags parse to text, then the full text runs as one command.</gray>");
        lines.add("<green>»</green> <white><p></white> "
                + "<gray>the executing player, for inside other tags</gray>");
        lines.add("<green>»</green> <white><pmessage:text></white> "
                + "<gray>message the executor, parses to nothing</gray>");
        lines.add("<green>»</green> <white><random-player></white> "
                + "<gray>random participant, @r outside tags</gray>");
        lines.add("<green>»</green> <white><random-num:min,max></white> "
                + "<gray>random whole number</gray>");
        lines.add("<green>»</green> <white><random-pick:a,b></white> "
                + "<gray>random entry from the list</gray>");
        lines.add("<gray>For statistics, if conditionals, and placeholders, "
                + "read the Modifiers docs page.</gray>");
        return lines;
    }
}
