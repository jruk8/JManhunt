package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * List foundation for JMHScript: the shared parse/format contract
 * behind flag-held lists, {@code <all-players>},
 * {@code <active-players>}, and every list op. Canonical form joins
 * items with {@code ", "} inside square brackets; parsing splits
 * top-level commas only, so nested lists (like location primitives)
 * survive as single items. Items store verbatim with no escaping:
 * an item holding a top-level comma splits on re-parse.
 */
public final class TagLists {

    private TagLists() {
    }

    /** True when the trimmed text is bracket-wrapped (length >= 2). */
    public static boolean isList(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.strip();
        return trimmed.length() >= 2 && trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    /**
     * Parses a list value to items. Non-lists and blank innards yield
     * an empty list; items split on top-level commas (square-depth
     * aware), trim, and keep empty items for round-trip stability.
     */
    public static List<String> parse(String text) {
        if (!isList(text)) {
            return List.of();
        }
        String inner = text.strip();
        inner = inner.substring(1, inner.length() - 1);
        if (inner.isBlank()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        StringBuilder item = new StringBuilder();
        int depth = 0;
        for (int index = 0; index < inner.length(); index++) {
            char letter = inner.charAt(index);
            if (letter == '[') {
                depth++;
                item.append(letter);
            } else if (letter == ']') {
                if (depth > 0) {
                    depth--;
                }
                item.append(letter);
            } else if (letter == ',' && depth == 0) {
                items.add(item.toString().strip());
                item.setLength(0);
            } else {
                item.append(letter);
            }
        }
        items.add(item.toString().strip());
        return items;
    }

    /** Canonical list string: items joined with {@code ", "} in brackets. */
    public static String format(List<String> items) {
        return "[" + String.join(", ", items) + "]";
    }

    /** Outcome of one list op: return text plus optional write-back items. */
    record OpResult(String text, List<String> writeBack) {
    }

    /** A verbatim flag reference (`{@code <gflag:name>}` and siblings). */
    record FlagRef(String kind, String name) {
    }

    private static final Pattern FLAG_REF = Pattern.compile(
            "<\\s*(gflag|pflag|lflag|rflag)\\s*:\\s*([^<>]+)\\s*>", Pattern.CASE_INSENSITIVE);

    /**
     * Matches one pre-split segment against a verbatim flag reference.
     * The name must be quote-clean; anything else (literals, other
     * expressions) is not a reference and applies purely.
     */
    static Optional<FlagRef> flagRef(String segment) {
        Matcher match = FLAG_REF.matcher(segment.strip());
        if (!match.matches()) {
            return Optional.empty();
        }
        Optional<String> name = FlagStore.parseName(match.group(2));
        if (name.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new FlagRef(match.group(1).toLowerCase(Locale.ROOT), name.get()));
    }

    /**
     * Pure list-op entry for tag evaluation: splits, validates, and
     * applies without persistence (used for literal and resolved
     * list args).
     */
    static String resolve(String tag, String op, String args, TagContext context) {
        return apply(tag, op, splitTopLevel(args), context).text();
    }

    /** Largest list {@code <range>} builds before warning and capping. */
    static final int RANGE_LIMIT = 1000;

    /**
     * Builds python-style {@code <range:stop>} /
     * {@code <range:start,stop>} / {@code <range:start,stop,step>}:
     * start inclusive, stop exclusive, bounds are whole numbers and
     * may be math. Counts down while start exceeds stop when the
     * step is negative. Any bad shape warns and yields
     * {@code "null"}, and ranges past the cap warn and keep the
     * first thousand.
     */
    static String range(String tag, String args, TagContext context) {
        List<String> parts = splitTopLevel(args);
        if (parts.isEmpty() || parts.size() > 3) {
            context.scope().warn("Tag <range> needs stop like <range:5>: " + tag);
            return "null";
        }
        Optional<Long> stop = parts.size() == 1
                ? wholeNumber(tag, parts.get(0), context)
                : wholeNumber(tag, parts.get(1), context);
        Optional<Long> start = parts.size() == 1
                ? Optional.of(0L) : wholeNumber(tag, parts.get(0), context);
        Optional<Long> step = parts.size() == 3
                ? wholeNumber(tag, parts.get(2), context) : Optional.of(1L);
        if (start.isEmpty() || stop.isEmpty() || step.isEmpty()) {
            return "null";
        }
        long delta = step.get();
        if (delta == 0) {
            context.scope().warn("Tag <range> needs a nonzero step: " + tag);
            return "null";
        }
        List<String> items = new ArrayList<>();
        boolean climbing = delta > 0;
        for (long value = start.get();
                climbing ? value < stop.get() : value > stop.get();
                value += delta) {
            items.add(Long.toString(value));
            if (items.size() > RANGE_LIMIT) {
                context.scope().warn(
                        "Tag <range> capped at " + RANGE_LIMIT + " items: " + tag);
                return format(items.subList(0, RANGE_LIMIT));
            }
        }
        return format(items);
    }

    /** One range bound: math that lands on a whole number in long range. */
    private static Optional<Long> wholeNumber(String tag, String raw, TagContext context) {
        Optional<String> item = CommandPlaceholders.parsePickItem(raw);
        String text = item.map(String::strip).orElse("");
        try {
            Double number = TagMath.evaluate(text);
            if (number == null || number.isNaN() || number.isInfinite()
                    || number != Math.floor(number) || Math.abs(number) > 9e15) {
                context.scope().warn("Tag <range> needs whole numbers like <range:1,5>: " + tag);
                return Optional.empty();
            }
            return Optional.of(number.longValue());
        } catch (TagMath.SyntaxException | TagMath.EvalException failed) {
            context.scope().warn("Tag <range> needs whole numbers like <range:1,5>: " + tag);
            return Optional.empty();
        }
    }

    /**
     * Applies a mutating op with flag write-back: the flag value is
     * loaded, the op applied, and the result stored back, unless the
     * op reports no write (out-of-bounds set). Remaining segments
     * must be pre-evaluated by the caller.
     */
    static String writeBack(String tag, String op, String kind, String name,
            List<String> evaluatedRest, TagContext context) {
        String current = TagFlags.loadFlag(kind, name, context);
        List<String> segments = new ArrayList<>();
        segments.add(current);
        segments.addAll(evaluatedRest);
        OpResult result = apply(tag, op, segments, context);
        if (result.writeBack() != null) {
            TagFlags.storeFlag(kind, name, format(result.writeBack()), tag, context);
        }
        return result.text();
    }

    /**
     * Validates arity and quotes, then applies one op to the parsed
     * list value. Misuse warns plus {@code "null"} ({@code "0"} for
     * {@code len}); data misses (out-of-bounds, absent items) use
     * their specified returns silently.
     */
    private static OpResult apply(String tag, String op, List<String> segments, TagContext context) {
        if (segments.size() != arity(op)) {
            context.scope().warn("Tag <" + op + "> needs " + arity(op) + " args like "
                    + example(op) + ": " + tag);
            return new OpResult(failure(op), null);
        }
        List<String> args = new ArrayList<>();
        for (String segment : segments) {
            Optional<String> item = CommandPlaceholders.parsePickItem(segment);
            if (item.isEmpty() && !segment.isBlank()) {
                context.scope().warn("Tag <" + op + "> mixes quotes: " + tag);
                return new OpResult(failure(op), null);
            }
            args.add(item.orElse(""));
        }
        List<String> items = new ArrayList<>(parse(args.get(0)));
        return switch (op) {
            case "list.append" -> {
                items.add(args.get(1));
                yield new OpResult("", items);
            }
            case "list.get" -> new OpResult(at(tag, op, items, args.get(1), context), null);
            case "list.set" -> set(tag, items, args.get(1), args.get(2), context);
            case "list.remove" -> {
                boolean removed = items.remove(args.get(1));
                yield new OpResult(removed ? "true" : "false", items);
            }
            case "list.contains" -> new OpResult(items.contains(args.get(1)) ? "true" : "false", null);
            case "list.clear" -> new OpResult("", List.of());
            case "list.pop" -> {
                if (items.isEmpty()) {
                    yield new OpResult("null", null);
                }
                yield new OpResult(items.remove(0), items);
            }
            case "len" -> new OpResult(String.valueOf(items.size()), null);
            default -> {
                Collections.shuffle(items, ThreadLocalRandom.current());
                yield new OpResult("", items);
            }
        };
    }

    /** Element at index, or {@code "null"} when out of bounds. */
    private static String at(String tag, String op, List<String> items, String indexText,
            TagContext context) {
        Optional<Integer> index = listIndex(tag, op, indexText, context);
        if (index.isEmpty()) {
            return "null";
        }
        int at = index.get();
        if (at < 0 || at >= items.size()) {
            return "null";
        }
        return items.get(at);
    }

    /**
     * Sets one index; out-of-bounds reports {@code "null"} with no
     * write. Index exactly size appends, so set-index-0 grows an
     * empty list.
     */
    private static OpResult set(String tag, List<String> items, String indexText, String value,
            TagContext context) {
        Optional<Integer> index = listIndex(tag, "list.set", indexText, context);
        if (index.isEmpty()) {
            return new OpResult("null", null);
        }
        int at = index.get();
        if (at < 0 || at > items.size()) {
            return new OpResult("null", null);
        }
        if (at == items.size()) {
            items.add(value);
        } else {
            items.set(at, value);
        }
        return new OpResult("", items);
    }

    /** Whole-number index; non-numeric warns plus empty. */
    private static Optional<Integer> listIndex(String tag, String op, String indexText,
            TagContext context) {
        try {
            return Optional.of(Integer.parseInt(indexText.strip()));
        } catch (NumberFormatException unmatched) {
            context.scope().warn("Tag <" + op + "> needs a whole index: " + tag);
            return Optional.empty();
        }
    }

    private static int arity(String op) {
        return switch (op) {
            case "list.set" -> 3;
            case "list.clear", "list.pop", "len", "list.shuffle" -> 1;
            default -> 2;
        };
    }

    private static String example(String op) {
        return switch (op) {
            case "list.append" -> "<list.append:list,x>";
            case "list.get" -> "<list.get:list,index>";
            case "list.set" -> "<list.set:list,index,x>";
            case "list.remove" -> "<list.remove:list,x>";
            case "list.contains" -> "<list.contains:list,x>";
            case "list.clear" -> "<list.clear:list>";
            case "list.pop" -> "<list.pop:list>";
            case "len" -> "<len:list>";
            default -> "<list.shuffle:list>";
        };
    }

    /** Misuse return: {@code "null"}, except numeric {@code len} ({@code "0"}). */
    private static String failure(String op) {
        return op.equals("len") ? "0" : "null";
    }

    /**
     * Splits tag args on top-level commas: commas inside nested tags
     * ({@code <...}>), square brackets ({@code [...]}), or quotes
     * never split. List ops and flag values need this (the shared
     * splitter would shred list literals); other tags keep the shared
     * splitter.
     */
    public static List<String> splitTopLevel(String args) {
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        int angles = 0;
        int squares = 0;
        char quote = 0;
        for (int index = 0; index < args.length(); index++) {
            char letter = args.charAt(index);
            if (quote != 0) {
                part.append(letter);
                if (letter == quote) {
                    quote = 0;
                }
            } else if (letter == '"' || letter == '\'') {
                quote = letter;
                part.append(letter);
            } else if (letter == '<') {
                angles++;
                part.append(letter);
            } else if (letter == '>') {
                if (angles > 0) {
                    angles--;
                }
                part.append(letter);
            } else if (letter == '[') {
                squares++;
                part.append(letter);
            } else if (letter == ']') {
                if (squares > 0) {
                    squares--;
                }
                part.append(letter);
            } else if (letter == ',' && angles == 0 && squares == 0) {
                parts.add(part.toString());
                part.setLength(0);
            } else {
                part.append(letter);
            }
        }
        parts.add(part.toString());
        return parts;
    }
}
