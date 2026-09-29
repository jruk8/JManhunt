package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static validation for typed modifier commands, shared by the creator CLI
 * and the creator GUI command editors. Fatal problems block a save while
 * warnings only flag. Names, arg shapes, and the inside-out innermost tag
 * scan mirror {@link CommandPlaceholders} so validation never disagrees
 * with what dispatch actually resolves.
 */
public final class CommandSyntax {
    private static final Pattern INNER_TAG = Pattern.compile("<([^<>]*)>");
    private static final Pattern LETTERS_ONLY = Pattern.compile("[A-Za-z]+");
    private static final Set<String> KNOWN_TAGS = Set.copyOf(knownTags());

    private CommandSyntax() {
    }

    /**
     * Modifier engine tags in cheatsheet order. The single tag table:
     * validation and the dialog cheatsheet both read from here, so a
     * new tag can never validate but stay undocumented. Duration is
     * compass-only (substituted by withDuration on that path alone),
     * so it stays out and warns as unknown on modifiers.
     */
    public static List<String> knownTags() {
        return List.of("p", "random-mob", "random-item", "random-num",
                "random-pick", "random-player", "all-players", "id", "min",
                "max", "clamp", "if", "gmessage", "pmessage", "gsound", "psound",
                "pstat", "gstat", "phasitem", "gflag", "gf", "pflag", "pf", "lflag", "lf", "placeholder",
                "rflag", "rmessage", "rsound",
                "loseplayer", "win", "args", "list.append", "list.get", "list.set", "list.remove",
                "list.contains", "list.clear", "list.pop", "len", "list.shuffle", "range",
                "active-players", "plocation", "prole", "distance",
                "floor", "ceil", "round", "abs", "sign", "sqrt", "cbrt", "root",
                "while", "for", "i", "def", "run");
    }

    /**
     * First fatal problem with a typed command: blank input, a stray
     * exit, unbalanced angle brackets, or malformed tag args. Empty
     * means the command is safe to save.
     */
    public static Optional<String> error(String command) {
        if (command == null || command.isBlank()) {
            return Optional.of("Command must not be empty.");
        }
        if (TagExpressions.isExitMisuse(command)) {
            return Optional.of("'exit' must stand alone on its line.");
        }
        int depth = 0;
        String balanced = withoutIfSpans(command);
        for (int index = 0; index < balanced.length(); index++) {
            char letter = balanced.charAt(index);
            if (letter == '<') {
                depth++;
            } else if (letter == '>') {
                if (depth == 0) {
                    return Optional.of("Unmatched '>' in command.");
                }
                depth--;
            }
        }
        if (depth > 0) {
            return Optional.of("Unclosed '<' tag in command.");
        }
        for (String body : tagBodies(command)) {
            Optional<String> tagError = tagError(body);
            if (tagError.isPresent()) {
                return tagError;
            }
        }
        return Optional.empty();
    }

    /**
     * Tag bodies inside-out, innermost spans first, so a nested tag no
     * longer hides its outer tag from validation. If-spans collapse
     * first (mirroring dispatch) since their conditions may hold bare
     * {@code < > <= >=}. Pass cap mirrors dispatch; anything deeper
     * stays for runtime to report.
     */
    static List<String> tagBodies(String command) {
        List<String> bodies = new ArrayList<>();
        String current = command;
        for (int pass = 0; pass < 25; pass++) {
            String collapsed = collapseIfSpans(current, bodies);
            if (!collapsed.equals(current)) {
                current = collapsed;
                continue;
            }
            Matcher matcher = INNER_TAG.matcher(current);
            if (!matcher.find()) {
                return bodies;
            }
            StringBuffer stripped = new StringBuffer();
            do {
                bodies.add(matcher.group(1));
                matcher.appendReplacement(stripped, "?");
            } while (matcher.find());
            matcher.appendTail(stripped);
            current = stripped.toString();
        }
        return bodies;
    }

