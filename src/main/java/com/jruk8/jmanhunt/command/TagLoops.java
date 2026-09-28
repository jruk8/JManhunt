package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * While and for loops: {@code <while:condition,body>} re-checks its
 * condition each iteration, {@code <for:list,body>} walks a snapshot
 * of its list with the item behind {@code <i>}. Bodies run for side
 * effects and their text is discarded; success resolves to
 * {@code ""}. A for loop whose source flag moves mid-loop cancels to
 * {@code "null"}. Every iteration consumes one shared line step from
 * the context budget ({@link #LOOP_LIMIT} per line, split with
 * function calls); exhaustion fires the context loop-limit sink (the
 * manager logs, tells the match, and cancels it) and resolves to
 * {@code "null"}. Nested loops recurse through body evaluation like
 * {@code <if>} spans, with no separate depth counter; each nesting
 * level gets a fresh tag-pass budget, but steps never refund. No
 * Bukkit types.
 */
public final class TagLoops {

    /** Hard per-line step cap shared by loops and function calls; past it the match cancels. */
    public static final int LOOP_LIMIT = 1000;

    /** One balanced loop span: offsets, op, and raw args. */
    public record LoopSpan(int start, int end, String op, String args) {
    }

    /** Full tag resolution for one fragment in the calling context. */
    public interface Evaluator {
        String evaluate(String fragment);
    }

    private TagLoops() {
    }

    /**
     * Outermost balanced while/for spans: nested loops wait for the
     * body evaluation of their enclosing loop, so each iteration
     * runs them fresh. Unbalanced spans are skipped.
     */
    public static List<LoopSpan> findLoopSpans(String line) {
        return findOpSpans(line, name -> name.equals("while") || name.equals("for"));
    }

    /**
     * Outermost balanced spans for the matching ops. Shared with def
     * spans, which need the same outermost-first extraction.
     */
    static List<LoopSpan> findOpSpans(String line, java.util.function.Predicate<String> matches) {
        List<LoopSpan> spans = new ArrayList<>();
        int index = 0;
        while (index < line.length()) {
            int open = line.indexOf('<', index);
            if (open < 0) {
                break;
            }
            LoopSpan span = loopSpanAt(line, open, matches);
            if (span != null) {
                spans.add(span);
            }
            index = open + 1;
        }
        List<LoopSpan> outer = new ArrayList<>();
        for (LoopSpan span : spans) {
            boolean nested = false;
            for (LoopSpan other : spans) {
                if (other != span && other.start() < span.start() && other.end() > span.end()) {
                    nested = true;
                    break;
                }
            }
            if (!nested) {
                outer.add(span);
            }
        }
        return outer;
    }

    private static LoopSpan loopSpanAt(String line, int open,
            java.util.function.Predicate<String> matches) {
        int cursor = open + 1;
        while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
            cursor++;
        }
        int rootEnd = cursor;
        while (rootEnd < line.length() && TagExpressions.isRootChar(line.charAt(rootEnd))) {
            rootEnd++;
        }
        String name = line.substring(cursor, rootEnd).toLowerCase(Locale.ROOT);
        if (!matches.test(name)) {
            return null;
        }
        cursor = rootEnd;
        while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= line.length() || (line.charAt(cursor) != ':'
                && line.charAt(cursor) != '>')) {
            return null;
        }
        int argsStart = cursor + 1;
        Integer end = TagExpressions.spanEnd(line, open);
        if (end == null) {
            return null;
        }
        return new LoopSpan(open, end + 1, name, line.substring(argsStart, end));
    }

    /**
     * Runs one loop span: the list arg resolves once up front (plus
     * a source-flag snapshot when it is a flag reference), while the
     * condition re-resolves each iteration. Any bad shape warns and
     * yields {@code "null"}.
     */
    public static String resolve(String tag, String op, String args,
            TagContext context, Evaluator eval) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() != 2) {
            context.scope().warn("Tag <" + op + "> needs a condition or list plus a body: " + tag);
            return "null";
        }
        return op.equals("while")
                ? runWhile(tag, parts.get(0), parts.get(1), context, eval)
                : runFor(tag, parts.get(0), parts.get(1), context, eval);
    }

    private static String runWhile(String tag, String condition, String body,
            TagContext context, Evaluator eval) {
        for (;;) {
            if (!context.tryConsumeStep()) {
                limitExceeded(tag, "while", context);
                return "null";
            }
            Optional<Boolean> keepGoing = whileCondition(tag, condition, context, eval);
            if (keepGoing.isEmpty()) {
                return "null";
            }
            if (!keepGoing.get()) {
                return "";
            }
            eval.evaluate(body);
        }
    }

    /**
     * One while check: a bare true/false word (case-insensitive)
     * short-circuits, else the full condition grammar runs. Anything
     * else warns and aborts the loop.
     */
    private static Optional<Boolean> whileCondition(String tag, String condition,
            TagContext context, Evaluator eval) {
        String resolved = eval.evaluate(condition).strip();
        if (resolved.equalsIgnoreCase("true")) {
            return Optional.of(true);
        }
        if (resolved.equalsIgnoreCase("false")) {
            return Optional.of(false);
        }
        try {
            return Optional.of(TagExpressions.evalCondition(resolved, context.scope()::warn, tag));
        } catch (TagExpressions.ExprException failed) {
            context.scope().warn("Tag <while> " + failed.getMessage() + ": " + tag);
            return Optional.empty();
        }
    }

    private static String runFor(String tag, String listArg, String body,
            TagContext context, Evaluator eval) {
        Optional<TagLists.FlagRef> ref = TagLists.flagRef(listArg);
        String snapshot = ref.map(flag -> TagFlags.loadFlag(flag.kind(), flag.name(), context))
                .orElse(null);
        String resolved = eval.evaluate(listArg);
        if (!TagLists.isList(resolved)) {
            context.scope().warn("Tag <for> needs a list like [a, b]: " + tag);
            return "null";
        }
        List<String> items = TagLists.parse(resolved);
        for (int step = 0; step < items.size(); step++) {
            if (!context.tryConsumeStep()) {
                limitExceeded(tag, "for", context);
                return "null";
            }
            if (snapshot != null && !snapshot.equals(
                    TagFlags.loadFlag(ref.get().kind(), ref.get().name(), context))) {
                context.scope().warn("Tag <for> list changed during the loop, cancelling: " + tag);
                return "null";
            }
            context.pushLoopItem(items.get(step));
            try {
                eval.evaluate(body);
            } finally {
                context.popLoopItem();
            }
        }
        return "";
    }

    private static void limitExceeded(String tag, String op, TagContext context) {
        context.loopLimitExceeded("<" + op + "> exceeded " + LOOP_LIMIT + " steps at "
                + context.provenance().describe() + ": " + tag);
    }
}
