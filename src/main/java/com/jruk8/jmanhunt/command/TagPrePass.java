package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Span pre-passes for tag evaluation: constructs the plain
 * innermost-tag scan cannot see (loop bodies with per-iteration
 * values, if conditions with bare comparisons, mutating list ops
 * with flag references) resolve here first. No Bukkit types.
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
     * Resolves outermost def spans, right to left. Bodies store
     * verbatim: nested tags inside wait for a call to evaluate them,
     * so this must run before loops, ifs, and list mutators.
     */
    static String resolveDefSpans(String command, TagContext context) {
        List<TagLoops.LoopSpan> spans = TagFunctions.findDefSpans(command);
        if (spans.isEmpty()) {
            return command;
        }
        spans.sort((first, second) -> Integer.compare(second.start(), first.start()));
        StringBuilder result = new StringBuilder(command);
        for (TagLoops.LoopSpan span : spans) {
            String tag = command.substring(span.start(), span.end());
            String resolved = TagFunctions.define(tag, span.args(), context);
            result.replace(span.start(), span.end(), resolved);
        }
        return result.toString();
    }

    /**
     * Resolves outermost loop spans, right to left. Nested loops wait
     * for the body evaluation of their enclosing loop, which recurses
     * through the evaluation chain per iteration.
     */
    static String resolveLoopSpans(String command, TagContext context, TagLoops.Evaluator eval) {
        List<TagLoops.LoopSpan> spans = TagLoops.findLoopSpans(command);
        if (spans.isEmpty()) {
            return command;
        }
        spans.sort((first, second) -> Integer.compare(second.start(), first.start()));
        StringBuilder result = new StringBuilder(command);
        for (TagLoops.LoopSpan span : spans) {
            String tag = command.substring(span.start(), span.end());
            String resolved = TagLoops.resolve(tag, span.op(), span.args(), context, eval);
            result.replace(span.start(), span.end(), resolved);
        }
        return result.toString();
    }

    /**
     * Resolves if-spans without nested ifs, right to left, with lazy
     * branches: only the condition and the chosen branch evaluate.
     * Spans nesting another if still wait for the inner span, so an
     * if nested in a dead branch resolves eagerly as before.
     */
    static String resolveIfSpans(String command, TagContext context, TagLoops.Evaluator eval) {
        List<TagExpressions.IfSpan> ready = new ArrayList<>();
        for (TagExpressions.IfSpan span : TagExpressions.findIfSpans(command)) {
            if (!TagExpressions.hasNestedIf(span.args())) {
                ready.add(span);
            }
        }
        if (ready.isEmpty()) {
            return command;
        }
        ready.sort((first, second) -> Integer.compare(second.start(), first.start()));
        StringBuilder result = new StringBuilder(command);
        for (TagExpressions.IfSpan span : ready) {
            String resolved = ifLazy(command.substring(span.start(), span.end()),
                    span.args(), context, eval);
            result.replace(span.start(), span.end(), resolved);
        }
        return result.toString();
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
        Optional<String> picked = CommandPlaceholders.parsePickItem(eval.evaluate(branch));
        if (picked.isEmpty()) {
            context.scope().warn("Tag <if> has malformed branches: " + tag);
            return "";
        }
        return picked.get();
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
                    rest, context);
            result.replace(span.start(), span.end(), resolved);
        }
        return result.toString();
    }

    /** Mutator spans holding no nested mutator span, rightmost first. */
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
            if (!nested) {
                ready.add(span);
            }
        }
        ready.sort((first, second) -> Integer.compare(second.start(), first.start()));
        return ready;
    }
}
