package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    private CommandSyntax() {
    }

    /**
     * Modifier engine tags in cheatsheet order. The single tag table:
     * validation and the dialog cheatsheet both read from here, so a
     * new tag can never validate but stay undocumented. Duration is
     * compass-only (substituted by withDuration on that path alone),
     * so it stays out and warns as unknown on modifiers.
     */
    public static List<String> knownTags() {
        return List.of("p", "random-mob", "random-item", "random-num",
                "random-pick", "random-player", "all-players", "id", "min",
                "max", "clamp", "if", "gmessage", "gmsg", "pmessage", "pmsg", "gsound", "psound",
                "pstat", "gstat", "phasitem", "gflag", "gf", "pflag", "pf", "lflag", "lf", "placeholder",
                "rflag", "rmessage", "rmsg", "rsound",
                "loseplayer", "win", "pswitch", "args", "list.append", "list.get", "list.set", "list.remove",
                "list.contains", "list.clear", "list.pop", "len", "list.shuffle", "range",
                "list.filter", "list.reverse", "list.join", "list.slice", "list.first", "list.last",
                "active-players", "plocation", "prole", "distance",
                "overlap-players", "nearby-players", "pworld", "world",
                "px", "py", "pz", "pyaw", "ppitch",
                "floor", "ceil", "round", "abs", "sign", "sqrt", "cbrt", "root",
                "while", "for", "i", "def", "run", "format",
                "str.join", "str.split", "str.lower", "str.upper", "str.contains",
                "pcooldown", "pcooldown.get", "pcooldown.reset",
                "gcooldown", "gcooldown.get", "gcooldown.reset", "default", "pheld",
                "vec.add", "vec.sub", "vec.mult", "vec.normalize", "vec.sqrdist", "vec.dist",
                "vec.dot", "vec.cross", "loc.shift", "pdir", "ploc",
                "pstate", "pstandingon", "ptitle", "pslot");
    }

    /**
     * First fatal problem with a typed command: blank input, a stray
     * exit, unbalanced angle brackets, or malformed tag args. Empty
     * means the command is safe to save.
     */
    public static Optional<String> error(String command) {
        Optional<String> problem = errorInner(EngineEscapes.substitute(command));
        return problem.map(EngineEscapes::restore);
    }

    private static Optional<String> errorInner(String command) {
        if (command == null || command.isBlank()) {
            return Optional.of("Command must not be empty.");
        }
        if (TagControlFlow.isExitMisuse(command)) {
            return Optional.of("'exit' must stand alone on its line.");
        }
        int depth = 0;
        String balanced = withoutIfSpans(command);
        for (int index = 0; index < balanced.length(); index++) {
            char letter = balanced.charAt(index);
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
            Optional<String> tagError = TagSyntaxErrors.tagError(body);
            if (tagError.isPresent()) {
                return tagError;
            }
        }
        return Optional.empty();
    }

    /**
     * Tag bodies inside-out, innermost spans first, so a nested tag no
     * longer hides its outer tag from validation. If-spans collapse
     * first (mirroring dispatch) since their conditions may hold bare
     * {@code < > <= >=}. Pass cap mirrors dispatch; anything deeper
     * stays for runtime to report.
     */
    static List<String> tagBodies(String command) {
        List<String> bodies = new ArrayList<>();
        String current = command;
        for (int pass = 0; pass < 25; pass++) {
            String collapsed = collapseIfSpans(current, bodies);
            if (!collapsed.equals(current)) {
                current = collapsed;
                continue;
            }
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
     * If-spans blanked for the bracket check: their quoted conditions
     * may hold {@code < > <= >=} that are content, not structure.
     * Loops to a fixpoint so nested ifs collapse inside out. The
     * check itself stays quote-blind to mirror dispatch.
     */
    private static String withoutIfSpans(String command) {
        String current = command;
        for (int pass = 0; pass < 25; pass++) {
            String collapsed = collapseIfSpans(current, new ArrayList<>());
            if (collapsed.equals(current)) {
                return current;
            }
            current = collapsed;
        }
        return current;
    }

    /** Collects nested bodies plus one collapsed body per ready if-span. */
    private static String collapseIfSpans(String current, List<String> bodies) {
        List<TagControlFlow.IfSpan> ready = new ArrayList<>();
        for (TagControlFlow.IfSpan span : TagControlFlow.findIfSpans(current)) {
            if (!TagControlFlow.hasNestedIf(span.args())) {
                ready.add(span);
            }
        }
        if (ready.isEmpty()) {
            return current;
        }
        ready.sort((first, second) -> Integer.compare(second.start(), first.start()));
        StringBuilder result = new StringBuilder(current);
        for (TagControlFlow.IfSpan span : ready) {
            bodies.addAll(tagBodies(span.args()));
            result.replace(span.start(), span.end(), "?");
            bodies.add("if:" + collapseTags(span.args()));
        }
        return result.toString();
    }

    /**
     * Blanks unquoted innermost tags with {@code ?}, leaving quoted
     * comparisons (and quoted nested tags) intact for the if checks.
     */
    private static String collapseTags(String text) {
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int open = nextUnquoted(text, index, '<');
            if (open < 0) {
                out.append(text.substring(index));
                return out.toString();
            }
            int close = nextUnquoted(text, open + 1, '>');
            int nested = nextUnquoted(text, open + 1, '<');
            if (close < 0 || (nested >= 0 && nested < close)) {
                out.append(text, index, open + 1);
                index = open + 1;
            } else {
                out.append(text, index, open).append('?');
                index = close + 1;
            }
        }
        return out.toString();
    }

    private static int nextUnquoted(String text, int from, char target) {
        char quote = 0;
        for (int index = from; index < text.length(); index++) {
            char letter = text.charAt(index);
            if (quote != 0) {
                if (letter == quote) {
                    quote = 0;
                }
            } else if (letter == '"' || letter == '\'') {
                quote = letter;
            } else if (letter == target) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Non-fatal flags: unknown tags (dispatch leaves them untouched),
     * invalid random-pick items (dispatch skips them), and non-letter
     * all-players filters (dispatch falls back to the executor).
     */
    public static List<String> warnings(String command) {
        return warnings(command, Set.of());
    }

    /** Warnings for one line; knownFunctions holds lowercase def names. */
    public static List<String> warnings(String command, Collection<String> knownFunctions) {
        List<String> found = new ArrayList<>();
        if (command == null || command.isBlank() || error(command).isPresent()) {
            return found;
        }
        for (String body : tagBodies(EngineEscapes.substitute(command))) {
            TagSyntaxErrors.collectWarnings(body, found, knownFunctions);
        }
        return found.stream().map(EngineEscapes::restore).toList();
    }

    /** First-token root of one command line. */
    public static String commandRoot(String command) {
        return SyntaxSuggest.commandRoot(command);
    }

    /** True when the line's root names a blocked command. */
    public static boolean isBlockedCommand(String command, Collection<String> blocked) {
        return SyntaxSuggest.isBlockedCommand(command, blocked);
    }
}
