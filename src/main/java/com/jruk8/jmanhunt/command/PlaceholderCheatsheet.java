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
            Map.entry("gmsg", new String[]{"<gmsg:text>", "alias of gmessage"}),
            Map.entry("pmessage",
                    new String[]{"<pmessage:player,text>", "message one player, returns empty"}),
            Map.entry("pmsg",
                    new String[]{"<pmsg:player,text>", "alias of pmessage"}),
            Map.entry("gsound",
                    new String[]{"<gsound:id,pitch,volume>", "sound for all, returns empty"}),
            Map.entry("psound",
                    new String[]{"<psound:player,id,pitch,volume>",
                            "sound for one player, returns empty"}),
            Map.entry("pstat",
                    new String[]{"<pstat:player,key>",
                            "health, hunger, max-health, mobs-killed, achievements"}),
            Map.entry("gstat", new String[]{"<gstat:key>", "duration seconds, daytime ticks"}),
            Map.entry("phasitem",
                    new String[]{"<phasitem:player,item,qty>",
                            "true when the player holds qty, 1 when omitted"}),
            Map.entry("gflag", new String[]{"<gflag:name,value>", "match flag, get when no value"}),
            Map.entry("gf", new String[]{"<gf:name,value>", "alias of gflag"}),
            Map.entry("pflag",
                    new String[]{"<pflag:name,value>", "executor flag, get when no value"}),
            Map.entry("pf", new String[]{"<pf:name,value>", "alias of pflag"}),
            Map.entry("lflag",
                    new String[]{"<lflag:name,value>", "run-local flag, get when no value"}),
            Map.entry("lf", new String[]{"<lf:name,value>", "alias of lflag"}),
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
            Map.entry("list.filter", new String[]{"<list.filter:list,cond>", "items where cond is true"}),
            Map.entry("list.reverse", new String[]{"<list.reverse:list>", "reversed copy"}),
            Map.entry("list.join", new String[]{"<list.join:list,sep>", "items joined with sep"}),
            Map.entry("list.slice", new String[]{"<list.slice:list,start,end>", "python-style slice"}),
            Map.entry("list.first", new String[]{"<list.first:list>", "first item, else null"}),
            Map.entry("list.last", new String[]{"<list.last:list>", "last item, else null"}),
            Map.entry("active-players", new String[]{"<active-players:ROLE>", "eligible names as a list"}),
            Map.entry("plocation", new String[]{"<plocation:player>", "player location list"}),
            Map.entry("prole", new String[]{"<prole:player>", "HUNTER or SPEEDRUNNER, else null"}),
            Map.entry("distance", new String[]{"<distance:loc1,loc2>", "3D distance, xyz only"}),
            Map.entry("overlap-players", new String[]{"<overlap-players:origin,role,radius,max>", "names near an origin"}),
            Map.entry("nearby-players", new String[]{"<nearby-players:player,role,radius,max>", "names near a player"}),
            Map.entry("pworld", new String[]{"<pworld:player>", "world alias: nether, end, else name"}),
            Map.entry("world", new String[]{"<world:player>", "alias of pworld"}),
            Map.entry("px", new String[]{"<px:player>", "player x coord"}),
            Map.entry("py", new String[]{"<py:player>", "player y coord"}),
            Map.entry("pz", new String[]{"<pz:player>", "player z coord"}),
            Map.entry("pyaw", new String[]{"<pyaw:player>", "player yaw"}),
            Map.entry("ppitch", new String[]{"<ppitch:player>", "player pitch"}),
            Map.entry("floor", new String[]{"<floor:2.7>", "2, rounds down"}),
            Map.entry("ceil", new String[]{"<ceil:2.3>", "3, rounds up"}),
            Map.entry("round", new String[]{"<round:2.5>", "3, rounds half up"}),
            Map.entry("abs", new String[]{"<abs:-4>", "4"}),
            Map.entry("sign", new String[]{"<sign:-4>", "-1, else 0 or 1"}),
            Map.entry("sqrt", new String[]{"<sqrt:9>", "3, square root"}),
            Map.entry("cbrt", new String[]{"<cbrt:-8>", "-2, cube root"}),
            Map.entry("root", new String[]{"<root:16,4>", "2, nth root"}),
            Map.entry("range", new String[]{"<range:1,5>", "[1, 2, 3, 4], python style"}),
            Map.entry("while", new String[]{"<while:1==1,...>", "repeats while true"}),
            Map.entry("for", new String[]{"<for:[a,b],...>", "walks a list item by item"}),
            Map.entry("i", new String[]{"<i>", "innermost for item, else null"}),
            Map.entry("def", new String[]{"<def:double,x+x,x>", "run-local function"}),
            Map.entry("run", new String[]{"<run:say hi>", "runs a console command, empty"}),
            Map.entry("format",
                    new String[]{"<format:string,params>", "fills {0}, {1}, silent when missing"}),
            Map.entry("rflag", new String[]{"<rflag:hunter,boost>", "flag of the named role"}),
            Map.entry("rmessage", new String[]{"<rmessage:hunter,push!>", "tells the named role"}),
            Map.entry("rmsg", new String[]{"<rmsg:hunter,push!>", "alias of rmessage"}),
            Map.entry("rsound", new String[]{"<rsound:hunter,id>", "plays for the named role"}),
            Map.entry("str.join", new String[]{"<str.join:list,sep>", "items joined with sep"}),
            Map.entry("str.split", new String[]{"<str.split:text,delim>", "literal split to list"}),
            Map.entry("str.lower", new String[]{"<str.lower:text>", "lowercase"}),
            Map.entry("str.upper", new String[]{"<str.upper:text>", "UPPERCASE"}),
            Map.entry("str.contains", new String[]{"<str.contains:text,needle>", "true when held"}),
            Map.entry("pcooldown", new String[]{"<pcooldown:player,key,seconds>", "true when ready, stamps"}),
            Map.entry("pcooldown.get", new String[]{"<pcooldown.get:player,key,seconds>", "seconds left, else 0"}),
            Map.entry("pcooldown.reset", new String[]{"<pcooldown.reset:player,key>", "clears, true"}),
            Map.entry("gcooldown", new String[]{"<gcooldown:key,seconds>", "true when ready, stamps"}),
            Map.entry("gcooldown.get", new String[]{"<gcooldown.get:key,seconds>", "seconds left, else 0"}),
            Map.entry("gcooldown.reset", new String[]{"<gcooldown.reset:key>", "clears, true"}),
            Map.entry("default", new String[]{"<default:value,fallback>", "fallback when blank/null"}),
            Map.entry("pheld", new String[]{"<pheld:player>", "main-hand material, else null"}));

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
        lines.add("<green>»</green> <white><pmessage:<p>,text></white> "
                + "<gray>message one player, parses to nothing</gray>");
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
