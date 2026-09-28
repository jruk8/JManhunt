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
            Map.entry("args", new String[]{"<args:index>", "trigger event arg, 0 when bare"}),
            Map.entry("list.append", new String[]{"<list.append:list,x>", "append x, store back, empty"}),
            Map.entry("list.get", new String[]{"<list.get:list,index>", "item at index, null when missing"}),
            Map.entry("list.set", new String[]{"<list.set:list,index,x>", "set index, store back, empty"}),
            Map.entry("list.remove", new String[]{"<list.remove:list,x>", "remove first x, true/false"}),
            Map.entry("list.contains", new String[]{"<list.contains:list,x>", "true when x is an item"}),
            Map.entry("list.clear", new String[]{"<list.clear:list>", "empty the list, store back"}),
            Map.entry("list.pop", new String[]{"<list.pop:list>", "remove and return first item"}),
            Map.entry("len", new String[]{"<len:list>", "item count, 0 when no list"}),
            Map.entry("list.shuffle", new String[]{"<list.shuffle:list>", "shuffle, store back, empty"}),
            Map.entry("active-players", new String[]{"<active-players:ROLE>", "eligible names as a list"}),
            Map.entry("plocation", new String[]{"<plocation:player>", "player location list"}),
            Map.entry("prole", new String[]{"<prole:player>", "HUNTER or SPEEDRUNNER, else null"}),
            Map.entry("distance", new String[]{"<distance:loc1,loc2>", "3D distance, xyz only"}),
            Map.entry("floor", new String[]{"<floor:2.7>", "2, rounds down"}),
            Map.entry("ceil", new String[]{"<ceil:2.3>", "3, rounds up"}),
            Map.entry("round", new String[]{"<round:2.5>", "3, rounds half up"}),
            Map.entry("abs", new String[]{"<abs:-4>", "4"}),
            Map.entry("sign", new String[]{"<sign:-4>", "-1, else 0 or 1"}),
            Map.entry("range", new String[]{"<range:1,5>", "[1, 2, 3, 4], python style"}),
            Map.entry("while", new String[]{"<while:1==1,...>", "repeats while true"}),
            Map.entry("for", new String[]{"<for:[a,b],...>", "walks a list item by item"}),
            Map.entry("i", new String[]{"<i>", "innermost for item, else null"}),
            Map.entry("rflag", new String[]{"<rflag:name>", "flag of the executor role"}),
            Map.entry("rmessage", new String[]{"<rmessage:text>", "tells the executor role"}),
            Map.entry("rsound", new String[]{"<rsound:id>", "plays for the executor role"}));

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
