package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * String foundation for JMHScript: the SSOT behind
 * {@code <str.join>}, {@code <str.split>}, {@code <str.lower>},
 * {@code <str.upper>}, and {@code <str.contains>}.
 * {@code <list.join>} delegates to the same join. Splits are
 * literal (never regex) and keep every part, so a join-split
 * round trip restores the list exactly. No Bukkit types.
 */
public final class TagStrings {

    private TagStrings() {
    }

    /** Joins list items with the separator verbatim. */
    public static String join(List<String> items, String separator) {
        return String.join(separator, items);
    }

    /** Literal split keeping every part, including empties. */
    public static List<String> split(String text, String delimiter) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int hit = text.indexOf(delimiter, start);
        while (hit >= 0) {
            parts.add(text.substring(start, hit));
            start = hit + delimiter.length();
            hit = text.indexOf(delimiter, start);
        }
        parts.add(text.substring(start));
        return parts;
    }

    /**
     * Pure string-op entry for tag evaluation: splits, validates,
     * and applies. Misuse warns plus {@code "null"}; data misses
     * use their specified returns silently.
     */
    static String resolve(String tag, String name, String args, TagContext context) {
        List<String> segments = TagLists.splitTopLevel(args);
        if (segments.size() != arity(name)) {
            context.scope().warn("Tag <" + name + "> needs " + arity(name) + " args like "
                    + example(name) + ": " + tag);
            return "null";
        }
        List<String> parsed = new ArrayList<>();
        for (String segment : segments) {
            Optional<String> item = CommandPlaceholders.parsePickItem(segment);
            if (item.isEmpty() && !segment.isBlank()) {
                context.scope().warn("Tag <" + name + "> mixes quotes: " + tag);
                return "null";
            }
            parsed.add(item.orElse(""));
        }
        return switch (name) {
            case "str.join" -> joinOp(tag, parsed.get(0), parsed.get(1), context);
            case "str.split" -> splitOp(tag, parsed.get(0), parsed.get(1), context);
            case "str.lower" -> parsed.get(0).toLowerCase(Locale.ROOT);
            case "str.upper" -> parsed.get(0).toUpperCase(Locale.ROOT);
            default -> parsed.get(0).contains(parsed.get(1)) ? "true" : "false";
        };
    }

    /** Joins a list value; non-lists warn plus null. */
    private static String joinOp(String tag, String raw, String separator, TagContext context) {
        if (!TagLists.isList(raw)) {
            context.scope().warn("Tag <str.join> needs a list like [a, b]: " + tag);
            return "null";
        }
        return join(TagLists.parse(raw), separator);
    }

    /** Splits text on a literal delimiter; empty delimiters warn plus null. */
    private static String splitOp(String tag, String text, String delimiter, TagContext context) {
        if (delimiter.isEmpty()) {
            context.scope().warn("Tag <str.split> needs a non-empty delimiter: " + tag);
            return "null";
        }
        return TagLists.format(split(text, delimiter));
    }

    /**
     * Edit-time arity for the string ops: top-level split, quote
     * hygiene, plus the per-op shape. Mirrors the runtime warns.
     */
    static Optional<String> opError(String name, String args) {
        int count = arity(name);
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs " + count + " args like "
                    + example(name) + ".");
        }
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != count) {
            return Optional.of("Tag <" + name + "> needs " + count + " args like "
                    + example(name) + ".");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty() && !part.isBlank()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
    }

    private static int arity(String name) {
        return switch (name) {
            case "str.lower", "str.upper" -> 1;
            default -> 2;
        };
    }

    private static String example(String name) {
        return switch (name) {
            case "str.join" -> "<str.join:list,sep>";
            case "str.split" -> "<str.split:text,delim>";
            case "str.lower" -> "<str.lower:text>";
            case "str.upper" -> "<str.upper:text>";
            default -> "<str.contains:text,needle>";
        };
    }
}