    /**
     * If-spans blanked for the bracket check: their quoted conditions
     * may hold {@code < > <= >=} that are content, not structure.
     * Loops to a fixpoint so nested ifs collapse inside out. The
     * check itself stays quote-blind to mirror dispatch.
     */
    private static String withoutIfSpans(String command) {
        String current = command;
        for (int pass = 0; pass < 25; pass++) {
            String collapsed = collapseIfSpans(current, new ArrayList<>());
            if (collapsed.equals(current)) {
                return current;
            }
            current = collapsed;
        }
        return current;
    }

    /** Collects nested bodies plus one collapsed body per ready if-span. */
    private static String collapseIfSpans(String current, List<String> bodies) {
        List<TagExpressions.IfSpan> ready = new ArrayList<>();
        for (TagExpressions.IfSpan span : TagExpressions.findIfSpans(current)) {
            if (!TagExpressions.hasNestedIf(span.args())) {
                ready.add(span);
            }
        }
        if (ready.isEmpty()) {
            return current;
        }
        ready.sort((first, second) -> Integer.compare(second.start(), first.start()));
        StringBuilder result = new StringBuilder(current);
        for (TagExpressions.IfSpan span : ready) {
            bodies.addAll(tagBodies(span.args()));
            result.replace(span.start(), span.end(), "?");
            bodies.add("if:" + collapseTags(span.args()));
        }
        return result.toString();
    }

