package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Span pre-passes for tag evaluation: constructs the plain
 * innermost-tag scan cannot see (loop bodies with per-iteration
 * values, if conditions with bare comparisons, filter conditions
 * binding {@code <i>}, mutating list ops with flag references)
 * resolve here first. No Bukkit types.
 */
final class TagPrePass {

    /** Mutating list ops whose flag references write back through the pre-pass. */
    private static final Pattern MUTATOR_OPEN =
            Pattern.compile("<\\s*list\\.(append|set|remove|clear|pop|shuffle)\\s*:",
                    Pattern.CASE_INSENSITIVE);

    private record MutSpan(int start, int end, String op) {
    }

    private TagPrePass() {
    }

    /**
     * Start/end pairs shielding plain-tag windows from span innards:
     * outermost defs, loops, and filters, ready ifs (no nested if,
     * so plain tags inside unready outers still resolve eagerly as
     * before), and flag-ref mutators (pure mutators stay
     * plain-phase). Computed fresh per walk on the current text.
     */
    static List<int[]> protectionSpans(String command) {
        List<int[]> spans = new ArrayList<>();
        for (TagLoops.LoopSpan span : TagFunctions.findDefSpans(command)) {
            spans.add(new int[] {span.start(), span.end()});
        }
        for (TagLoops.LoopSpan span : TagLoops.findLoopSpans(command)) {
            spans.add(new int[] {span.start(), span.end()});
        }
        for (TagLoops.LoopSpan span : TagLists.findFilterSpans(command)) {
            spans.add(new int[] {span.start(), span.end()});
        }
        for (TagControlFlow.IfSpan span : TagControlFlow.findIfSpans(command)) {
            if (!TagControlFlow.hasNestedIf(span.args())) {
                spans.add(new int[] {span.start(), span.end()});
            }
        }
        for (MutSpan span : readyMutatorSpans(command)) {
            spans.add(new int[] {span.start(), span.end()});
        }
        return spans;
    }

