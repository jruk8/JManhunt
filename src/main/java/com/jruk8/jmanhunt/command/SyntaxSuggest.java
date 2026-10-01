package com.jruk8.jmanhunt.command;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Root token checks and typo suggestions behind {@link CommandSyntax}. */
final class SyntaxSuggest {
    private SyntaxSuggest() {
    }

    /**
     * First-token check against injected known roots: strips leading
     * slashes and namespace prefixes, lowercases like dispatch, and
     * fails unknown commands naming the token. Placeholder-built roots
     * resolve at runtime, so they always pass. Pure for tests.
     */
    static Optional<String> unknownRoot(String command, Set<String> knownRoots) {
        if (TagControlFlow.isExit(command)) {
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
    static Optional<String> giveItemCheck(String command, Predicate<String> knownMaterial,
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
    static String firstToken(String command) {
        List<String> tokens = tokens(command);
        return tokens.isEmpty() ? "" : tokens.get(0);
    }

    static List<String> tokens(String command) {
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
    static String rootName(String token) {
        int colon = token.indexOf(':');
        String bare = colon < 0 ? token : token.substring(colon + 1);
        return bare.toLowerCase(Locale.ROOT);
    }

    /**
     * Dispatch-style root of a command line: first whitespace token
     * with slashes and any namespace prefix stripped, lowercased.
     * Blank lines have no root. Pure for tests.
     */
    static String commandRoot(String command) {
        return rootName(firstToken(command));
    }

    /**
     * True when the line's root names a blocked command. Both sides
     * normalize through {@link #commandRoot}, so list entries like
     * {@code /Op} or {@code minecraft:stop} still match. Blank lines
     * and null lists never match. Pure for tests.
     */
    static boolean isBlockedCommand(String command, Collection<String> blocked) {
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
    static String closestMaterial(String item, Collection<String> materialNames) {
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
    static int editDistance(String first, String second) {
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
