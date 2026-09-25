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
                "pstat", "gstat", "gflag", "pflag", "lflag", "placeholder");
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
    private static List<String> tagBodies(String command) {
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
        List<String> found = new ArrayList<>();
        if (command == null || command.isBlank() || error(command).isPresent()) {
            return found;
        }
        for (String body : tagBodies(command)) {
            collectWarnings(body, found);
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
            case "id" -> idError(args);
            case "min", "max" -> arityError(name, args, 2, "two numbers");
            case "clamp" -> arityError(name, args, 3, "a value plus low and high");
            case "gmessage", "pmessage" -> arityError(name, args, 1, "one text");
            case "gsound", "psound" -> soundError(name, args);
            case "if" -> ifError(args);
            case "pstat" -> statError(name, args, 1, TagStats.PSTAT_KEYS);
            case "gstat" -> statError(name, args, 0, TagStats.GSTAT_KEYS);
            case "gflag", "pflag", "lflag" -> flagError(name, args);
            case "placeholder" -> arityError(name, args, 1, "one key");
            default -> Optional.empty();
        };
    }

    private static void collectWarnings(String body, List<String> found) {
        int separator = body.indexOf(':');
        String name = (separator < 0 ? body : body.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
        String args = separator < 0 ? null : body.substring(separator + 1);
        if (!KNOWN_TAGS.contains(name)) {
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
        String[] bounds = args.split(",", -1);
        if (bounds.length != 2) {
            return Optional.of("Tag <random-num:" + args.trim() + "> needs two numbers like <random-num:1,6>.");
        }
        try {
            long first = Long.parseLong(bounds[0].trim());
            long second = Long.parseLong(bounds[1].trim());
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

    private static Optional<String> idError(String args) {
        if (args != null && !args.isBlank()) {
            return Optional.of("Tag <id> takes no arguments.");
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
        List<String> parts = CommandPlaceholders.splitPickArgs(args);
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
            TagExpressions.validateConditionSpacing(condition);
        } catch (TagExpressions.ExprException spacing) {
            return Optional.of("Tag <if> " + spacing.getMessage() + ".");
        }
        if (!TagExpressions.hasComparison(condition)) {
            return Optional.of("Tag <if> condition needs a comparison (==, !=, >, <, >=, <=).");
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