    /** True when the match sits inside any protection span. */
    static boolean covers(List<int[]> spans, int start, int end) {
        for (int[] span : spans) {
            if (start >= span[0] && end <= span[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stitches ascending disjoint spans with gap-resolved text before
     * each span; the gap function takes gap text plus its base offset.
     * Strict-prefix gaps only: trailing text passes through for later
     * groups and the sweep. Overlapping spans fail fast and loud.
     */
    private static <S> String stitch(String command, List<S> spans, ToIntFunction<S> startOf,
            ToIntFunction<S> endOf, Function<S, String> resolve,
            BiFunction<String, Integer, String> gap) {
        spans.sort((first, second) ->
                Integer.compare(startOf.applyAsInt(first), startOf.applyAsInt(second)));
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (S span : spans) {
            int start = startOf.applyAsInt(span);
            out.append(gap.apply(command.substring(cursor, start), cursor));
            out.append(resolve.apply(span));
            cursor = endOf.applyAsInt(span);
        }
        out.append(command.substring(cursor));
        return out.toString();
    }

    /**
     * Resolves outermost def spans, left to right, with identity
     * gaps. Bodies store verbatim: nested tags inside wait for a
     * call to evaluate them, so this must run before loops, ifs,
     * and list mutators. Duplicate names are rightmost-wins.
     */
    static String resolveDefSpans(String command, TagContext context) {
        List<TagLoops.LoopSpan> spans = TagFunctions.findDefSpans(command);
        if (spans.isEmpty()) {
            return command;
        }
        return stitch(command, spans, TagLoops.LoopSpan::start, TagLoops.LoopSpan::end,
                span -> TagFunctions.define(command.substring(span.start(), span.end()),
                        span.args(), context),
                (gap, base) -> gap);
    }

    /**
     * Resolves outermost loop spans, left to right, resolving the
     * strict-prefix plain window before each span through the gap
     * function. Nested loops wait for the body evaluation of their
     * enclosing loop, which recurses through the evaluation chain
     * per iteration.
     */
    static String resolveLoopSpans(String command, TagContext context, TagLoops.Evaluator eval,
            BiFunction<String, Integer, String> gap) {
        List<TagLoops.LoopSpan> spans = TagLoops.findLoopSpans(command);
        if (spans.isEmpty()) {
            return command;
        }
        return stitch(command, spans, TagLoops.LoopSpan::start, TagLoops.LoopSpan::end,
                span -> TagLoops.resolve(command.substring(span.start(), span.end()),
                        span.op(), span.args(), context, eval),
                gap);
    }

    /**
     * Resolves if-spans without nested ifs, left to right, resolving
     * the strict-prefix plain window before each span through the gap
     * function, with lazy branches: only the condition and the chosen
     * branch evaluate. Spans nesting another if still wait for the
     * inner span, so an if nested in a dead branch resolves eagerly
     * as before.
     */
    static String resolveIfSpans(String command, TagContext context, TagLoops.Evaluator eval,
            BiFunction<String, Integer, String> gap) {
        List<TagControlFlow.IfSpan> ready = new ArrayList<>();
        for (TagControlFlow.IfSpan span : TagControlFlow.findIfSpans(command)) {
            if (!TagControlFlow.hasNestedIf(span.args())) {
                ready.add(span);
            }
        }
        if (ready.isEmpty()) {
            return command;
        }
        return stitch(command, ready, TagControlFlow.IfSpan::start, TagControlFlow.IfSpan::end,
                span -> ifLazy(command.substring(span.start(), span.end()), span.args(),
                        context, eval),
                gap);
    }

    /**
     * Lazy {@code <if>} for spans with nested tags: splits the raw args
     * first, evaluates the condition, then evaluates only the chosen
     * branch. The unselected branch never runs, which is what lets
     * recursive calls terminate on a base case; eager evaluation
     * would recurse through the dead branch first. Innermost ifs
     * (no nested tags) keep the eager path, where eager and lazy
     * coincide.
     */
    private static String ifLazy(String tag, String args, TagContext context, TagLoops.Evaluator eval) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() < 2 || parts.size() > 3) {
            context.scope().warn("Tag <if> needs a condition plus one or two branches: " + tag);
            return "";
        }
        Optional<String> condition = CommandPlaceholders.parsePickItem(eval.evaluate(parts.get(0)));
        if (condition.isEmpty() || condition.get().isBlank()) {
            context.scope().warn("Tag <if> has malformed branches: " + tag);
            return "";
        }
        boolean result;
        try {
            result = TagExpressions.evalCondition(condition.get(), context.scope()::warn, tag);
        } catch (TagExpressions.ExprException failed) {
            context.scope().warn("Tag <if> " + failed.getMessage() + ": " + tag);
            return "";
        }
        if (!result && parts.size() < 3) {
            return "";
        }
        String branch = result ? parts.get(1) : parts.get(2);
        String evaluated = eval.evaluate(branch);
        if (evaluated.isBlank()) {
            return "";
        }
        Optional<String> picked = CommandPlaceholders.parsePickItem(evaluated);
        if (picked.isEmpty()) {
            context.scope().warn("Tag <if> has malformed branches: " + tag);
            return "";
        }
        return picked.get();
    }

    /**
     * Resolves outermost filter spans, left to right, resolving the
     * strict-prefix plain window before each span through the gap
     * function. Nested filters wait for the condition evaluation of
     * their enclosing filter, which recurses through the evaluation
     * chain per item.
     */
    static String resolveFilterSpans(String command, TagContext context,
            TagLoops.Evaluator eval, BiFunction<String, Integer, String> gap) {
        List<TagLoops.LoopSpan> spans = TagLists.findFilterSpans(command);
        if (spans.isEmpty()) {
            return command;
        }
        return stitch(command, spans, TagLoops.LoopSpan::start, TagLoops.LoopSpan::end,
                span -> TagLists.filterSpan(command.substring(span.start(), span.end()),
                        span.args(), context, eval),
                gap);
    }

    /**
     * Resolves mutating list spans whose list arg is a verbatim flag
     * reference, writing the result back to that flag. Spans holding
     * other expressions are left for generic inside-out evaluation,
     * which applies them purely. Right to left; nested mutators wait
     * for the inner span to resolve first.
     */
    static String resolveListSpans(String command, TagContext context, TagLoops.Evaluator eval) {
        List<MutSpan> ready = readyMutatorSpans(command);
        if (ready.isEmpty()) {
            return command;
        }
        StringBuilder result = new StringBuilder(command);
        for (MutSpan span : ready) {
            String body = command.substring(span.start() + 1, span.end() - 1);
            List<String> segments = TagLists.splitTopLevel(body.substring(body.indexOf(':') + 1));
            Optional<TagLists.FlagRef> ref = TagLists.flagRef(segments.get(0));
            if (ref.isEmpty()) {
                continue;
            }
            List<String> rest = new ArrayList<>();
            for (int index = 1; index < segments.size(); index++) {
                rest.add(eval.evaluate(segments.get(index)));
            }
            String tag = command.substring(span.start(), span.end());
            String resolved = TagLists.writeBack(tag, span.op(), ref.get().kind(), ref.get().name(),
                    rest, context, eval);
            result.replace(span.start(), span.end(), resolved);
        }
        return result.toString();
    }

    /**
     * Mutator spans holding no nested mutator span whose list arg is
     * a verbatim flag reference, rightmost first. Pure mutators stay
     * out so the plain scan still applies them.
     */
    private static List<MutSpan> readyMutatorSpans(String command) {
        List<MutSpan> all = new ArrayList<>();
        Matcher opener = MUTATOR_OPEN.matcher(command);
        while (opener.find()) {
            int close = CommandPlaceholders.spanClose(command, opener.start());
            if (close >= 0) {
                all.add(new MutSpan(opener.start(), close + 1,
                        "list." + opener.group(1).toLowerCase(Locale.ROOT)));
            }
        }
        List<MutSpan> ready = new ArrayList<>();
        for (MutSpan span : all) {
            boolean nested = false;
            for (MutSpan other : all) {
                if (other != span && other.start() > span.start() && other.end() < span.end()) {
                    nested = true;
                    break;
                }
            }
            if (!nested && hasFlagRef(command, span)) {
                ready.add(span);
            }
        }
        ready.sort((first, second) -> Integer.compare(second.start(), first.start()));
        return ready;
    }

    /** True when the mutator span reads a verbatim flag reference. */
    private static boolean hasFlagRef(String command, MutSpan span) {
        String body = command.substring(span.start() + 1, span.end() - 1);
        String args = body.substring(body.indexOf(':') + 1);
        return TagLists.flagRef(TagLists.splitTopLevel(args).get(0)).isPresent();
    }
}
