package com.jruk8.jmanhunt.command;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces tags and resolves relative coordinates (~) in modifier commands.
 * All command dispatch stays through the console sender, so tilde resolution
 * is done here rather than relying on the sender's location.
 *
 * <p>Tags evaluate from the inside out, so nesting works:
 * {@code <random-pick:coal <random-num:4,12>, diamond>} first rolls the
 * number, then picks an item. Unknown tags are left untouched.
 *
 * <p>Vanilla {@code @a} and {@code @r} selectors are implicitly converted to
 * match-scoped forms so a modifier can never leak into another running
 * match: {@code @a} becomes a hidden fan-out marker (expanded up front by
 * {@link #expandAllPlayers}), {@code @r} becomes
 * {@code <random-player>}. A {@code team=} argument on {@code @a[...]}
 * survives as a role filter; every other vanilla selector argument is
 * dropped. {@code @p} and {@code @s} become the executor tag {@code <p>}
 * for the same reason. Conversion only touches text outside tags: a bare
 * {@code @} selector inside tag arguments stays verbatim, so only JMHScript
 * tags nest.
 */
public final class CommandPlaceholders {
    private static final Pattern TILDE_PATTERN = Pattern.compile("~([+-]?\\d+(?:\\.\\d+)?)?");
    /** Innermost tag: an angle pair containing no further angle brackets. */
    private static final Pattern INNER_TAG = Pattern.compile("<([^<>]*)>");
    /** Bare @a/@r plus optional vanilla [...] args, never @admin-style words. */
    private static final Pattern SELECTOR_ALL = Pattern.compile("@a(?![A-Za-z0-9_])(\\[[^\\]]*\\])?");
    private static final Pattern SELECTOR_RANDOM = Pattern.compile("@r(?![A-Za-z0-9_])(\\[[^\\]]*\\])?");
    /** Bare @p/@s plus optional vanilla [...] args, never @server-style words. */
    private static final Pattern SELECTOR_SELF = Pattern.compile("@[ps](?![A-Za-z0-9_])(\\[[^\\]]*\\])?");
    /** team=HUNTER inside vanilla selector args. */
    private static final Pattern TEAM_ARGUMENT = Pattern.compile("(?i)(?:^|[,\\[])\\s*team\\s*=\\s*([^,\\]]+)");
    /**
     * Hidden fan-out token from {@code @a} conversion, with an optional
     * :TEAM filter. Never advertised: it stays out of the tag table.
     */
    private static final Pattern FANOUT_TOKEN = Pattern.compile("<all-fanout(?::([A-Za-z]+))?>");
    /** Maximal no-space runs for the bare math pass. */
    private static final Pattern MATH_TOKEN = Pattern.compile("\\S+");
    /** Safety cap for the inside-out evaluation loop. */
    private static final int MAX_TAG_PASSES = 25;
    /** Random-pick draws at most ten candidates (indexes 0-9). */
    private static final int MAX_PICK_ATTEMPTS = 10;

    // Lazily initialized to avoid IllegalStateException when the class is
    // loaded in a unit test without a running Bukkit server.
    private static volatile List<EntityType> spawnableLiving;
    private static volatile List<Material> items;

    private CommandPlaceholders() {
    }

    /**
     * Replaces all placeholders in a command string.
     *
     * @param command    the raw command from config
     * @param playerName the participating player's name, or null for console commands
     * @param x          the player's x coordinate for tilde resolution, or 0 if no player
     * @param y          the player's y coordinate for tilde resolution, or 0 if no player
     * @param z          the player's z coordinate for tilde resolution, or 0 if no player
     * @return the parsed command ready for console dispatch
     */
    public static String replace(String command, String playerName, double x, double y, double z) {
        return replace(command, playerName, x, y, z, TagContext.inert(
                ModifierTagScope.executor(playerName, message -> { })));
    }

    /**
     * Same, with a tag context for match tags, extended expressions,
     * and warning delivery. Call {@link #expandAllPlayers} first when
     * a command may fan out to the whole match; a surviving fan-out
     * marker here covers only the executor, while
     * {@code <all-players>} resolves to a name list. Bare no-space
     * math evaluates after tags; anything else stays verbatim.
     */
    public static String replace(String command, String playerName, double x, double y, double z,
            TagContext context) {
        context.resetStepBudget();
        String parsed = convertSelectors(EngineEscapes.substitute(command));
        try {
            parsed = evaluateTags(parsed, playerName, context);
        } catch (StackOverflowError exhausted) {
            // Small stacks can overflow before the step budget trips;
            // report it as the same loop limit, once per line.
            context.loopLimitExceeded("Stack exhausted evaluating " + command + " before "
                    + TagLoops.LOOP_LIMIT + " steps at " + context.provenance().describe());
            return "null";
        }
        parsed = applyMath(parsed, context);
        if (playerName != null) {
            parsed = resolveTildes(parsed, x, y, z);
        }
        return EngineEscapes.restore(parsed);
    }

    /**
     * Evaluates bare math tokens: maximal no-space runs that fully
     * parse. Fully quoted tokens and tokens with no operator
     * character skip the attempt; anything unparseable stays verbatim.
     */
    private static String applyMath(String command, TagContext context) {
        Matcher matcher = MATH_TOKEN.matcher(command);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group();
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(mathToken(token, context)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String mathToken(String token, TagContext context) {
        if (isQuotedToken(token)) {
            return token;
        }
        boolean candidate = false;
        for (int index = 0; index < token.length(); index++) {
            char letter = token.charAt(index);
            if (letter == '+' || letter == '-' || letter == '*' || letter == '/'
                    || letter == '%' || letter == '?') {
                candidate = true;
                break;
            }
        }
        if (!candidate) {
            return token;
        }
        try {
            Double value = TagMath.evaluate(token);
            return value == null ? token : TagMath.formatNumber(value);
        } catch (TagMath.SyntaxException syntax) {
            return token;
        } catch (TagMath.EvalException failed) {
            context.scope().warn("Math '" + token + "' " + failed.getMessage() + ", using 0");
            return "0";
        }
    }

    /** Fully quoted tokens are prose, never math: quotes protect them. */
    private static boolean isQuotedToken(String token) {
        if (token.length() < 2) {
            return false;
        }
        char first = token.charAt(0);
        return (first == '"' || first == '\'')
                && token.charAt(token.length() - 1) == first;
    }

    /**
     * Replaces the &lt;duration&gt; placeholder with the given delay in whole
     * seconds, floored so commands like `effect give` receive an int.
     * Pure for tests.
     */
    public static String withDuration(String command, double delaySeconds) {
        if (!command.contains("<duration>")) {
            return command;
        }
        return command.replace("<duration>", String.valueOf((long) Math.floor(delaySeconds)));
    }

    /**
     * Converts vanilla selectors outside tags to match-scoped forms:
     * {@code @a} becomes the hidden fan-out marker and {@code @r}
     * becomes {@code <random-player>}. A {@code team=} argument becomes
     * a role filter ({@code <all-fanout:HUNTER>}); other vanilla
     * arguments are dropped. {@code @p} and {@code @s} become the
     * executor tag {@code <p>}; their arguments are dropped. Text
     * inside {@code <...>} spans is left untouched, so only JMHScript
     * tags nest. Pure for tests.
     */
    static String convertSelectors(String command) {
        List<int[]> spans = topLevelTagSpans(command);
        if (spans.isEmpty()) {
            return convertSelectorRun(command);
        }
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (int[] span : spans) {
            out.append(convertSelectorRun(command.substring(cursor, span[0])));
            out.append(command, span[0], span[1]);
            cursor = span[1];
        }
        out.append(convertSelectorRun(command.substring(cursor)));
        return out.toString();
    }

    /** Selector conversion for one tag-free run. Pure for tests. */
    private static String convertSelectorRun(String run) {
        String converted = replaceSelector(run, SELECTOR_ALL, true);
        converted = replaceSelector(converted, SELECTOR_RANDOM, false);
        return replaceSelfSelector(converted);
    }

    /**
     * Top-level {@code <...>} spans as [start, end) pairs. Angle
     * brackets inside quotes do not count, and a {@code <} with no
     * matching close is prose, not a span. Pure for tests.
     */
    static List<int[]> topLevelTagSpans(String command) {
        List<int[]> spans = new ArrayList<>();
        int index = 0;
        while (index < command.length()) {
            int open = command.indexOf('<', index);
            if (open < 0) {
                return spans;
            }
            int close = spanClose(command, open);
            if (close < 0) {
                index = open + 1;
            } else {
                spans.add(new int[]{open, close + 1});
                index = close + 1;
            }
        }
        return spans;
    }

    /** Matching close of the tag opening at {@code open}; -1 when unclosed. Shared with the pre-pass. */
    static int spanClose(String command, int open) {
        int depth = 0;
        char quote = 0;
        for (int index = open; index < command.length(); index++) {
            char letter = command.charAt(index);
            if (quote != 0) {
                if (letter == quote) {
                    quote = 0;
                }
            } else if (letter == '"' || letter == '\'') {
                quote = letter;
            } else if (letter == '<') {
                depth++;
            } else if (letter == '>') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static String replaceSelfSelector(String command) {
        Matcher matcher = SELECTOR_SELF.matcher(command);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement("<p>"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceSelector(String command, Pattern pattern, boolean keepTeam) {
        Matcher matcher = pattern.matcher(command);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = "<all-fanout>";
            if (!keepTeam) {
                replacement = "<random-player>";
            } else if (matcher.group(1) != null) {
                Matcher team = TEAM_ARGUMENT.matcher(matcher.group(1));
                if (team.find()) {
                    replacement = "<all-fanout:" + team.group(1).trim().toUpperCase(Locale.ROOT) + ">";
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Fans a command out to one copy per in-match participant when it holds
     * the hidden {@code @a} fan-out marker. With nobody to cover, warns and
     * returns no commands so nothing leaks outside the match. The
     * {@code <all-players>} tag is NOT expanded here: it resolves to a name
     * list during tag evaluation instead. Pure for tests.
     */
    public static List<String> expandAllPlayers(String command, ModifierTagScope scope) {
        String substituted = EngineEscapes.substitute(command);
        String converted = convertSelectors(substituted);
        Matcher matcher = FANOUT_TOKEN.matcher(converted);
        if (!matcher.find()) {
            return List.of(substituted);
        }
        String team = matcher.group(1);
        List<String> names = scope.participantNames(team);
        if (names.isEmpty()) {
            scope.warn("Skipping command with @a"
                    + (team == null ? "" : "[team=" + team + "]")
                    + " because the match has no covered players: " + command);
            return List.of();
        }
        List<String> expanded = new ArrayList<>(names.size());
        for (String name : names) {
            expanded.add(matcher.replaceAll(Matcher.quoteReplacement(name)));
        }
        return expanded;
    }

    /**
     * Pre-resolves random-mob and random-item tags to activation-shared rolls
     * so PER_INVOKE sees one mob and one item across every executor. Rolls
     * come from the given roller and are cached in sharedDraws (one cache per
     * modifier activation, shared across every command list); lines without
     * those tags pass through untouched, and every other tag still resolves
     * per executor downstream. Matching mirrors tag evaluation: innermost
     * spans whose name (before any colon, trimmed, case-blind) is random-mob
     * or random-item. Pure apart from the roller, so unit tests cover it
     * with fixed draws.
     */
    public static List<String> preresolveSharedRandoms(List<String> lines, Map<String, String> sharedDraws,
            java.util.function.Function<String, String> roller) {
        boolean wanted = false;
        for (String line : lines) {
            if (containsSharedRandom(line)) {
                wanted = true;
                break;
            }
        }
        if (!wanted) {
            return lines;
        }
        List<String> fixed = new ArrayList<>(lines.size());
        for (String line : lines) {
            fixed.add(preresolveLine(line, sharedDraws, roller));
        }
        return fixed;
    }

    /** Draws one shared roll for a random tag name. */
    public static String rollSharedRandom(String name) {
        return "random-mob".equals(name) ? randomMob() : randomItem();
    }

    private static boolean containsSharedRandom(String line) {
        Matcher matcher = INNER_TAG.matcher(line);
        while (matcher.find()) {
            if (isSharedRandom(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    private static String preresolveLine(String line, Map<String, String> sharedDraws,
            java.util.function.Function<String, String> roller) {
        String current = line;
        for (int pass = 0; pass < MAX_TAG_PASSES; pass++) {
            Matcher matcher = INNER_TAG.matcher(current);
            StringBuffer result = new StringBuffer();
            boolean changed = false;
            while (matcher.find()) {
                String name = tagName(matcher.group(1));
                String draw = sharedDraws.get(name);
                if (draw == null && isSharedRandom(matcher.group(1))) {
                    draw = roller.apply(name);
                    sharedDraws.put(name, draw);
                }
                if (draw != null) {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(draw));
                    changed = true;
                } else {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                }
            }
            matcher.appendTail(result);
            current = result.toString();
            if (!changed) {
                return current;
            }
        }
        return current;
    }

    /** Tag name: before any colon, trimmed, lowercase. Mirrors tag evaluation. */
    private static String tagName(String body) {
        int separator = body.indexOf(':');
        String name = separator < 0 ? body : body.substring(0, separator);
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isSharedRandom(String body) {
        String name = tagName(body);
        return "random-mob".equals(name) || "random-item".equals(name);
    }

    /**
     * Inside-out tag evaluation; unknown tags survive untouched. Defs
     * resolve first each pass so their bodies store verbatim instead
     * of pre-resolving. Loops go next so their bodies (with
     * {@code <i>} and per-iteration conditions) stay frozen for the
     * loop protocol instead of pre-resolving. If-tags go next because
     * their conditions may hold bare {@code < > <= >=} that the plain
     * innermost scan cannot see. Mutating list tags with flag
     * references resolve last so they can write back before the
     * reference itself resolves to a value. Loops, ifs, and filters
     * walk left to right with the strict-prefix plain window before
     * each span resolved first, so earlier writes are visible later
     * on the line; a final sweep resolves the remaining plain tags.
     */
    private static String evaluateTags(String command, String playerName, TagContext context) {
        String current = command;
        TagLoops.Evaluator eval = fragment -> evaluateTags(fragment, playerName, context);
        for (int pass = 0; pass < MAX_TAG_PASSES; pass++) {
            String stepped = TagPrePass.resolveDefSpans(current, context);
            stepped = walkLoops(stepped, playerName, context, eval);
            stepped = walkFilters(stepped, playerName, context, eval);
            stepped = walkIfs(stepped, playerName, context, eval);
            stepped = TagPrePass.resolveListSpans(stepped, context, eval);
            boolean changed = !stepped.equals(current);
            current = stepped;
            String swept = resolvePlainWindow(current, 0, TagPrePass.protectionSpans(current),
                    playerName, context, eval);
            if (!swept.equals(current)) {
                changed = true;
            }
            current = swept;
            if (!changed) {
                return current;
            }
        }
        context.scope().warn(
                "Stopped evaluating nested tags after " + MAX_TAG_PASSES + " passes: " + command);
        return current;
    }

    /** Loops walk with prefix windows over the current text. */
    private static String walkLoops(String command, String playerName, TagContext context,
            TagLoops.Evaluator eval) {
        List<int[]> protections = TagPrePass.protectionSpans(command);
        return TagPrePass.resolveLoopSpans(command, context, eval,
                (gap, base) -> resolvePlainWindow(gap, base, protections, playerName,
                        context, eval));
    }

    /** Ifs walk with prefix windows over the current text. */
    private static String walkIfs(String command, String playerName, TagContext context,
            TagLoops.Evaluator eval) {
        List<int[]> protections = TagPrePass.protectionSpans(command);
        return TagPrePass.resolveIfSpans(command, context, eval,
                (gap, base) -> resolvePlainWindow(gap, base, protections, playerName,
                        context, eval));
    }

    /** Filters walk with prefix windows over the current text. */
    private static String walkFilters(String command, String playerName, TagContext context,
            TagLoops.Evaluator eval) {
        List<int[]> protections = TagPrePass.protectionSpans(command);
        return TagPrePass.resolveFilterSpans(command, context, eval,
                (gap, base) -> resolvePlainWindow(gap, base, protections, playerName,
                        context, eval));
    }

    /**
     * Resolves innermost tags in the window exactly like the plain
     * scan, except matches covered by a protection span pass through.
     * The base offset maps window positions onto the walked text.
     */
    static String resolvePlainWindow(String text, int baseOffset, List<int[]> protections,
            String playerName, TagContext context, TagLoops.Evaluator eval) {
        Matcher matcher = INNER_TAG.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        StringBuffer result = new StringBuffer();
        do {
            String match = matcher.group(0);
            String resolved = match;
            if (!TagPrePass.covers(protections, baseOffset + matcher.start(),
                    baseOffset + matcher.end())) {
                resolved = resolveTag(matcher.group(1), playerName, context, eval);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolved));
        } while (matcher.find());
        matcher.appendTail(result);
        return result.toString();
    }

    private static String resolveTag(String body, String playerName, TagContext context,
            TagLoops.Evaluator eval) {
        ModifierTagScope scope = context.scope();
        String tag = "<" + body + ">";
        int separator = body.indexOf(':');
        String name = (separator < 0 ? body : body.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
        String args = separator < 0 ? "" : body.substring(separator + 1);
        return switch (name) {
            case "p" -> playerName != null ? playerName : tag;
            case "random-mob" -> randomMob();
            case "random-item" -> randomItem();
            case "random-num" -> randomNumber(args, scope, tag);
            case "random-pick" -> randomPick(args, scope, tag);
            case "random-player", "all-players", "all-fanout" -> scopeTag(tag, name, args, playerName, context);
            // Handled separately by withDuration before evaluation.
            case "duration" -> tag;
            case "id" -> context.containerId();
            case "i" -> context.loopItem().orElse("null");
            case "pstat" -> TagStats.player(tag, args, context); case "gstat" -> TagStats.global(tag, args, context);
            case "phasitem" -> TagItems.hasItem(tag, args, context);
            case "gflag", "gf" -> TagFlags.global(tag, args, context);
            case "pflag", "pf" -> TagFlags.player(tag, args, context);
            case "lflag", "lf" -> TagFlags.local(tag, args, context);
            case "rflag" -> TagFlags.role(tag, args, context);
            case "placeholder" -> TagPlaceholders.resolve(tag, args, context);
            case "min", "max", "clamp", "root" -> TagExpressions.minMaxClamp(tag, name, args, context);
            case "if" -> TagExpressions.ifEval(tag, args, context);
            case "gmessage", "gmsg", "pmessage", "pmsg", "rmessage", "rmsg" ->
                    TagSinks.message(tag, name, args, context);
            case "gsound", "psound", "rsound" -> TagSinks.sound(tag, name, args, context);
            case "loseplayer" -> TagExpressions.loseplayer(tag, args, context);
            case "win" -> TagExpressions.win(tag, args, context);
            case "args" -> TagArgs.resolve(tag, args, context);
            case "list.append", "list.get", "list.set", "list.remove", "list.contains", "list.clear",
                    "list.pop", "len", "list.shuffle", "list.filter", "list.reverse", "list.join",
                    "list.slice", "list.first", "list.last" ->
                    TagLists.resolve(tag, name, args, context, eval);
            case "str.join", "str.split", "str.lower", "str.upper", "str.contains" ->
                    TagStrings.resolve(tag, name, args, context);
            case "pcooldown", "pcooldown.get", "pcooldown.reset", "gcooldown", "gcooldown.get",
                    "gcooldown.reset" -> TagCooldowns.resolve(tag, name, args, context);
            case "default" -> TagExpressions.defaultValue(tag, args, context);
            case "pheld" -> TagItems.held(tag, args, context);
            case "range" -> TagLists.range(tag, args, context);
            case "active-players" -> TagRoster.activePlayers(tag, args, context);
            case "plocation", "ploc" -> TagLocations.plocation(tag, name, args, context);
            case "vec.add", "vec.sub", "vec.mult", "vec.normalize", "vec.sqrdist", "vec.dist",
                    "vec.dot", "vec.cross", "loc.shift", "pdir" ->
                    TagVectors.resolve(tag, name, args, context);
            case "pstate", "pstandingon", "ptitle", "pslot" ->
                    TagPlayers.resolve(tag, name, args, context);
            case "prole" -> TagRoster.role(tag, args, context);
            case "overlap-players" -> TagLocations.overlapPlayers(tag, args, context);
            case "nearby-players" -> TagLocations.nearbyPlayers(tag, args, playerName, context);
            case "pworld", "world" -> TagLocations.playerWorld(tag, name, args, context);
            case "px", "py", "pz", "pyaw", "ppitch" ->
                    TagLocations.playerCoord(tag, name, args, context);
            case "distance" -> TagLocations.distance(tag, args, context);
            case "floor", "ceil", "round", "abs", "sign", "sqrt", "cbrt" ->
                    TagExpressions.mathUnary(tag, name, args, context);
            case "run" -> TagRun.run(tag, args, context); case "format" -> TagFormat.format(tag, args, context);
            default -> TagFunctions.call(tag, name, args, context, eval);
        };
    }

    /**
     * Match-scope tags: one random participant, the participant name
     * list, or the hidden {@code @a} fan-out marker (executor-only
     * dispatch covers the executor; the modifier path expands it up
     * front instead).
     */
    private static String scopeTag(String tag, String name, String args, String playerName,
            TagContext context) {
        ModifierTagScope scope = context.scope();
        return switch (name) {
            case "random-player" -> scope.randomParticipant()
                    .or(() -> Optional.ofNullable(playerName))
                    .orElseGet(() -> {
                        scope.warn("Skipping <random-player> with no players in scope: " + tag);
                        return tag;
                    });
            case "all-players" -> {
                List<String> names = scope.participantNames(args.isBlank() ? null : args.strip());
                if (names.isEmpty()) {
                    scope.warn("Skipping <all-players> with no players in scope, using []: " + tag);
                }
                yield TagLists.format(names);
            }
            default -> {
                scope.warn("@a outside match fan-out covers only the executor.");
                yield playerName != null ? playerName : tag;
            }
        };
    }

    /**
     * Rolls an integer between a and b, inclusive; order does not matter.
     * Unparseable input warns and yields 0 so dispatch keeps a valid int.
     */
    static String randomNumber(String args, ModifierTagScope scope, String tag) {
        List<String> bounds = splitPickArgs(args == null ? "" : args);
        if (bounds.size() == 2) {
            Optional<String> first = parsePickItem(bounds.get(0));
            Optional<String> second = parsePickItem(bounds.get(1));
            if (first.isPresent() && second.isPresent()) {
                try {
                    long firstBound = Long.parseLong(first.get().strip());
                    long secondBound = Long.parseLong(second.get().strip());
                    long low = Math.min(firstBound, secondBound);
                    long high = Math.max(firstBound, secondBound);
                    long span = high - low + 1;
                    if (span > 0) {
                        long rolled = low + nextLong(scope, span);
                        if (rolled >= Integer.MIN_VALUE && rolled <= Integer.MAX_VALUE) {
                            return String.valueOf(rolled);
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // Falls through to the warning below.
                }
            }
        }
        scope.warn("Invalid <random-num:a,b> tag, using 0: " + tag);
        return "0";
    }

    /** Bounded long draw from the scope's random source. */
    private static long nextLong(ModifierTagScope scope, long bound) {
        long drawn = scope.random().nextLong() >>> 1;
        return drawn % bound;
    }

    /**
     * Picks one item from a comma list. Items may be bare, double-quoted, or
     * single-quoted, and may hold spaces; spaces after a comma are skipped.
     * Invalid items warn with the item in parentheses plus its index while
     * another candidate is tried, up to ten draws or until every unique item
     * is exhausted. Total failure warns and yields an empty string.
     */
    static String randomPick(String args, ModifierTagScope scope, String tag) {
        if (args == null || args.isBlank()) {
            scope.warn("Empty <random-pick:...> tag: " + tag);
            return "";
        }
        List<String> rawItems = splitPickArgs(args);
        Set<String> seen = new LinkedHashSet<>();
        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < rawItems.size(); index++) {
            if (seen.add(rawItems.get(index))) {
                candidates.add(index);
            }
        }
        int attempts = Math.min(MAX_PICK_ATTEMPTS, candidates.size());
        for (int draw = 0; draw < attempts; draw++) {
            int slot = scope.random().nextInt(candidates.size());
            int index = candidates.remove(slot);
            Optional<String> parsed = parsePickItem(rawItems.get(index));
            if (parsed.isPresent()) {
                return parsed.get();
            }
            scope.warn("Skipping invalid <random-pick> item (" + rawItems.get(index).trim()
                    + ") at index " + index + " in: " + tag);
        }
        scope.warn("No valid <random-pick> item, using an empty string: " + tag);
        return "";
    }

    /** Splits pick args on commas outside single or double quotes. */
    static List<String> splitPickArgs(String args) {
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int index = 0; index < args.length(); index++) {
            char letter = args.charAt(index);
            if (quote != 0) {
                current.append(letter);
                if (letter == quote) {
                    quote = 0;
                }
            } else if (letter == '"' || letter == '\'') {
                quote = letter;
                current.append(letter);
            } else if (letter == ',') {
                items.add(current.toString());
                current.setLength(0);
            } else {
                current.append(letter);
            }
        }
        items.add(current.toString());
        return items;
    }

    /**
     * Validates one pick item: either bare with no quote characters, or one
     * balanced quoted string and nothing else. Empty when illegal.
     */
    static Optional<String> parsePickItem(String raw) {
        String item = raw.trim();
        if (item.isEmpty()) {
            return Optional.empty();
        }
        char first = item.charAt(0);
        if (first != '"' && first != '\'') {
            return item.indexOf('"') < 0 && item.indexOf('\'') < 0
                    ? Optional.of(item)
                    : Optional.empty();
        }
        if (item.length() < 2 || item.charAt(item.length() - 1) != first) {
            return Optional.empty();
        }
        String inner = item.substring(1, item.length() - 1);
        return inner.indexOf(first) < 0 ? Optional.of(inner) : Optional.empty();
    }

    /**
     * Resolves relative coordinates (~, ~5, ~-3) to absolute coordinates using
     * the given reference position. Tildes cycle through x, y, z in order.
     * This is package-private for testing.
     */
    static String resolveTildes(String command, double x, double y, double z) {
        Matcher matcher = TILDE_PATTERN.matcher(command);
        StringBuilder result = new StringBuilder();
        int[] index = {0};
        double[] coords = {x, y, z};
        while (matcher.find()) {
            String offsetStr = matcher.group(1);
            double offset = offsetStr == null ? 0.0 : Double.parseDouble(offsetStr);
            double base = coords[index[0] % 3];
            double resolved = base + offset;
            // Use integer string when the value is a whole number, otherwise
            // keep the decimal to avoid ".0" in coordinates.
            String replacement = resolved == Math.floor(resolved)
                    ? String.valueOf((long) resolved)
                    : String.valueOf(resolved);
            matcher.appendReplacement(result, replacement);
            index[0]++;
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Returns the list of spawnable living entity types. Package-private for testing. */
    static List<EntityType> spawnableLivingEntities() {
        if (spawnableLiving == null) {
            synchronized (CommandPlaceholders.class) {
                if (spawnableLiving == null) {
                    spawnableLiving = Arrays.stream(EntityType.values())
                            .filter(EntityType::isSpawnable)
                            .filter(EntityType::isAlive)
                            .toList();
                }
            }
        }
        return spawnableLiving;
    }

    /** Returns the list of item materials. Package-private for testing. */
    static List<Material> items() {
        if (items == null) {
            synchronized (CommandPlaceholders.class) {
                if (items == null) {
                    items = Arrays.stream(Material.values())
                            .filter(Material::isItem)
                            .toList();
                }
            }
        }
        return items;
    }

    private static String randomMob() {
        List<EntityType> mobs = spawnableLivingEntities();
        return mobs.get(ThreadLocalRandom.current().nextInt(mobs.size())).name().toLowerCase(Locale.ROOT);
    }

    private static String randomItem() {
        List<Material> itemList = items();
        return itemList.get(ThreadLocalRandom.current().nextInt(itemList.size())).name().toLowerCase(Locale.ROOT);
    }
}