    /**
     * Blanks unquoted innermost tags with {@code ?}, leaving quoted
     * comparisons (and quoted nested tags) intact for the if checks.
     */
    private static String collapseTags(String text) {
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int open = nextUnquoted(text, index, '<');
            if (open < 0) {
                out.append(text.substring(index));
                return out.toString();
            }
            int close = nextUnquoted(text, open + 1, '>');
            int nested = nextUnquoted(text, open + 1, '<');
            if (close < 0 || (nested >= 0 && nested < close)) {
                out.append(text, index, open + 1);
                index = open + 1;
            } else {
                out.append(text, index, open).append('?');
                index = close + 1;
            }
        }
        return out.toString();
    }

    private static int nextUnquoted(String text, int from, char target) {
        char quote = 0;
        for (int index = from; index < text.length(); index++) {
            char letter = text.charAt(index);
            if (quote != 0) {
                if (letter == quote) {
                    quote = 0;
                }
            } else if (letter == '"' || letter == '\'') {
                quote = letter;
            } else if (letter == target) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Non-fatal flags: unknown tags (dispatch leaves them untouched),
     * invalid random-pick items (dispatch skips them), and non-letter
     * all-players filters (dispatch falls back to the executor).
     */
    public static List<String> warnings(String command) {
        return warnings(command, Set.of());
    }

    /** Warnings for one line; knownFunctions holds lowercase def names. */
    public static List<String> warnings(String command, Collection<String> knownFunctions) {
        List<String> found = new ArrayList<>();
        if (command == null || command.isBlank() || error(command).isPresent()) {
            return found;
        }
        for (String body : tagBodies(command)) {
            collectWarnings(body, found, knownFunctions);
        }
        return found;
    }

    private static Optional<String> tagError(String body) {
        int separator = body.indexOf(':');
        String name = (separator < 0 ? body : body.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
        String args = separator < 0 ? null : body.substring(separator + 1);
        return switch (name) {
            case "random-num" -> randomNumberError(args);
            case "random-pick" -> randomPickError(args);
            case "id", "i" -> noArgsError(name, args);
            case "min", "max" -> arityError(name, args, 2, "two numbers");
            case "clamp" -> arityError(name, args, 3, "a value plus low and high");
            case "gmessage" -> arityError(name, args, 1, "one text");
            case "pmessage" -> arityError(name, args, 2, "a player and a text");
            case "rmessage" -> RoleTagSyntax.messageError(name, args);
            case "gsound" -> soundError(name, args);
            case "psound" -> TagSinks.playerSoundError(name, args);
            case "rsound" -> RoleTagSyntax.soundError(name, args);
            case "if" -> ifError(args);
            case "pstat" -> statError(name, args, 1, TagStats.PSTAT_KEYS);
            case "gstat" -> statError(name, args, 0, TagStats.GSTAT_KEYS);
            case "phasitem" -> TagItems.syntaxError(name, args);
            case "gflag", "gf", "pflag", "pf", "lflag", "lf" -> flagError(name, args);
            case "rflag" -> RoleTagSyntax.flagError(name, args);
            case "placeholder" -> arityError(name, args, 1, "one key");
            case "loseplayer" -> loseplayerError(name, args);
            case "win" -> winError(name, args);
            case "args" -> argsError(name, args);
            case "list.append", "list.get", "list.set", "list.remove", "list.contains",
                    "list.clear", "list.pop", "len", "list.shuffle" ->
                    TagLists.opError(name, args);
            case "range" -> rangeError(name, args);
            case "active-players" -> activePlayersError(name, args);
            case "plocation", "prole" -> playerNameError(name, args);
            case "distance" -> topLevelArityError(name, args, 2, "<distance:loc1,loc2>");
            case "floor", "ceil", "round", "abs", "sign", "sqrt", "cbrt" -> mathUnaryError(name, args);
            case "root" -> topLevelArityError(name, args, 2, "<root:x,n>");
            case "while", "for" -> loopError(name, args);
            case "def" -> defError(name, args);
            default -> Optional.empty();
        };
    }

    private static Optional<String> defError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <def> needs a name and a body like <def:double,x+x,x>.");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() < 2 || parts.get(0).isBlank()) {
            return Optional.of("Tag <def> needs a name and a body like <def:double,x+x,x>.");
        }
        return Optional.empty();
    }

    private static void collectWarnings(String body, List<String> found,
            Collection<String> knownFunctions) {
        int separator = body.indexOf(':');
        String name = (separator < 0 ? body : body.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
        String args = separator < 0 ? null : body.substring(separator + 1);
        if (!KNOWN_TAGS.contains(name) && !knownFunctions.contains(name)) {
            found.add("Unknown tag '<" + body.trim() + ">', left untouched at runtime.");
            return;
        }
        if ("random-pick".equals(name) && args != null) {
            List<String> items = CommandPlaceholders.splitPickArgs(args);
            for (int index = 0; index < items.size(); index++) {
                if (CommandPlaceholders.parsePickItem(items.get(index)).isEmpty()) {
                    found.add("Skipping invalid <random-pick> item (" + items.get(index).trim()
                            + ") at index " + index + ".");
                }
            }
        } else if ("all-players".equals(name) && args != null && !LETTERS_ONLY.matcher(args.trim()).matches()) {
            found.add("Tag <all-players:" + args.trim() + "> filter should be letters only.");
        }
    }

    private static Optional<String> randomNumberError(String args) {
        if (args == null) {
            return Optional.of("Tag <random-num> needs two numbers like <random-num:1,6>.");
        }
        List<String> bounds = CommandPlaceholders.splitPickArgs(args);
        if (bounds.size() != 2) {
            return Optional.of("Tag <random-num:" + args.trim() + "> needs two numbers like <random-num:1,6>.");
        }
        Optional<String> firstItem = CommandPlaceholders.parsePickItem(bounds.get(0));
        Optional<String> secondItem = CommandPlaceholders.parsePickItem(bounds.get(1));
        if (firstItem.isEmpty() || secondItem.isEmpty()) {
            return Optional.of("Tag <random-num:" + args.trim() + "> mixes quotes.");
        }
        try {
            long first = Long.parseLong(firstItem.get().strip());
            long second = Long.parseLong(secondItem.get().strip());
            long low = Math.min(first, second);
            long high = Math.max(first, second);
            if (high - low + 1 <= 0) {
                return Optional.of("Tag <random-num:" + args.trim() + "> range is too large.");
            }
            if (low < Integer.MIN_VALUE || high > Integer.MAX_VALUE) {
                return Optional.of("Tag <random-num:" + args.trim() + "> is outside the integer range.");
            }
        } catch (NumberFormatException unmatched) {
            return Optional.of("Tag <random-num:" + args.trim() + "> needs two numbers like <random-num:1,6>.");
        }
        return Optional.empty();
    }

    private static Optional<String> randomPickError(String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <random-pick> needs at least one item.");
        }
        for (String item : CommandPlaceholders.splitPickArgs(args)) {
            if (CommandPlaceholders.parsePickItem(item).isPresent()) {
                return Optional.empty();
            }
        }
        return Optional.of("Tag <random-pick:" + args.trim() + "> has no valid item.");
    }

    private static Optional<String> noArgsError(String name, String args) {
        if (args != null && !args.isBlank()) {
            return Optional.of("Tag <" + name + "> takes no arguments.");
        }
        return Optional.empty();
    }

    /**
     * Loop shape: a condition or list plus a body. Split top-level
     * so list args survive, mirroring the runtime split.
     */
    private static Optional<String> loopError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a condition or list plus a body.");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 2) {
            return Optional.of("Tag <" + name + "> needs a condition or list plus a body.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    /**
     * Loseplayer tag shape: a required head player plus an optional
     * reason after the first comma.
     */
    private static Optional<String> loseplayerError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <loseplayer> needs a player like <loseplayer:Steve>.");
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (CommandPlaceholders.parsePickItem(parts.get(0)).isEmpty()) {
            return Optional.of("Tag <loseplayer> needs a player like <loseplayer:Steve>.");
        }
        return Optional.empty();
    }

    /**
     * Win tag shape: a head role, HUNTER or SPEEDRUNNER, plus an
     * optional reason after the first comma.
     */
    private static Optional<String> winError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <win> needs HUNTER or SPEEDRUNNER.");
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        Optional<String> item = CommandPlaceholders.parsePickItem(parts.get(0));
        if (item.isEmpty()) {
            return Optional.of("Tag <win> mixes quotes.");
        }
        String role = item.get().strip().toUpperCase(Locale.ROOT);
        if (!role.equals("HUNTER") && !role.equals("SPEEDRUNNER")) {
            return Optional.of("Tag <win> needs HUNTER or SPEEDRUNNER.");
        }
        return Optional.empty();
    }

    /**
     * Args tag shape: bare or one whole index. Like
     * {@code <random-num>} bounds the index must be literal here;
     * nested tags resolve at runtime but the editor cannot see
     * through them.
     */
    private static Optional<String> argsError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.empty();
        }
        try {
            Integer.parseInt(args.strip());
        } catch (NumberFormatException unmatched) {
            return Optional.of("Tag <args> needs a whole index like <args:0>.");
        }
        return Optional.empty();
    }

    /**
     * Fixed-arity shape over top-level segments (list literals stay
     * whole), each quote-clean unless blank, mirroring the runtime
     * splitter. Serves every tag whose args may hold lists.
     */
    private static Optional<String> topLevelArityError(String name, String args, int arity, String example) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs " + arity + " args like " + example + ".");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != arity) {
            return Optional.of("Tag <" + name + "> needs " + arity + " args like " + example + ".");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty() && !part.isBlank()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    /** Single-number math tags share one example shape. */
    private static Optional<String> mathUnaryError(String name, String args) {
        return topLevelArityError(name, args, 1, "<" + name + ":x>");
    }

    /**
     * Active-players shape: one role, HUNTER or SPEEDRUNNER. Like
     * {@code <win>} the role must be literal here.
     */
    /**
     * Range shape: one to three quote-clean bounds; values are
     * checked at runtime since they may be math or nested tags.
     */
    private static Optional<String> rangeError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <range> needs stop like <range:5>.");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.isEmpty() || parts.size() > 3) {
            return Optional.of("Tag <range> needs stop like <range:5>.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <range> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    private static Optional<String> activePlayersError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <active-players> needs HUNTER or SPEEDRUNNER.");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 1) {
            return Optional.of("Tag <active-players> needs HUNTER or SPEEDRUNNER.");
        }
        Optional<String> item = CommandPlaceholders.parsePickItem(parts.get(0));
        if (item.isEmpty()) {
            return Optional.of("Tag <active-players> mixes quotes.");
        }
        String role = item.get().strip().toUpperCase(Locale.ROOT);
        if (!role.equals("HUNTER") && !role.equals("SPEEDRUNNER")) {
            return Optional.of("Tag <active-players> needs HUNTER or SPEEDRUNNER.");
        }
        return Optional.empty();
    }

    /**
     * Player-name shape for {@code <plocation>} and {@code <prole>}:
     * one present, quote-clean name. Like {@code <loseplayer>} the
     * name may resolve from a nested tag at runtime.
     */
    private static Optional<String> playerNameError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a player like <" + name + ":Steve>.");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 1 || CommandPlaceholders.parsePickItem(parts.get(0)).isEmpty()) {
            return Optional.of("Tag <" + name + "> needs a player like <" + name + ":Steve>.");
        }
        return Optional.empty();
    }

    private static Optional<String> arityError(String name, String args, int arity, String what) {
        if (args == null || args.isBlank()
                || CommandPlaceholders.splitPickArgs(args).size() != arity) {
            return Optional.of("Tag <" + name + "> needs " + what + ".");
        }
        for (String part : CommandPlaceholders.splitPickArgs(args)) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    /**
     * Stat tag shape: arity, quote hygiene, plus the key. Keys must be
     * literal here like {@code <random-num>} bounds; nested tags
     * resolve at runtime but the editor cannot see through them.
     */
    private static Optional<String> statError(String name, String args, int keyIndex,
            List<String> validKeys) {
        int arity = keyIndex + 1;
        String what = arity == 1 ? "one key" : "a player plus a key";
        if (args == null || args.isBlank()
                || CommandPlaceholders.splitPickArgs(args).size() != arity) {
            return Optional.of("Tag <" + name + "> needs " + what + ".");
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        String key = CommandPlaceholders.parsePickItem(parts.get(keyIndex)).orElse("");
        if (!validKeys.contains(TagStats.normalizeKey(key))) {
            return Optional.of("Unknown <" + name + "> key '" + key.strip() + "'. Valid keys: "
                    + String.join(", ", validKeys) + ".");
        }
        return Optional.empty();
    }

    /** Flag tag shape: a name plus an optional value, quotes parsed. */
    private static Optional<String> flagError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a name plus an optional value.");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() < 1 || parts.size() > 2) {
            return Optional.of("Tag <" + name + "> needs a name plus an optional value.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        if (FlagStore.parseName(parts.get(0)).isEmpty()) {
            return Optional.of("Tag <" + name + "> needs a name.");
        }
        return Optional.empty();
    }

    private static Optional<String> soundError(String name, String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs a sound id.");
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() < 1 || parts.size() > 3) {
            return Optional.of("Tag <" + name + "> needs an id plus pitch and volume.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    private static Optional<String> ifError(String args) {
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <if> needs a condition plus one or two branches.");
        }
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
        if (parts.size() < 2 || parts.size() > 3) {
            return Optional.of("Tag <if> needs a condition plus one or two branches.");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty()) {
                return Optional.of("Tag <if> mixes quotes.");
            }
        }
        String condition = CommandPlaceholders.parsePickItem(parts.get(0)).orElse("").strip();
        try {
            TagExpressions.validateConditionBrackets(condition);
        } catch (TagExpressions.ExprException spacing) {
            return Optional.of("Tag <if> " + spacing.getMessage() + ".");
        }
        if (!TagExpressions.hasComparison(condition)) {
            return Optional.of("Tag <if> condition needs a comparison (==, !=, lt, le, gt, ge).");
        }
        return Optional.empty();
    }

    /**
     * First-token check against injected known roots: strips leading
     * slashes and namespace prefixes, lowercases like dispatch, and
     * fails unknown commands naming the token. Placeholder-built roots
     * resolve at runtime, so they always pass. Pure for tests.
     */
    public static Optional<String> unknownRoot(String command, Set<String> knownRoots) {
        if (TagExpressions.isExit(command)) {
            return Optional.empty();
        }
        String token = firstToken(command);
        if (token.contains("<") || token.contains(">")) {
            return Optional.empty();
        }
        String root = rootName(token);
        if (root.isEmpty()) {
            return Optional.of("Command must not be empty.");
        }
        if (knownRoots.contains(root)) {
            return Optional.empty();
        }
        return Optional.of("Unknown command '" + token + "'.");
    }

    /**
     * Give-shape item check: for give commands the third token must be
     * a known material unless placeholders build it at runtime. Unknown
     * items fail, with a did-you-mean hint when exactly one candidate
     * is close. Pure for tests.
     */
    public static Optional<String> giveItemCheck(String command, Predicate<String> knownMaterial,
            Collection<String> materialNames) {
        List<String> tokens = tokens(command);
        if (tokens.isEmpty() || !"give".equals(rootName(tokens.get(0)))) {
            return Optional.empty();
        }
        if (tokens.size() < 3) {
            return Optional.empty();
        }
        String item = tokens.get(2);
        if (item.contains("<") || item.contains(">") || knownMaterial.test(item)) {
            return Optional.empty();
        }
        String hint = closestMaterial(item, materialNames);
        if (hint == null) {
            return Optional.of("Unknown item '" + item + "'.");
        }
        return Optional.of("Unknown item '" + item + "'. Did you mean '" + hint + "'?");
    }

    /** First whitespace token with leading slashes stripped. */
    private static String firstToken(String command) {
        List<String> tokens = tokens(command);
        return tokens.isEmpty() ? "" : tokens.get(0);
    }

    private static List<String> tokens(String command) {
        if (command == null || command.isBlank()) {
            return List.of();
        }
        String text = command.strip().replaceFirst("^/+", "").strip();
        if (text.isEmpty()) {
            return List.of();
        }
        return List.of(text.split("\\s+"));
    }

    /** Dispatch-style root: namespace prefix stripped, lowercased. */
    private static String rootName(String token) {
        int colon = token.indexOf(':');
        String bare = colon < 0 ? token : token.substring(colon + 1);
        return bare.toLowerCase(Locale.ROOT);
    }

    /**
     * Dispatch-style root of a command line: first whitespace token
     * with slashes and any namespace prefix stripped, lowercased.
     * Blank lines have no root. Pure for tests.
     */
    public static String commandRoot(String command) {
        return rootName(firstToken(command));
    }

    /**
     * True when the line's root names a blocked command. Both sides
     * normalize through {@link #commandRoot}, so list entries like
     * {@code /Op} or {@code minecraft:stop} still match. Blank lines
     * and null lists never match. Pure for tests.
     */
    public static boolean isBlockedCommand(String command, Collection<String> blocked) {
        String root = commandRoot(command);
        if (root.isEmpty() || blocked == null) {
            return false;
        }
        for (String entry : blocked) {
            if (entry != null && root.equals(commandRoot(entry))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Closest candidate within two edits, or null when none is close
     * or the best distance ties. Exact matches are skipped: they are
     * the item itself under a predicate the caller already rejected.
     */
    private static String closestMaterial(String item, Collection<String> materialNames) {
        String want = item.toLowerCase(Locale.ROOT);
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : materialNames) {
            int distance = editDistance(want, candidate.toLowerCase(Locale.ROOT));
            if (distance == 0) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            } else if (distance == bestDistance) {
                best = null;
            }
        }
        return bestDistance <= 2 ? best : null;
    }

    /** Plain Levenshtein distance over two short names. */
    private static int editDistance(String first, String second) {
        int[] row = new int[second.length() + 1];
        for (int column = 0; column <= second.length(); column++) {
            row[column] = column;
        }
        for (int left = 1; left <= first.length(); left++) {
            int diagonal = row[0];
            row[0] = left;
            for (int right = 1; right <= second.length(); right++) {
                int keep = diagonal + (first.charAt(left - 1) == second.charAt(right - 1) ? 0 : 1);
                diagonal = row[right];
                row[right] = Math.min(keep, Math.min(row[right] + 1, row[right - 1] + 1));
            }
        }
        return row[second.length()];
    }
}
