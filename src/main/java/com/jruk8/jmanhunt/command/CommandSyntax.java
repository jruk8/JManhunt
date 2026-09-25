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
     * Engine tags in cheatsheet order. The single tag table: validation
     * and the dialog cheatsheet both read from here, so a new tag can
     * never validate but stay undocumented.
     */
    public static List<String> knownTags() {
        return List.of("p", "random-mob", "random-item", "random-num",
                "random-pick", "random-player", "all-players", "duration");
    }

    /**
     * First fatal problem with a typed command: blank input, unbalanced
     * angle brackets, or malformed random-num/random-pick args. Empty
     * means the command is safe to save.
     */
    public static Optional<String> error(String command) {
        if (command == null || command.isBlank()) {
            return Optional.of("Command must not be empty.");
        }
        int depth = 0;
        for (int index = 0; index < command.length(); index++) {
            char letter = command.charAt(index);
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
     * longer hides its outer tag from validation. Pass cap mirrors
     * dispatch; anything deeper stays for runtime to report.
     */
    private static List<String> tagBodies(String command) {
        List<String> bodies = new ArrayList<>();
        String current = command;
        for (int pass = 0; pass < 25; pass++) {
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

    /**
     * First-token check against injected known roots: strips leading
     * slashes and namespace prefixes, lowercases like dispatch, and
     * fails unknown commands naming the token. Placeholder-built roots
     * resolve at runtime, so they always pass. Pure for tests.
     */
    public static Optional<String> unknownRoot(String command, Set<String> knownRoots) {
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
