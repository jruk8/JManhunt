package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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
    private static final Set<String> KNOWN_TAGS = Set.of("p", "random-mob", "random-item",
            "random-num", "random-pick", "random-player", "all-players", "duration");

    private CommandSyntax() {
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
}
