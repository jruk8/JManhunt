package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Run-local functions: {@code <def:name,body,params...>} stores a body
 * verbatim (never evaluated at definition), {@code <name:args...>}
 * substitutes params positionally and evaluates. Bodies run through
 * the normal chain, so calls nest and recurse; every call consumes
 * one shared line step alongside loop iterations, and exhaustion
 * fires the same loop-limit sink. Names are case-insensitive and
 * must not collide with builtin tags; params bind case-sensitively
 * as bare tokens outside quotes. No Bukkit types.
 */
public final class TagFunctions {

    /** Stored function: display name, raw body, and param names in order. */
    public record Definition(String name, String body, List<String> params) {
    }

    /** Identifier shape for function and param names. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * Builtin tag names a def must not shadow. Dispatch prefers
     * builtins anyway (the switch runs before the table lookup), so
     * drift here only costs the def-time warning, never a hijack.
     * Mirrors the resolveTag switch plus the pre-pass ops.
     */
    private static final Set<String> BUILTINS = Set.of(
            "p", "random-mob", "random-item", "random-num", "random-pick",
            "random-player", "all-players", "all-fanout", "duration", "id", "i",
            "pstat", "gstat", "gflag", "pflag", "lflag", "rflag", "placeholder",
            "min", "max", "clamp", "root", "if",
            "gmessage", "pmessage", "rmessage", "gsound", "psound", "rsound",
            "loseplayer", "win", "args",
            "list.append", "list.get", "list.set", "list.remove", "list.contains",
            "list.clear", "list.pop", "len", "list.shuffle", "range",
            "active-players", "plocation", "prole", "distance",
            "floor", "ceil", "round", "abs", "sign", "sqrt", "cbrt",
            "while", "for", "def");

    private TagFunctions() {
    }

    /** Outermost balanced def spans; nested defs wait for the enclosing body. */
    static List<TagLoops.LoopSpan> findDefSpans(String line) {
        return TagLoops.findOpSpans(line, "def"::equals);
    }

    /**
     * Stores one def verbatim and resolves to "": tag-only def lines
     * skip quietly downstream like other setter tags. Bad shapes warn
     * and store nothing.
     */
    static String define(String tag, String args, TagContext context) {
        List<String> parts = TagLists.splitTopLevel(args);
        if (parts.size() < 2 || parts.get(0).isBlank()) {
            context.scope().warn("Tag <def> needs a name and a body like <def:double,x+x,x>: " + tag);
            return "";
        }
        String name = parts.get(0).strip();
        if (!NAME.matcher(name).matches()) {
            context.scope().warn("Tag <def> names use letters, digits, and _ only: " + tag);
            return "";
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (BUILTINS.contains(key)) {
            context.scope().warn("Tag <def> cannot redefine the builtin <" + key + ">: " + tag);
            return "";
        }
        List<String> params = new ArrayList<>();
        for (int index = 2; index < parts.size(); index++) {
            String param = parts.get(index).strip();
            if (!NAME.matcher(param).matches()) {
                context.scope().warn("Tag <def> skips the bad param name '" + param + "': " + tag);
                continue;
            }
            params.add(param);
        }
        context.functions().put(key, new Definition(name, parts.get(1), List.copyOf(params)));
        return "";
    }

    /**
     * Runs one call: positional substitution, then full evaluation so
     * bodies nest and recurse. Unknown names survive untouched like
     * any unknown tag. Each call consumes one shared line step.
     */
    static String call(String tag, String name, String args, TagContext context,
            TagLoops.Evaluator eval) {
        Definition def = context.functions().get(name);
        if (def == null) {
            return tag;
        }
        if (!context.tryConsumeStep()) {
            context.loopLimitExceeded("<" + def.name() + "> exceeded " + TagLoops.LOOP_LIMIT
                    + " steps at " + context.provenance().describe() + ": " + tag);
            return "null";
        }
        List<String> values = args.isEmpty() ? List.of() : TagLists.splitTopLevel(args);
        if (values.size() > def.params().size()) {
            context.scope().warn("Too many args for <" + def.name() + ">, extras ignored: " + tag);
        }
        List<String> bound = values.stream().map(value -> value.strip()).toList();
        return eval.evaluate(substitute(def.body(), def.params(), bound));
    }

    /**
     * Substitutes params as bare case-sensitive tokens outside quotes,
     * in one pass so values never re-substitute. Missing values bind
     * "null": every param is optional.
     */
    static String substitute(String body, List<String> params, List<String> values) {
        if (params.isEmpty()) {
            return body;
        }
        String alternation = params.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        Pattern tokens = Pattern.compile("\\b(?:" + alternation + ")\\b");
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < body.length()) {
            char letter = body.charAt(index);
            if (letter == '"' || letter == '\'') {
                int close = body.indexOf(letter, index + 1);
                int end = close < 0 ? body.length() : close + 1;
                out.append(body, index, end);
                index = end;
            } else {
                int next = index;
                while (next < body.length() && body.charAt(next) != '"' && body.charAt(next) != '\'') {
                    next++;
                }
                out.append(substituteSegment(body.substring(index, next), tokens, params, values));
                index = next;
            }
        }
        return out.toString();
    }

    private static String substituteSegment(String segment, Pattern tokens,
            List<String> params, List<String> values) {
        Matcher matcher = tokens.matcher(segment);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int slot = params.indexOf(matcher.group());
            String value = slot < values.size() ? values.get(slot) : "null";
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
