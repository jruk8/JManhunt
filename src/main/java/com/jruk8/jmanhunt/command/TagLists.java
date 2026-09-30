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

    /**
     * A verbatim flag reference (`{@code <gflag:name>}` and siblings).
     * Role references carry the full store key (`{@code ROLE:name}`).
     */
    record FlagRef(String kind, String name) {
    }

    private static final Pattern FLAG_REF = Pattern.compile(
            "<\\s*(gflag|pflag|lflag|rflag|gf|pf|lf)\\s*:\\s*([^<>]+)\\s*>",
            Pattern.CASE_INSENSITIVE);

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
        String kind = match.group(1).toLowerCase(Locale.ROOT);
        if (kind.equals("rflag")) {
            return roleFlagRef(match.group(2));
        }
        Optional<String> name = FlagStore.parseName(match.group(2));
        if (name.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new FlagRef(kind, name.get()));
    }

    /**
     * Matches a verbatim {@code <rflag:role,name>} reference. Bad
     * roles or names are not references, so the span applies purely
     * and the inner tag warns through the normal path.
     */
    private static Optional<FlagRef> roleFlagRef(String args) {
        List<String> parts = splitTopLevel(args);
        if (parts.size() != 2) {
            return Optional.empty();
        }
        Optional<String> role = FlagStore.canonicalRole(parts.get(0));
        Optional<String> name = FlagStore.parseName(parts.get(1));
        if (role.isEmpty() || name.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new FlagRef("rflag", FlagStore.roleKey(role.get(), name.get())));
    }

    /**
     * Pure list-op entry for tag evaluation: splits, validates, and
     * applies without persistence (used for literal and resolved
     * list args).
     */
    static String resolve(String tag, String op, String args, TagContext context,
            TagLoops.Evaluator eval) {
        return apply(tag, op, splitTopLevel(args), context, eval).text();
    }

    /**
     * Edit-time arity for the list ops: top-level split, quote
     * hygiene, plus the per-op shape. Mirrors the runtime warns.
     */
    static Optional<String> opError(String name, String args) {
        int arity = name.equals("list.set") || name.equals("list.slice") ? 3
                : name.equals("list.append") || name.equals("list.get") || name.equals("list.remove")
                        || name.equals("list.contains") || name.equals("list.filter")
                        || name.equals("list.join") ? 2 : 1;
        String example = switch (name) {
            case "list.append" -> "<list.append:list,x>";
            case "list.get" -> "<list.get:list,index>";
            case "list.set" -> "<list.set:list,index,x>";
            case "list.remove" -> "<list.remove:list,x>";
            case "list.contains" -> "<list.contains:list,x>";
            case "list.clear" -> "<list.clear:list>";
            case "list.pop" -> "<list.pop:list>";
            case "list.shuffle" -> "<list.shuffle:list>";
            case "list.filter" -> "<list.filter:list,cond>";
            case "list.reverse" -> "<list.reverse:list>";
            case "list.join" -> "<list.join:list,sep>";
            case "list.slice" -> "<list.slice:list,start,end>";
            case "list.first" -> "<list.first:list>";
            case "list.last" -> "<list.last:list>";
            default -> "<len:list>";
        };
        if (args == null || args.isBlank()) {
            return Optional.of("Tag <" + name + "> needs " + arity + " args like " + example
                    + ".");
        }
        List<String> parts = splitTopLevel(args);
        if (parts.size() != arity) {
            return Optional.of("Tag <" + name + "> needs " + arity + " args like " + example
                    + ".");
        }
        for (String part : parts) {
            if (CommandPlaceholders.parsePickItem(part).isEmpty() && !part.isBlank()) {
                return Optional.of("Tag <" + name + "> mixes quotes.");
            }
        }
        return Optional.empty();
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
            List<String> evaluatedRest, TagContext context, TagLoops.Evaluator eval) {
        String current = TagFlags.loadFlag(kind, name, context);
        List<String> segments = new ArrayList<>();
        segments.add(current);
        segments.addAll(evaluatedRest);
        OpResult result = apply(tag, op, segments, context, eval);
        if (result.writeBack() != null) {
            TagFlags.storeFlag(kind, name, format(result.writeBack()), tag, context);
        }
        return result.text();
    }

    /**
     * Validates arity and quotes, then parses each segment. Misuse
     * warns and yields empty; the caller maps that to the op's
     * failure value.
     */
    private static Optional<List<String>> parsedArgs(String tag, String op,
            List<String> segments, TagContext context) {
        if (segments.size() != arity(op)) {
            context.scope().warn("Tag <" + op + "> needs " + arity(op) + " args like "
                    + example(op) + ": " + tag);
            return Optional.empty();
        }
        List<String> args = new ArrayList<>();
        for (String segment : segments) {
            Optional<String> item = CommandPlaceholders.parsePickItem(segment);
            if (item.isEmpty() && !segment.isBlank()) {
                context.scope().warn("Tag <" + op + "> mixes quotes: " + tag);
                return Optional.empty();
            }
            args.add(item.orElse(""));
        }
        return Optional.of(args);
    }

    /**
     * Validates arity and quotes, then applies one op to the parsed
     * list value. Misuse warns plus {@code "null"} ({@code "0"} for
     * {@code len}); data misses (out-of-bounds, absent items) use
     * their specified returns silently.
     */
    private static OpResult apply(String tag, String op, List<String> segments, TagContext context,
            TagLoops.Evaluator eval) {
        Optional<List<String>> parsed = parsedArgs(tag, op, segments, context);
        if (parsed.isEmpty()) {
            return new OpResult(failure(op), null);
        }
        List<String> args = parsed.get();
        List<String> items = new ArrayList<>(parse(args.get(0)));
        OpResult extra = applyExtra(tag, op, args, items, context, eval);
        if (extra != null) {
            return extra;
        }
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

    /**
     * Pure read-only ops: filter, reverse, join, slice, first, and
     * last. Null when the op is not one of them. Never writes back,
     * so flag references read through without storing.
     */
    private static OpResult applyExtra(String tag, String op, List<String> args,
            List<String> items, TagContext context, TagLoops.Evaluator eval) {
        return switch (op) {
            case "list.filter" -> filter(tag, args.get(0), items, args.get(1), context, eval);
            case "list.reverse" -> reverse(items);
            case "list.join" -> joinOp(tag, args.get(0), args.get(1), context);
            case "list.slice" -> slice(tag, items, args.get(1), args.get(2), context);
            case "list.first" -> new OpResult(items.isEmpty() ? "null" : items.get(0), null);
            case "list.last" -> new OpResult(items.isEmpty() ? "null"
                    : items.get(items.size() - 1), null);
            default -> null;
        };
    }

    /**
     * Eager filter for pre-resolved args: keeps the items whose
     * condition resolves to {@code true} (case-blind) with the item
     * behind {@code <i>}. Non-lists warn plus null.
     */
    private static OpResult filter(String tag, String raw, List<String> items, String condition,
            TagContext context, TagLoops.Evaluator eval) {
        if (!isList(raw)) {
            context.scope().warn("Tag <list.filter> needs a list like [a, b]: " + tag);
            return new OpResult("null", null);
        }
        return filterItems(tag, items, condition, context, eval);
    }

    /**
     * Outermost balanced filter spans: the condition binds
     * {@code <i>} per item, so spans resolve in the pre-pass like
     * loops instead of the innermost-tag sweep.
     */
    static List<TagLoops.LoopSpan> findFilterSpans(String line) {
        return TagLoops.findOpSpans(line, "list.filter"::equals);
    }

    /**
     * Lazy filter for one span: the list arg resolves once up front
     * while the condition re-resolves per item with the item behind
     * {@code <i>}. Bad shapes warn plus {@code "null"}.
     */
    static String filterSpan(String tag, String args, TagContext context,
            TagLoops.Evaluator eval) {
        List<String> parts = splitTopLevel(args);
        if (parts.size() != 2) {
            context.scope().warn("Tag <list.filter> needs a list plus a condition like "
                    + "<list.filter:list,cond>: " + tag);
            return "null";
        }
        String resolved = eval.evaluate(parts.get(0));
        if (!isList(resolved)) {
            context.scope().warn("Tag <list.filter> needs a list like [a, b]: " + tag);
            return "null";
        }
        return filterItems(tag, parse(resolved), parts.get(1), context, eval).text();
    }

    /**
     * Keeps the items whose condition resolves to {@code true}
     * (case-blind) with the item behind {@code <i>}. Each item
     * spends one shared line step.
     */
    private static OpResult filterItems(String tag, List<String> items, String condition,
            TagContext context, TagLoops.Evaluator eval) {
        List<String> kept = new ArrayList<>();
        for (String item : items) {
            if (!context.tryConsumeStep()) {
                context.loopLimitExceeded("<list.filter> exceeded " + TagLoops.LOOP_LIMIT
                        + " steps at " + context.provenance().describe() + ": " + tag);
                return new OpResult("null", null);
            }
            context.pushLoopItem(item);
            try {
                if (eval.evaluate(condition).strip().equalsIgnoreCase("true")) {
                    kept.add(item);
                }
            } finally {
                context.popLoopItem();
            }
        }
        return new OpResult(format(kept), null);
    }

    /** Reversed copy; the input list is never mutated. */
    private static OpResult reverse(List<String> items) {
        List<String> copy = new ArrayList<>(items);
        Collections.reverse(copy);
        return new OpResult(format(copy), null);
    }

    /** Joins through the shared string SSOT; non-lists warn plus null. */
    private static OpResult joinOp(String tag, String raw, String separator, TagContext context) {
        if (!isList(raw)) {
            context.scope().warn("Tag <list.join> needs a list like [a, b]: " + tag);
            return new OpResult("null", null);
        }
        return new OpResult(TagStrings.join(parse(raw), separator), null);
    }

    /**
     * Python-style slice: start inclusive, end exclusive, negatives
     * count from the end, out-of-range bounds clamp. Non-integer
     * bounds warn plus null.
     */
    private static OpResult slice(String tag, List<String> items, String startText,
            String endText, TagContext context) {
        Optional<Integer> start = sliceBound(tag, startText, context);
        Optional<Integer> end = sliceBound(tag, endText, context);
        if (start.isEmpty() || end.isEmpty()) {
            return new OpResult("null", null);
        }
        int size = items.size();
        int from = Math.min(Math.max(start.get() < 0 ? size + start.get() : start.get(), 0), size);
        int to = Math.min(Math.max(end.get() < 0 ? size + end.get() : end.get(), 0), size);
        if (from >= to) {
            return new OpResult(format(List.of()), null);
        }
        return new OpResult(format(new ArrayList<>(items.subList(from, to))), null);
    }

    /** Whole-number slice bound; non-numeric warns plus empty. */
    private static Optional<Integer> sliceBound(String tag, String boundText, TagContext context) {
        try {
            return Optional.of(Integer.parseInt(boundText.strip()));
        } catch (NumberFormatException unmatched) {
            context.scope().warn("Tag <list.slice> needs whole bounds: " + tag);
            return Optional.empty();
        }
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
            case "list.set", "list.slice" -> 3;
            case "list.clear", "list.pop", "len", "list.shuffle", "list.reverse", "list.first",
                    "list.last" -> 1;
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
            case "list.filter" -> "<list.filter:list,cond>";
            case "list.reverse" -> "<list.reverse:list>";
            case "list.join" -> "<list.join:list,sep>";
            case "list.slice" -> "<list.slice:list,start,end>";
            case "list.first" -> "<list.first:list>";
            case "list.last" -> "<list.last:list>";
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
